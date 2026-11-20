package com.jaguar.attendancetracker.backend.reminders

import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Only accessed under the app's reminder coordinator lock, never exported or synced. */
internal class ReminderConsentFiles(private val directory: File) {
    private data class Entry(val revision: UUID, val consent: ReminderConsent?)
    private fun file(id: UUID) = AtomicFile(File(directory, "$id.bin"))

    fun current(id: UUID): ReminderConsent? = read(id)?.consent

    fun enable(consent: ReminderConsent) {
        val ticket = consent.ticket
        write(ticket.examId, Entry(ticket.revision, consent))
    }

    fun revoke(id: UUID) {
        val entry = read(id) ?: return
        if (entry.consent != null) write(id, Entry(entry.revision, null))
    }

    /** Atomically consume before notification, prioritizing at-most-once over crash retries. */
    fun claim(ticket: ReminderTicket): Boolean {
        val entry = read(ticket.examId) ?: return false
        if (entry.revision != ticket.revision || entry.consent == null) return false
        write(ticket.examId, Entry(ticket.revision, null))
        return true
    }

    private fun read(id: UUID): Entry? {
        try {
            DataInputStream(file(id).openRead()).use { stream ->
                require(stream.available() <= 1024)
                require(stream.readInt() == 1)
                val revision = UUID(stream.readLong(), stream.readLong())
                val consent = when (stream.readUnsignedByte()) {
                    0 -> null
                    1 -> {
                        val date = LocalDate.ofEpochDay(stream.readLong())
                        val local = LocalDateTime.parse(stream.readUTF())
                        val zone = ZoneId.of(stream.readUTF())
                        val offset = ZoneOffset.ofTotalSeconds(stream.readInt())
                        val scheduled = Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
                        val expiry = Instant.ofEpochSecond(stream.readLong(), stream.readInt().toLong())
                        ReminderConsent(ReminderTicket(id, revision), date, ReminderTime(local, zone, offset, scheduled, expiry))
                    }
                    else -> throw IOException("Invalid reminder file")
                }
                require(stream.read() == -1)
                return Entry(revision, consent)
            }
        } catch (e: FileNotFoundException) {
            // AtomicFile may also report an inaccessible existing file as FileNotFoundException.
            // Do not treat an existing unreadable file as safely revoked.
            val base = file(id).baseFile
            if (base.exists() || File(directory, "$id.bin.bak").exists()) throw e
            return null
        }
    }

    private fun write(id: UUID, entry: Entry) {
        val atomic = file(id)
        val output = atomic.startWrite()
        try {
            val stream = DataOutputStream(output)
            stream.writeInt(1)
            stream.writeLong(entry.revision.mostSignificantBits)
            stream.writeLong(entry.revision.leastSignificantBits)
            val consent = entry.consent
            stream.writeByte(if (consent == null) 0 else 1)
            if (consent != null) {
                val time = consent.time
                stream.writeLong(consent.examDate.toEpochDay())
                stream.writeUTF(time.local.toString())
                stream.writeUTF(time.zone.id)
                stream.writeInt(time.offset.totalSeconds)
                stream.writeLong(time.scheduled.epochSecond)
                stream.writeInt(time.scheduled.nano)
                stream.writeLong(time.expiresAt.epochSecond)
                stream.writeInt(time.expiresAt.nano)
            }
            stream.flush()
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
        // The platform logs some rename errors internally rather than throwing.
        if (read(id) != entry) throw IOException("Reminder consent was not saved")
    }
}

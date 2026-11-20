package com.jaguar.attendancetracker.ui.imExport

import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PreparedPlannerRestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction

/** Local file transport. No persisted permissions, temporary files or network operations here. */
internal class PlannerBackupFiles(private val restore: PlannerRestoreRepository) {
    suspend fun read(open: () -> InputStream?): PreparedPlannerRestore = withContext(Dispatchers.IO) {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = open() ?: throw IOException("Planner backup input unavailable")
        // The strict codec bounds the decoded stream; UTF-8 decoding cannot inflate the
        // raw input beyond four bytes per decoded character (plus a small read buffer).
        InputStreamReader(input, decoder).use { restore.prepare(it) }
    }

    suspend fun write(open: () -> OutputStream?) {
        val backup = restore.createBackup()
        withContext(Dispatchers.IO) {
            val encoder = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val output = open() ?: throw IOException("Planner backup output unavailable")
            // A flush or close failure is a failed save, never a successful backup.
            OutputStreamWriter(output, encoder).use { it.write(backup) }
        }
    }
}

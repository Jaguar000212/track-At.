package com.jaguar.attendancetracker.ui.imExport

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannerBackupFilesTest {
    @Test
    fun completeUtf8FileRoundTripWaitsForExplicitApplyAndClosesOutput() = withDbs { source, destination ->
        val date = LocalDate.of(2024, 2, 29)
        val term = Semester(name = "Archived semester", startDate = date, endDate = null, isArchived = true)
        val task = BoardItem(title = "Résumé λ 😀", dueDate = date)
        source.plannerRestoreDao().insertSemesters(listOf(term))
        source.plannerRestoreDao().insertBoard(listOf(task))
        var closed = false
        val sink = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                closed = true
            }
        }
        PlannerBackupFiles(PlannerRestoreRepository(source)).write { sink }
        assertTrue(closed)
        val files = PlannerBackupFiles(PlannerRestoreRepository(destination))
        val preview = files.read { sink.toByteArray().inputStream() }
        assertTrue(destination.plannerRestoreDao().board().isEmpty())
        assertEquals(PlannerMergeOutcome.INSERT, preview.preview.board.single().outcome)
        assertEquals(PlannerRestoreResult.APPLIED, preview.apply())
        assertEquals(listOf(term), destination.plannerRestoreDao().semesters())
        assertEquals(listOf(task), destination.plannerRestoreDao().board())
        assertEquals(PlannerRestoreResult.NOTHING_TO_APPLY, files.read { sink.toByteArray().inputStream() }.apply())
    }

    @Test
    fun badUtf8NullStreamsAndCloseFailuresCannotReportSuccess() = withDbs { db, _ ->
        val files = PlannerBackupFiles(PlannerRestoreRepository(db))
        try {
            files.read { byteArrayOf(0xC3.toByte(), 0x28).inputStream() }
            fail("Malformed UTF-8 must be rejected")
        } catch (_: CharacterCodingException) {
            assertTrue(db.plannerRestoreDao().board().isEmpty())
        }

        expectIoFailure { files.read { null } }
        expectIoFailure { files.write { null } }
        expectIoFailure {
            files.write {
                object : ByteArrayOutputStream() {
                    override fun close() { throw IOException("fixture close failure") }
                }
            }
        }
        val sink = ByteArrayOutputStream()
        files.write { sink }
        expectIoFailure {
            files.read {
                object : java.io.ByteArrayInputStream(sink.toByteArray()) {
                    override fun close() { throw IOException("fixture read close failure") }
                }
            }
        }
        assertTrue(db.plannerRestoreDao().board().isEmpty())
    }

    private suspend fun expectIoFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected I/O failure")
        } catch (_: IOException) {
            // Expected, including failed close after data was written/read.
        }
    }

    private fun withDbs(block: suspend (AttendanceDatabase, AttendanceDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking { block(source, destination) }
        } finally {
            destination.close()
            source.close()
        }
    }
}

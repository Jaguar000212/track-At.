package com.jaguar.attendancetracker.ui.board

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BoardPersistenceTest {
    @Test
    fun boardEditsKeepCompletionAndPlannerBackupRestoresWithoutTimetable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking {
                val board = BoardViewModel(source)
                val originalDate = LocalDate.of(2024, 2, 29)
                assertFalse(board.save(null, " ", null))
                assertTrue(board.save(null, "Submit assignment", null))
                val item = source.boardItemDao().observeAll().first().single()
                assertNull(item.dueDate)
                assertTrue(board.setDone(item.id, true))
                assertTrue(board.save(item.id, "Submit history assignment", originalDate))
                val done = source.boardItemDao().observeAll().first().single()
                assertEquals(item.copy(title = "Submit history assignment", dueDate = originalDate, isDone = true), done)
                assertTrue(source.plannerRestoreDao().subjects().isEmpty())
                assertTrue(source.plannerRestoreDao().schedule().isEmpty())

                val sink = ByteArrayOutputStream()
                PlannerBackupFiles(PlannerRestoreRepository(source)).write { sink }
                val restore = PlannerBackupFiles(PlannerRestoreRepository(restored))
                assertEquals(PlannerRestoreResult.APPLIED, restore.read { sink.toByteArray().inputStream() }.apply())
                assertEquals(done, restored.boardItemDao().observeAll().first().single())
                val destinationBoard = BoardViewModel(restored)
                assertTrue(destinationBoard.setDone(done.id, false))
                assertTrue(destinationBoard.save(done.id, done.title, null))
                assertEquals(done.copy(dueDate = null, isDone = false),
                    restored.boardItemDao().observeAll().first().single())
            }
        } finally {
            restored.close()
            source.close()
        }
    }
}

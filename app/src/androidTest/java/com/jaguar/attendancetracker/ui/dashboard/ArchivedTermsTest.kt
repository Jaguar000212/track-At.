package com.jaguar.attendancetracker.ui.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import com.jaguar.attendancetracker.ui.dayView.ensureOperationalAttendanceForDate
import com.jaguar.attendancetracker.ui.today.todaySnapshot
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArchivedTermsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun archivedScopeIsReadOnlyAndBackRestoresActiveSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            val dao = db.plannerRestoreDao()
            val semesterDao = db.semesterDao()
            val start = LocalDate.of(2025, 8, 1)
            val archived = Semester(name = "Archived term fixture", startDate = start,
                endDate = start.plusMonths(4), isArchived = true)
            val active = Semester(name = "Active term fixture", startDate = start.plusYears(1), endDate = null)
            val subject = Subject(name = "Historical subject fixture", semesterId = archived.id,
                startDate = start, isEnded = true, color = "PINK", professor = null, roomNo = null, notes = null,
                totalClasses = 1, attendedClasses = 1)
            val mark = AttendanceRecord(subjectId = subject.id, sessionId = null, date = start,
                status = AttendanceStatus.PRESENT, classType = ClassType.EXTRA)
            val results = runBlocking {
                dao.insertSemesters(listOf(archived, active))
                dao.insertSubjects(listOf(subject))
                dao.insertAttendance(listOf(mark))
                assertEquals(listOf(active), semesterDao.getActiveSemestersWithSubjects().first().map { it.semester })
                semesterDao.getArchivedSemestersWithSubjects().first().also {
                    assertEquals(archived, it.single().semester)
                    assertEquals(listOf(subject), it.single().subjects)
                }
            }
            val selected = "Retained active selection"
            val activeAction = "Active action fixture"
            compose.setContent {
                SemesterBrowser { history ->
                    if (history) ArchivedTerms(DashboardState.Success(results))
                    else {
                        var showSelected by rememberSaveable { mutableStateOf(false) }
                        Column {
                            TextButton(onClick = { showSelected = true }) { Text(activeAction) }
                            if (showSelected) Text(selected)
                        }
                    }
                }
            }
            compose.onNodeWithText(activeAction).performClick()
            compose.onNodeWithText(selected).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.terms_archived)).performClick()
            compose.onNodeWithText(archived.name).assertIsDisplayed()
            compose.onNodeWithText(subject.name).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.terms_archive_saved_totals, 1, 1)).assertIsDisplayed()
            compose.onNodeWithText(activeAction).assertDoesNotExist()
            val activity = compose.activity
            compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText(selected).assertIsDisplayed()
            compose.onNodeWithText(subject.name).assertDoesNotExist()
            runBlocking {
                assertEquals(listOf(mark), dao.attendance())
                assertEquals(results, semesterDao.getArchivedSemestersWithSubjects().first())
            }
        } finally { db.close() }
    }

    @Test fun archivingGuardsMaterializationAndStaleExtraWhileKeepingHistoryAndBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking {
                val semesterDao = db.semesterDao()
                val schedules = ScheduleRepository(db.sessionRecordDao(), db.subjectDao(), semesterDao)
                val attendance = AttendanceRepository(db.attendanceRecordDao(), db.subjectDao())
                val historical = LocalDate.of(2025, 1, 20)
                val nextWeek = historical.plusWeeks(1)
                val alpha = Semester(name = "Alpha", startDate = historical, endDate = null)
                val beta = Semester(name = "Beta", startDate = historical, endDate = null)
                val first = Subject(name = "First", semesterId = alpha.id, startDate = historical,
                    isEnded = false, color = "PINK", professor = null, roomNo = null, notes = null)
                val second = first.copy(id = java.util.UUID.randomUUID(), name = "Second", semesterId = beta.id)
                val aSlot = SessionRecord(subjectId = first.id, startDate = historical, dayOfWeek = historical.dayOfWeek.value)
                val bSlot = aSlot.copy(id = java.util.UUID.randomUUID(), subjectId = second.id)
                val prior = AttendanceRecord(subjectId = second.id, sessionId = bSlot.id, date = historical,
                    status = null, classType = ClassType.REGULAR)
                db.plannerRestoreDao().apply {
                    insertSemesters(listOf(alpha, beta))
                    insertSubjects(listOf(first, second))
                    insertSchedule(listOf(aSlot, bSlot))
                    insertAttendance(listOf(prior))
                }

                val staleChoice = schedules.getExtraClassSubjects().first().single { it.id == second.id }
                val staleRegular = AttendanceRecord(subjectId = second.id, sessionId = bSlot.id, date = nextWeek,
                    status = null, classType = ClassType.REGULAR)
                schedules.setSemesterArchived(beta.id, true)
                schedules.updateSemester(beta.copy(name = "Stale edit"))
                schedules.deleteSemester(beta)
                assertEquals(beta.copy(isArchived = true), semesterDao.getArchivedSemestersWithSubjects().first().single().semester)
                assertEquals(listOf(first.id), schedules.getExtraClassSubjects().first().map { it.id })
                val operational = schedules.getOperationalSessionsWithSubjects().first()
                assertEquals(listOf(aSlot), operational.map { it.session })
                val today = todaySnapshot(historical, attendance.getAttendanceWithSubjects(historical).first(), operational)
                assertEquals(listOf(aSlot), today.scheduled.map { it.session })
                assertEquals(listOf(prior), today.records.map { it.record })

                val staleExtra = AttendanceRecord(subjectId = staleChoice.id, sessionId = null, date = historical,
                    status = null, classType = ClassType.EXTRA)
                assertFalse(attendance.addAttendance(staleExtra))
                assertFalse(attendance.addAttendance(staleRegular))
                ensureOperationalAttendanceForDate(historical, attendance, schedules)
                repeat(2) { ensureOperationalAttendanceForDate(nextWeek, attendance, schedules) }
                assertEquals(setOf(prior.id), attendance.getAttendanceBySubject(second.id).map { it.id }.toSet())
                assertEquals(listOf(aSlot.id), attendance.getAttendanceByDate(nextWeek).map { it.sessionId })
                attendance.markPresent(AttendanceWithSubject(prior, second))
                assertEquals(prior.copy(status = AttendanceStatus.PRESENT), attendance.getAttendanceBySubject(second.id).single())

                val exported = PlannerRestoreRepository(db).createBackup()
                val apply = PlannerRestoreRepository(restored).prepare(exported.reader()).apply()
                assertEquals(PlannerRestoreResult.APPLIED, apply)
                assertEquals(beta.copy(isArchived = true), restored.semesterDao().getArchivedSemestersWithSubjects().first().single().semester)
                assertEquals(prior.copy(status = AttendanceStatus.PRESENT), restored.attendanceRecordDao().getBySubject(second.id).single())
                assertEquals(setOf(aSlot, bSlot), restored.sessionRecordDao().getAll().first().toSet())

                schedules.setSemesterArchived(beta.id, false)
                assertEquals(setOf(first.id, second.id), schedules.getExtraClassSubjects().first().map { it.id }.toSet())
                assertTrue(attendance.addAttendance(staleExtra))
                ensureOperationalAttendanceForDate(nextWeek, attendance, schedules)
                assertEquals(setOf(aSlot.id, bSlot.id), attendance.getAttendanceByDate(nextWeek).map { it.sessionId }.toSet())
            }
        } finally {
            restored.close()
            db.close()
        }
    }

    @Test fun archiveAndUnarchiveNeedExplicitConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val term = Semester(name = "Confirm fixture", startDate = LocalDate.of(2025, 1, 1), endDate = null)
        val archive = context.getString(R.string.terms_archive_action)
        val unarchive = context.getString(R.string.terms_unarchive_action)
        val activity = compose.activity
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            var archived by rememberSaveable { mutableStateOf(false) }
            SemesterArchiveAction(term.copy(isArchived = archived), onSetArchived = { id, next ->
                assertEquals(term.id, id)
                changes += next
                archived = next
            })
        }
        compose.onNodeWithText(archive).performClick()
        compose.onNodeWithText(context.getString(R.string.terms_archive_title, term.name)).assertIsDisplayed()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), changes) }
        compose.onNodeWithText(archive).performClick()
        compose.onNodeWithText(context.getString(R.string.terms_confirm_archive)).performClick()
        compose.onNodeWithText(unarchive).performClick()
        compose.onNodeWithText(context.getString(R.string.terms_confirm_unarchive)).performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), changes) }
    }
}

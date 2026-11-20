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
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}

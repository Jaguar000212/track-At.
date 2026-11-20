package com.jaguar.attendancetracker.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.CampusBriefWidgetReceiver
import com.jaguar.attendancetracker.MainActivity
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.WidgetReceiver
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.reminders.OPEN_EXAM_REMINDER
import com.jaguar.attendancetracker.backend.reminders.examReminderIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class CampusBriefWidgetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun campusBriefCountsOnlySelectedWindowsAndDoesNotMaterializeAttendance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val today = LocalDate.of(2026, 11, 20)
        try {
            runBlocking {
                val term = Semester(name = "Private term", startDate = today.minusMonths(1), endDate = null)
                val subject = Subject(name = "Private subject", semesterId = term.id, startDate = term.startDate,
                    isEnded = false, color = "PINK", professor = null, roomNo = null, notes = null)
                val dao = db.plannerRestoreDao()
                dao.insertSemesters(listOf(term))
                dao.insertSubjects(listOf(subject))
                dao.insertSchedule(listOf(SessionRecord(subjectId = subject.id,
                    dayOfWeek = today.dayOfWeek.value, startDate = today.minusWeeks(1))))
                dao.insertAttendance(listOf(
                    record(subject.id, today, AttendanceStatus.PRESENT),
                    record(subject.id, today, AttendanceStatus.ABSENT),
                    record(subject.id, today, AttendanceStatus.CANCELLED),
                    record(subject.id, today, null),
                    record(subject.id, today.minusDays(1), AttendanceStatus.PRESENT),
                ))
                dao.insertBoard(listOf(
                    BoardItem(title = "Due today", dueDate = today),
                    BoardItem(title = "Due at boundary", dueDate = today.plusDays(6)),
                    BoardItem(title = "Completed task", dueDate = today, isDone = true),
                    BoardItem(title = "Overdue", dueDate = today.minusDays(1)),
                    BoardItem(title = "Later due", dueDate = today.plusDays(7)),
                    BoardItem(title = "No deadline"),
                ))
                dao.insertExams(listOf(
                    Exam(title = "Starts today", date = today, semesterId = term.id),
                    Exam(title = "Exam at boundary", date = today.plusDays(6)),
                    Exam(title = "Later exam", date = today.plusDays(7)),
                    Exam(title = "Past exam", date = today.minusDays(1)),
                    Exam(title = "Unknown exam date"),
                ))

                val before = dao.attendance()
                assertEquals(CampusBriefCounts(today, 2, 1, 2, 2), campusBriefCounts(db, today))
                assertEquals(before, dao.attendance())
                assertEquals(CampusBriefCounts(today.plusWeeks(2), 0, 0, 0, 0), campusBriefCounts(db, today.plusWeeks(2)))
            }
        } finally { db.close() }
    }

    @Test fun campusBriefRegistersSeparatelyAndWarmIntentNavigatesToToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val campus = ComponentName(context, CampusBriefWidgetReceiver::class.java)
        val tracker = ComponentName(context, WidgetReceiver::class.java)
        val receiver = context.packageManager.getReceiverInfo(campus, PackageManager.GET_META_DATA)
        assertFalse(receiver.exported)
        assertTrue(receiver.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) != 0)
        assertTrue(context.packageManager.getReceiverInfo(tracker, PackageManager.GET_META_DATA)
            .metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) != 0)

        val start = campusBriefTodayIntent(context)
        assertEquals(ComponentName(context, MainActivity::class.java), start.component)
        assertEquals(OPEN_CAMPUS_BRIEF_TODAY, start.action)
        compose.onNodeWithText(context.getString(R.string.exams_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.exams_intro)).assertIsDisplayed()
        val activity = compose.activity
        val originalLaunch = Intent(activity.intent)
        compose.runOnIdle { activity.startActivity(start) }
        compose.onNodeWithText(context.getString(R.string.today_open_day_view)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.exams_intro)).assertDoesNotExist()

        val reminder = examReminderIntent(context)
        assertEquals(ComponentName(context, MainActivity::class.java), reminder.component)
        assertEquals(OPEN_EXAM_REMINDER, reminder.action)
        assertNull(reminder.extras)
        assertNull(reminder.data)
        compose.runOnIdle { activity.startActivity(reminder) }
        compose.onNodeWithText(context.getString(R.string.exams_intro)).assertIsDisplayed()
        assertSame(activity, compose.activity)
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            assertTrue(originalLaunch.filterEquals(activity.intent))
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText(context.getString(R.string.today_open_day_view)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.exams_intro)).assertDoesNotExist()
    }

    private fun record(subjectId: UUID, date: LocalDate, status: AttendanceStatus?) =
        AttendanceRecord(subjectId = subjectId, sessionId = null, date = date, status = status, classType = ClassType.EXTRA)
}

package com.jaguar.attendancetracker.ui.widget

import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import java.time.LocalDate

internal const val CAMPUS_BRIEF_WINDOW_DAYS = 7

internal data class CampusBriefCounts(
    val asOf: LocalDate,
    val savedMarked: Long,
    val savedUnmarked: Long,
    val dueTasks: Long,
    val datedExams: Long,
)

internal suspend fun campusBriefCounts(db: AttendanceDatabase, asOf: LocalDate): CampusBriefCounts =
    db.withTransaction {
        val dao = db.campusBriefDao()
        val last = asOf.plusDays((CAMPUS_BRIEF_WINDOW_DAYS - 1).toLong())
        val attendance = dao.attendance(asOf)
        CampusBriefCounts(asOf, attendance.marked, attendance.unmarked,
            dao.dueTasks(asOf, last), dao.datedExams(asOf, last))
    }

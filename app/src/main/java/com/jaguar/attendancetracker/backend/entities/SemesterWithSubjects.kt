package com.jaguar.attendancetracker.backend.entities

import androidx.room.Embedded
import androidx.room.Relation

data class SemesterWithSubjects(
    @Embedded val semester: Semester,
    @Relation(
        parentColumn = "id",
        entityColumn = "semesterId"
    )
    val subjects: List<Subject>
) {
    fun getCumulativeAttendancePercentage(): Float {
        val totalClasses = subjects.sumOf { it.totalClasses }
        val attendedClasses = subjects.sumOf { it.attendedClasses }
        return if (totalClasses == 0) 0f
        else (attendedClasses.toFloat() / totalClasses) * 100
    }
}

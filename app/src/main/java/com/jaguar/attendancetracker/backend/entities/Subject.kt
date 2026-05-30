package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "Subjects")
data class Subject(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val semester: Int,
    val startDate: LocalDate,
    val isEnded: Boolean,
    val color: String,
    val professor: String?,
    val roomNo: String?,
    val notes: String?,

    val totalClasses: Int = 0,

    val attendedClasses: Int = 0,
    val minAttendance: Int = 75
) {
    fun attendancePercentage(): Float = if (totalClasses == 0) 0f
    else (attendedClasses.toFloat() / totalClasses) * 100

    fun requiredToMakeUp(): Int {
        if (totalClasses == 0) return 0
        val target = minAttendance / 100.0
        val current = attendedClasses.toDouble() / totalClasses

        return if (current >= target) {
            val canSkip = (attendedClasses / target) - totalClasses
            canSkip.toInt()
        } else {
            val needed = (target * totalClasses - attendedClasses) / (1.0 - target)
            -kotlin.math.ceil(needed).toInt()
        }
    }

}
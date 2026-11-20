package com.jaguar.attendancetracker.backend

import com.jaguar.attendancetracker.backend.entities.Subject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SubjectCalculationsTest {
    private fun subject(attended: Int, total: Int, target: Int = 75) = Subject(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = "Mathematics", semesterId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        startDate = LocalDate.of(2026, 1, 1), isEnded = false, color = "BLUE",
        professor = null, roomNo = null, notes = null,
        totalClasses = total, attendedClasses = attended, minAttendance = target,
    )

    @Test fun emptyAndPartialAttendancePercentages() {
        assertEquals(0f, subject(0, 0).attendancePercentage(), 0f)
        assertEquals(0, subject(0, 0).requiredToMakeUp())
        assertEquals(0f, subject(0, 4).attendancePercentage(), 0f)
        assertEquals(75f, subject(3, 4).attendancePercentage(), 0.0001f)
        assertEquals(100f, subject(4, 4).attendancePercentage(), 0.0001f)
        assertEquals(100f / 3, subject(1, 3).attendancePercentage(), 0.0001f)
    }

    @Test fun makeUpUsesNegativeCountsAndSkipAllowanceUsesPositiveCounts() {
        assertEquals(-12, subject(0, 4).requiredToMakeUp())
        assertEquals(-4, subject(2, 4).requiredToMakeUp())
        assertEquals(-1, subject(2, 3).requiredToMakeUp())
        assertEquals(0, subject(3, 4).requiredToMakeUp())
        assertEquals(0, subject(4, 5).requiredToMakeUp())
        assertEquals(1, subject(4, 4).requiredToMakeUp())
        assertEquals(2, subject(6, 6).requiredToMakeUp())
        assertEquals(-2, subject(1, 4, 50).requiredToMakeUp())
        assertEquals(0, subject(4, 4, 100).requiredToMakeUp())
    }

    @Test fun seventyFivePercentDecisionsAreMinimalAcrossSmallValidCounts() {
        // 75% has exact binary representation; arbitrary targets have a separate known rounding issue.
        for (total in 1..40) for (attended in 0..total) {
            val decision = subject(attended, total).requiredToMakeUp()
            if (attended * 4 >= total * 3) {
                assertTrue(decision >= 0)
                assertTrue(attended * 4 >= (total + decision) * 3)
                assertTrue(attended * 4 < (total + decision + 1) * 3)
            } else {
                val needed = -decision
                assertTrue(needed > 0)
                assertTrue((attended + needed) * 4 >= (total + needed) * 3)
                assertTrue((attended + needed - 1) * 4 < (total + needed - 1) * 3)
            }
        }
    }
}

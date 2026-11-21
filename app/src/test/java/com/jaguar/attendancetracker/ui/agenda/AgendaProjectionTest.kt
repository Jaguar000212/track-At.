package com.jaguar.attendancetracker.ui.agenda

import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class AgendaProjectionTest {
    private val today = LocalDate.of(2026, 12, 31)

    @Test
    fun datedAndUndatedStaySeparateAndCompletedTasksStayOut() {
        val sharedId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val agenda = projectAgenda(
            listOf(
                BoardItem(id = sharedId, title = "Read chapter", dueDate = today),
                BoardItem(title = "Already finished", dueDate = today, isDone = true),
                BoardItem(title = "Check handout"),
                BoardItem(title = "Tomorrow task", dueDate = today.plusDays(1)),
            ),
            listOf(Exam(id = sharedId, title = "Physics", date = today), Exam(title = "Dates not published")),
        )
        assertEquals(listOf("Read chapter", "Physics"), agenda.dated.getValue(today).map { it.title })
        assertEquals(listOf("Tomorrow task"), agenda.dated.getValue(today.plusDays(1)).map { it.title })
        assertEquals(listOf("Check handout", "Dates not published"), agenda.undated.map { it.title })
        assertEquals(2, agenda.dated.getValue(today).map { it.key }.distinct().size)
    }

    @Test
    fun startsOnRequestedLocaleWeekdayAcrossNewYear() {
        assertEquals(
            (0L..6L).map { LocalDate.of(2026, 12, 28).plusDays(it) },
            agendaWeek(today, DayOfWeek.MONDAY),
        )
        assertEquals(LocalDate.of(2026, 12, 27), agendaWeek(today, DayOfWeek.SUNDAY).first())
        assertEquals(today, agendaWeek(today, DayOfWeek.THURSDAY).first())
    }
}

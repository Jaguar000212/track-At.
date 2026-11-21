package com.jaguar.attendancetracker.ui.agenda

import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal enum class AgendaKind { TASK, EXAM }

internal data class AgendaItem(val key: String, val title: String, val kind: AgendaKind, val date: LocalDate?)

internal data class AgendaEntries(
    val dated: Map<LocalDate, List<AgendaItem>>,
    val undated: List<AgendaItem>,
)

internal fun projectAgenda(tasks: List<BoardItem>, exams: List<Exam>): AgendaEntries {
    val items = (
        tasks.filterNot { it.isDone }.map { AgendaItem("task-${it.id}", it.title, AgendaKind.TASK, it.dueDate) } +
            exams.map { AgendaItem("exam-${it.id}", it.title, AgendaKind.EXAM, it.date) }
        ).sortedWith(compareBy(AgendaItem::kind, AgendaItem::title, AgendaItem::key))
    return AgendaEntries(items.filter { it.date != null }.groupBy { requireNotNull(it.date) }, items.filter { it.date == null })
}

internal fun agendaWeek(selected: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val start = selected.with(TemporalAdjusters.previousOrSame(firstDay))
    return List(7) { start.plusDays(it.toLong()) }
}

package com.jaguar.attendancetracker.backend.repositories

import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.RolloverTemplateRow
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal data class RolloverTermDetails(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val targetAttendance: Int,
)

internal data class RolloverSource(
    val name: String, val courses: List<RolloverTemplateRow>, val slots: List<RolloverSlotChoice>,
)
internal data class RolloverSlotChoice(val row: SessionRecord, val ordinal: Int)
internal data class RolloverSlotPreview(val courseName: String, val weekday: Int, val ordinal: Int, val firstDate: LocalDate)
internal fun firstRolloverOccurrence(start: LocalDate, weekday: Int): LocalDate? {
    if (weekday !in 1..7) return null
    return try { start.plusDays(((weekday - start.dayOfWeek.value + 7) % 7).toLong()) }
    catch (_: DateTimeException) { null }
}

private val sourceSlotOrder = compareBy<SessionRecord>({ it.dayOfWeek }, { it.orderNo }, { it.id })
internal data class RolloverCoursePreview(val name: String, val color: SubjectColor)
internal data class RolloverPreview(
    val sourceName: String,
    val newTerm: RolloverTermDetails,
    val courses: List<RolloverCoursePreview>,
    val slots: List<RolloverSlotPreview> = emptyList(),
)

internal enum class RolloverInputIssue { INVALID_TERM, INVALID_SELECTION, INVALID_SLOT_DATES, SOURCE_CHANGED }
internal enum class RolloverResult { CREATED, ALREADY_CREATED, STALE_PREVIEW, TIMETABLE_FULL }

internal sealed interface RolloverPreparation {
    data class Invalid(val issue: RolloverInputIssue) : RolloverPreparation
    data class Ready(val confirmation: PreparedTermRollover) : RolloverPreparation
}

/** In-memory only. Keep private course data out of saved-state bundles and logs. */
internal interface PreparedTermRollover {
    val preview: RolloverPreview
    suspend fun apply(): RolloverResult
}

internal class TermRolloverRepository @Inject constructor(private val db: AttendanceDatabase) {
    suspend fun readSource(id: UUID): RolloverSource? = db.withTransaction {
        val term = db.semesterDao().find(id) ?: return@withTransaction null
        val courses = db.subjectDao().rolloverChoices(id).filter { it.name.isNotBlank() }
        val allowed = courses.mapTo(HashSet()) { it.id }
        val ordinals = mutableMapOf<Pair<UUID, Int>, Int>()
        val slots = db.sessionRecordDao().rolloverSchedule(id)
            .filter { it.subjectId in allowed && it.dayOfWeek in 1..7 }.sortedWith(sourceSlotOrder).map {
                val key = it.subjectId to it.dayOfWeek
                val ordinal = (ordinals[key] ?: 0) + 1
                ordinals[key] = ordinal
                RolloverSlotChoice(it, ordinal)
            }
        RolloverSource(term.name, courses, slots)
    }

    suspend fun prepare(
        sourceTerm: UUID,
        destination: RolloverTermDetails,
        selectedCourses: List<UUID>,
        selectedSlots: List<RolloverSlotChoice> = emptyList(),
    ): RolloverPreparation {
        if (destination.name.isBlank() || destination.targetAttendance !in 0..100 ||
            (destination.endDate != null && destination.endDate < destination.startDate)
        ) return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_TERM)
        val ids = selectedCourses.toList()
        if (ids.isEmpty() || ids.toSet().size != ids.size) {
            return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION)
        }

        val slots = selectedSlots.sortedWith { a, b -> sourceSlotOrder.compare(a.row, b.row) }
        val courseIds = ids.toHashSet()
        if (slots.map { it.row.id }.toSet().size != slots.size || slots.any { it.row.subjectId !in courseIds }) {
            return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION)
        }
        if (slots.any {
                val first = firstRolloverOccurrence(destination.startDate, it.row.dayOfWeek)
                first == null || (destination.endDate != null && first > destination.endDate)
            }
        ) return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SLOT_DATES)

        return db.withTransaction {
            val source = db.semesterDao().find(sourceTerm)
                ?: return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED)
            val templates = readTemplates(sourceTerm, ids)
                ?: return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED)
            if (templates.any { it.name.isBlank() }) {
                return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION)
            }
            if (!sameSelectedSlots(sourceTerm, slots)) {
                return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED)
            }
            RolloverPreparation.Ready(Pending(source, destination, templates, slots))
        }
    }

    // Chunk IN binds so even large imported source terms cannot exceed SQLite's parameter limit.
    private suspend fun readTemplates(source: UUID, ids: List<UUID>): List<RolloverTemplateRow>? {
        val rows = ids.chunked(500).flatMap { db.subjectDao().rolloverTemplates(source, it) }
            .associateBy { it.id }
        if (rows.size != ids.size) return null
        return ids.map(rows::getValue)
    }

    private suspend fun sameSelectedSlots(term: UUID, slots: List<RolloverSlotChoice>): Boolean {
        if (slots.isEmpty()) return true
        val live = slots.map { it.row.id }.chunked(500)
            .flatMap { db.sessionRecordDao().rolloverSelectedSchedule(term, it) }.sortedWith(sourceSlotOrder)
        return live == slots.map { it.row }
    }

    private inner class Pending(
        private val source: Semester,
        destination: RolloverTermDetails,
        private val templates: List<RolloverTemplateRow>,
        private val slots: List<RolloverSlotChoice>,
    ) : PreparedTermRollover {
        private val lock = Mutex()
        private var used = false
        private val courses = templates.map {
            RolloverCoursePreview(it.name, SubjectColor.entries.firstOrNull { color -> color.name == it.color } ?: SubjectColor.PINK)
        }
        override val preview = RolloverPreview(source.name, destination, courses.toList(), slots.map {
            RolloverSlotPreview(templates.first { course -> course.id == it.row.subjectId }.name,
                it.row.dayOfWeek, it.ordinal, requireNotNull(firstRolloverOccurrence(destination.startDate, it.row.dayOfWeek)))
        })
        private val term = Semester(
            name = destination.name,
            startDate = destination.startDate,
            endDate = destination.endDate,
            targetAttendance = destination.targetAttendance,
        )
        private val copies = courses.map {
            Subject(
                name = it.name,
                semesterId = term.id,
                startDate = term.startDate,
                isEnded = false,
                color = it.color.name,
                professor = null,
                roomNo = null,
                notes = null,
                minAttendance = term.targetAttendance,
            )
        }

        private val targets = templates.zip(copies).associate { (source, copy) -> source.id to copy.id }
        private val newSlotIds = slots.associate { it.row.id to UUID.randomUUID() }

        private suspend fun prepareCopiedSlots(): List<SessionRecord>? {
            if (slots.isEmpty()) return emptyList()
            val chosen = slots.groupBy { it.row.dayOfWeek }
            val existing = db.sessionRecordDao().readTimetableOrder(chosen.keys).groupBy { it.session.dayOfWeek }
            return buildList {
                for ((day, rows) in chosen) {
                    val current = existing[day].orEmpty()
                    var order = (current.filterNot { it.archived }.maxOfOrNull { it.session.orderNo }
                        ?.coerceAtLeast(-1)?.toLong() ?: -1L) + 1
                    val reserved = current.filter { it.archived }.mapTo(HashSet()) { it.session.orderNo }
                    for (entry in rows) {
                        while (order <= Int.MAX_VALUE && order.toInt() in reserved) order++
                        if (order > Int.MAX_VALUE) return null
                        val slot = entry.row
                        add(SessionRecord(newSlotIds.getValue(slot.id), targets.getValue(slot.subjectId),
                            order.toInt(), day, term.startDate))
                        order++
                    }
                }
            }
        }

        override suspend fun apply(): RolloverResult = lock.withLock {
            if (used) return@withLock RolloverResult.ALREADY_CREATED
            currentCoroutineContext().ensureActive()
            // Once explicitly confirmed, record completion even if the screen leaves
            // during commit. The same confirmation must never create a second term.
            withContext(NonCancellable) {
                val result = db.withTransaction {
                    val currentSource = db.semesterDao().find(source.id)
                    if (currentSource != source || readTemplates(source.id, templates.map { it.id }) != templates ||
                        !sameSelectedSlots(source.id, slots)
                    ) {
                        return@withTransaction RolloverResult.STALE_PREVIEW
                    }
                    val copiedSlots = prepareCopiedSlots() ?: return@withTransaction RolloverResult.TIMETABLE_FULL
                    db.semesterDao().insert(term)
                    for (copy in copies) db.subjectDao().insert(copy)
                    if (copiedSlots.isNotEmpty()) db.plannerRestoreDao().insertSchedule(copiedSlots)
                    RolloverResult.CREATED
                }
                if (result == RolloverResult.CREATED) used = true
                result
            }
        }
    }
}

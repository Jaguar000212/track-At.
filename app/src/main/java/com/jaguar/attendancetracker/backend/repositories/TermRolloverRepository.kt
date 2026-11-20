package com.jaguar.attendancetracker.backend.repositories

import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.RolloverTemplateRow
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal data class RolloverTermDetails(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val targetAttendance: Int,
)

internal data class RolloverCoursePreview(val name: String, val color: SubjectColor)
internal data class RolloverPreview(
    val sourceName: String,
    val newTerm: RolloverTermDetails,
    val courses: List<RolloverCoursePreview>,
)

internal enum class RolloverInputIssue { INVALID_TERM, INVALID_SELECTION, SOURCE_CHANGED }
internal enum class RolloverResult { CREATED, ALREADY_CREATED, STALE_PREVIEW }

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
    suspend fun prepare(
        sourceTerm: UUID,
        destination: RolloverTermDetails,
        selectedCourses: List<UUID>,
    ): RolloverPreparation {
        if (destination.name.isBlank() || destination.targetAttendance !in 0..100 ||
            (destination.endDate != null && destination.endDate < destination.startDate)
        ) return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_TERM)
        val ids = selectedCourses.toList()
        if (ids.isEmpty() || ids.toSet().size != ids.size) {
            return RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION)
        }

        return db.withTransaction {
            val source = db.semesterDao().find(sourceTerm)
                ?: return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED)
            val templates = readTemplates(sourceTerm, ids)
                ?: return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED)
            if (templates.any { it.name.isBlank() }) {
                return@withTransaction RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION)
            }
            RolloverPreparation.Ready(Pending(source, destination, templates))
        }
    }

    // Chunk IN binds so even large imported source terms cannot exceed SQLite's parameter limit.
    private suspend fun readTemplates(source: UUID, ids: List<UUID>): List<RolloverTemplateRow>? {
        val rows = ids.chunked(500).flatMap { db.subjectDao().rolloverTemplates(source, it) }
            .associateBy { it.id }
        if (rows.size != ids.size) return null
        return ids.map(rows::getValue)
    }

    private inner class Pending(
        private val source: Semester,
        destination: RolloverTermDetails,
        private val templates: List<RolloverTemplateRow>,
    ) : PreparedTermRollover {
        private val lock = Mutex()
        private var used = false
        private val courses = templates.map {
            RolloverCoursePreview(it.name, SubjectColor.entries.firstOrNull { color -> color.name == it.color } ?: SubjectColor.PINK)
        }
        override val preview = RolloverPreview(source.name, destination, courses.toList())
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

        override suspend fun apply(): RolloverResult = lock.withLock {
            if (used) return@withLock RolloverResult.ALREADY_CREATED
            currentCoroutineContext().ensureActive()
            // Once explicitly confirmed, record completion even if the screen leaves
            // during commit. The same confirmation must never create a second term.
            withContext(NonCancellable) {
                val result = db.withTransaction {
                    val currentSource = db.semesterDao().find(source.id)
                    if (currentSource != source || readTemplates(source.id, templates.map { it.id }) != templates) {
                        return@withTransaction RolloverResult.STALE_PREVIEW
                    }
                    db.semesterDao().insert(term)
                    for (copy in copies) db.subjectDao().insert(copy)
                    RolloverResult.CREATED
                }
                if (result == RolloverResult.CREATED) used = true
                result
            }
        }
    }
}

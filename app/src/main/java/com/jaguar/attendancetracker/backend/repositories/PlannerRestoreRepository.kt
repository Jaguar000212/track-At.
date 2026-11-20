package com.jaguar.attendancetracker.backend.repositories

import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.ui.imExport.BackupData
import com.jaguar.attendancetracker.ui.imExport.PlannerBackup
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFormat
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome
import com.jaguar.attendancetracker.ui.imExport.PlannerMergePlanner
import com.jaguar.attendancetracker.ui.imExport.PlannerMergePreview
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Reader

internal enum class PlannerRestoreResult {
    APPLIED, NOTHING_TO_APPLY, STALE_PREVIEW,
}

/**
 * A short-lived in-memory confirmation handle containing private student data.
 * Never log it, save it in instance state, serialize it, or persist it.
 */
internal interface PreparedPlannerRestore {
    val preview: PlannerMergePreview

    /** Call only after explicit confirmation; not currently connected to any UI. */
    suspend fun apply(): PlannerRestoreResult
}

internal class PlannerRestoreRepository(private val db: AttendanceDatabase) {
    private val dao get() = db.plannerRestoreDao()

    suspend fun prepare(source: Reader): PreparedPlannerRestore {
        // The caller owns and closes the Reader. A future file transport must report malformed
        // UTF-8 instead of replacing it before the strict planner decoder sees the text.
        val incoming = withContext(Dispatchers.IO) { PlannerBackupFormat.decode(source) }
        val before = db.withTransaction { capture() }
        return Pending(incoming, before)
    }

    private suspend fun capture() = PlannerBackup(
        attendance = BackupData(
            timestamp = 0,
            semesters = dao.semesters(),
            subjects = dao.subjects(),
            schedule = dao.schedule(),
            attendance = dao.attendance(),
        ),
        board = dao.board(),
    )

    private inner class Pending(
        private val incoming: PlannerBackup,
        private val before: PlannerBackup,
    ) : PreparedPlannerRestore {
        override val preview = PlannerMergePlanner.preview(before, incoming)

        override suspend fun apply(): PlannerRestoreResult = db.withTransaction {
            val current = capture()
            // Compare the entire actual state, not just counts or visible outcomes. If any
            // retained row changed, confirm a new preview even if the same rows would insert.
            if (before != current) return@withTransaction PlannerRestoreResult.STALE_PREVIEW
            val fresh = PlannerMergePlanner.preview(current, incoming)
            if (fresh != preview) return@withTransaction PlannerRestoreResult.STALE_PREVIEW

            val semesters = fresh.semesters.inserts()
            val subjects = fresh.subjects.inserts().map { it.copy(totalClasses = 0, attendedClasses = 0) }
            val sessions = fresh.schedule.inserts()
            val attendance = fresh.attendance.inserts()
            val board = fresh.board.inserts()
            if (semesters.isEmpty() && subjects.isEmpty() && sessions.isEmpty() &&
                attendance.isEmpty() && board.isEmpty()
            ) return@withTransaction PlannerRestoreResult.NOTHING_TO_APPLY

            if (semesters.isNotEmpty()) dao.insertSemesters(semesters)
            if (subjects.isNotEmpty()) dao.insertSubjects(subjects)
            if (sessions.isNotEmpty()) dao.insertSchedule(sessions)
            if (attendance.isNotEmpty()) dao.insertAttendance(attendance)

            for ((subjectId, total) in fresh.totalsAfterAcceptedAttendance) {
                check(dao.updateDerivedTotals(subjectId, total.totalClasses, total.attendedClasses) == 1) {
                    "Restore subject aggregate update did not affect exactly one row"
                }
            }
            // A failure in any write propagates out of withTransaction and rolls back everything.
            if (board.isNotEmpty()) dao.insertBoard(board)
            PlannerRestoreResult.APPLIED
        }
    }

    private fun <T> List<PlannerMergeRow<T>>.inserts(): List<T> =
        filter { it.outcome == PlannerMergeOutcome.INSERT }.map { it.incoming }
}

package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SessionRecordDao {
    @Upsert
    suspend fun upsertAll(records: List<SessionRecord>)

    @Insert
    suspend fun insert(record: SessionRecord)

    @Update
    suspend fun update(record: SessionRecord)

    @Transaction
    @Query("""
        SELECT r.*, t.id AS termId, t.isArchived AS archived FROM SessionRecords r
        JOIN Subjects s ON s.id = r.subjectId JOIN Semesters t ON t.id = s.semesterId
    """)
    fun observeTimetable(): Flow<List<TimetableEntry>>

    @Query("""
        SELECT r.*, t.id AS termId, t.isArchived AS archived FROM SessionRecords r
        JOIN Subjects s ON s.id = r.subjectId JOIN Semesters t ON t.id = s.semesterId
        WHERE r.dayOfWeek IN (:days)
    """)
    suspend fun readTimetableOrder(days: Set<Int>): List<TimetableOrderRow>

    @Query("""
        UPDATE SessionRecords SET orderNo = :orderNo WHERE id = :id AND subjectId IN (
          SELECT s.id FROM Subjects s JOIN Semesters t ON t.id = s.semesterId WHERE t.isArchived = :archived
        )
    """)
    suspend fun updateScopedOrder(id: UUID, orderNo: Int, archived: Boolean)

    @Transaction
    suspend fun reorderGroups(
        expectedByDay: Map<Int, List<TimetableOrderRow>>,
        orderByGroup: Map<TimetableGroup, List<UUID>>,
    ): Boolean {
        if (orderByGroup.isEmpty()) return true
        val changedDays = orderByGroup.keys.mapTo(HashSet()) { it.day }
        if (expectedByDay.keys != changedDays) return false
        val actualByDay = readTimetableOrder(changedDays).groupBy { it.session.dayOfWeek }
        if (actualByDay.keys != expectedByDay.keys) return false
        // Check all affected weekdays before any write, including their opposite group.
        if (actualByDay.any { (day, actual) -> actual.toSet() != expectedByDay.getValue(day).toSet() }) return false
        if (orderByGroup.any { (group, desired) ->
                val ids = actualByDay.getValue(group.day).filter { it.group == group }.map { it.session.id }.toSet()
                desired.size != ids.size || desired.toSet() != ids
            }
        ) return false
        val currentOrders = actualByDay.mapValues { (_, day) ->
            day.associate { it.session.id to it.session.orderNo }.toMutableMap()
        }
        for ((group, desired) in orderByGroup.entries.sortedWith(compareBy({ it.key.day }, { it.key.archived }))) {
            val actual = actualByDay.getValue(group.day)
            val orders = currentOrders.getValue(group.day)
            val reserved = actual.filter { it.group != group }.map { orders.getValue(it.session.id) }
            for ((id, orderNo) in orderAssignments(desired, reserved)) {
                if (orders[id] != orderNo) {
                    updateScopedOrder(id, orderNo, group.archived)
                    orders[id] = orderNo
                }
            }
        }
        return true
    }

    @Delete
    suspend fun delete(record: SessionRecord)

    @Query("DELETE FROM SessionRecords where subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: UUID)

    @Query("SELECT * FROM SessionRecords")
    fun getAll(): Flow<List<SessionRecord>>

    @Query("SELECT r.* FROM SessionRecords r JOIN Subjects s ON r.subjectId = s.id WHERE s.semesterId = :term")
    suspend fun rolloverSchedule(term: UUID): List<SessionRecord>

    @Query("SELECT r.* FROM SessionRecords r JOIN Subjects s ON r.subjectId = s.id WHERE s.semesterId = :term AND r.id IN (:ids)")
    suspend fun rolloverSelectedSchedule(term: UUID, ids: List<UUID>): List<SessionRecord>

    @Query("SELECT * FROM SessionRecords WHERE subjectId = :subjectId order by dayOfWeek")
    suspend fun getBySubject(subjectId: UUID): List<SessionRecord>

    @Query("""
        SELECT r.* FROM SessionRecords r JOIN Subjects s ON s.id = r.subjectId
        JOIN Semesters t ON t.id = s.semesterId WHERE r.dayOfWeek = :day AND t.isArchived = 0
    """)
    suspend fun getOperationalByDay(day: Int): List<SessionRecord>

    @Transaction
    @Query("""
        SELECT r.* FROM SessionRecords r JOIN Subjects s ON s.id = r.subjectId
        JOIN Semesters t ON t.id = s.semesterId WHERE t.isArchived = 0 ORDER BY r.dayOfWeek, r.orderNo
    """)
    fun getOperationalWithSubjects(): Flow<List<SessionsWithSubject>>

}

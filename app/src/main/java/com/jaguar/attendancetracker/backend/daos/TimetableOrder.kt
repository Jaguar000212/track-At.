package com.jaguar.attendancetracker.backend.daos

import androidx.room.Embedded
import androidx.room.Relation
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import java.util.UUID

data class TimetableOrderRow(
    @Embedded val session: SessionRecord,
    val termId: UUID,
    val archived: Boolean,
)

data class TimetableGroup(val day: Int, val archived: Boolean)

val TimetableOrderRow.group: TimetableGroup
    get() = TimetableGroup(session.dayOfWeek, archived)

data class TimetableEntry(
    @Embedded val order: TimetableOrderRow,
    @Relation(parentColumn = "subjectId", entityColumn = "id") val subject: Subject,
)

/** UI ordering is total even for imported legacy rows with duplicate order numbers. */
internal val timetableEntryOrder = compareBy<TimetableEntry>(
    { it.order.session.dayOfWeek }, { it.order.archived }, { it.order.session.orderNo },
    { it.order.session.id },
)

/** The untouched group reserves its current order numbers. */
internal fun orderAssignments(
    desired: List<UUID>, occupiedOrders: Collection<Int>,
): List<Pair<UUID, Int>> {
    val occupied = occupiedOrders.toHashSet()
    var next = 0
    return desired.map {
        while (next in occupied) next++
        it to next++
    }
}

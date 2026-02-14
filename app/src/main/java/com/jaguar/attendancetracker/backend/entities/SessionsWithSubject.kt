package com.jaguar.attendancetracker.backend.entities

import androidx.room.Embedded
import androidx.room.Relation

data class SessionsWithSubject(
    @Embedded val session: SessionRecord,

    @Relation(parentColumn = "subjectId", entityColumn = "id") val subject: Subject
)

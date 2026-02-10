package com.jaguar.attendancetracker.backend.entities

import androidx.room.Embedded

data class SessionsWithSubject(
    @Embedded(prefix = "sr_") val session: SessionRecord,
    @Embedded(prefix = "s_") val subject: Subject
)

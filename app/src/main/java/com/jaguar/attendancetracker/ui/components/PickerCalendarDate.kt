package com.jaguar.attendancetracker.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material 3 encodes a date-only selection at midnight UTC, not at local midnight. */
internal fun LocalDate.toPickerDateMillis(): Long =
    atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

/** Do not interpret picker millis as an event in the device zone. */
internal fun Long.fromPickerDateMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

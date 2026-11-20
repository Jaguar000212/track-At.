package com.jaguar.attendancetracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class PickerCalendarDateTest {
    @Test fun midnightUtcRoundTripsAcrossWinterSummerAndDeviceZones() {
        val original = TimeZone.getDefault()
        val dates = listOf(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 7, 20))
        try {
            for (zone in listOf("America/Los_Angeles", "UTC", "Asia/Kolkata")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(zone, ZoneId.systemDefault().id)
                for (date in dates) {
                    val millis = Instant.parse("${date}T00:00:00Z").toEpochMilli()
                    assertEquals(millis, date.toPickerDateMillis())
                    assertEquals(date, millis.fromPickerDateMillis())
                    // The old interpretation reproduces the bug in a negative device offset.
                    val oldLocal = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    assertEquals(if (zone == "America/Los_Angeles") date.minusDays(1) else date, oldLocal)
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}

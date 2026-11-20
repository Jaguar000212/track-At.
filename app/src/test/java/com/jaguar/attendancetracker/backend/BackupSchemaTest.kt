package com.jaguar.attendancetracker.backend

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.jaguar.attendancetracker.backend.entities.*
import com.jaguar.attendancetracker.backend.enums.*
import com.jaguar.attendancetracker.ui.imExport.BackupData
import com.jaguar.attendancetracker.ui.imExport.LocalDateAdapter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class BackupSchemaTest {
    // Match the actual export builder without instantiating Android ViewModel/Context.
    private val gson = GsonBuilder().registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting().create()
    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-${n.toString().padStart(12, '0')}")
    private val date = LocalDate.of(2024, 2, 29)

    @Test fun allBackupCollectionsRoundTripWithRelationshipsAndOptionalFields() {
        val semester = Semester(id(1), "Spring", date, null, 80, false)
        val subject = Subject(id(2), "Math \"A\"\nλ", semester.id, date, false, "#123456",
            "Professor", null, "Notes\nwith Unicode 😀", 8, 6, 75)
        val session = SessionRecord(id(3), subject.id, 2, 4, date)
        val records = AttendanceStatus.entries.mapIndexed { index, status ->
            AttendanceRecord(id(4 + index), subject.id, session.id, date.plusDays(index.toLong()), status, ClassType.REGULAR)
        } + AttendanceRecord(id(7), subject.id, null, date, null, ClassType.EXTRA)
        val backup = BackupData(DB_VERSION, 123456789L, listOf(semester), listOf(subject), listOf(session), records)
        val json = gson.toJson(backup)
        assertEquals(backup, gson.fromJson(json, BackupData::class.java))
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(setOf("version", "timestamp", "semesters", "subjects", "schedule", "attendance"), root.keySet())
        assertEquals(DB_VERSION, root["version"].asInt)
        assertEquals("2024-02-29", root["semesters"].asJsonArray[0].asJsonObject["startDate"].asString)
        assertEquals(subject.id.toString(), root["schedule"].asJsonArray[0].asJsonObject["subjectId"].asString)
        assertEquals("PRESENT", root["attendance"].asJsonArray[0].asJsonObject["status"].asString)
        assertEquals("EXTRA", root["attendance"].asJsonArray[3].asJsonObject["classType"].asString)
    }

    @Test fun omittedCollectionsStayDistinctFromExplicitEmptyCollections() {
        val omitted = BackupData(version = DB_VERSION, timestamp = 42L)
        val omittedJson = JsonParser.parseString(gson.toJson(omitted)).asJsonObject
        assertEquals(setOf("version", "timestamp"), omittedJson.keySet())
        assertEquals(omitted, gson.fromJson(omittedJson, BackupData::class.java))
        val empty = BackupData(DB_VERSION, 42L, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, gson.fromJson(gson.toJson(empty), BackupData::class.java))
        assertTrue(JsonParser.parseString(gson.toJson(empty)).asJsonObject["subjects"].asJsonArray.size() == 0)
        val partial = BackupData(DB_VERSION, 42L, attendance = emptyList())
        assertEquals(partial, gson.fromJson(gson.toJson(partial), BackupData::class.java))
    }

    @Test fun dateAdapterUsesIsoDatesNotRoomEpochDays() {
        for (value in listOf(date, LocalDate.of(1970, 1, 1), LocalDate.of(1999, 12, 31))) {
            assertEquals("\"$value\"", gson.toJson(value, LocalDate::class.java))
            assertEquals(value, gson.fromJson("\"$value\"", LocalDate::class.java))
        }
        for (invalid in listOf("2023-02-29", "2024-13-01", "29/02/2024", "19782")) {
            assertThrows(DateTimeParseException::class.java) {
                LocalDateAdapter().deserialize(JsonParser.parseString("\"$invalid\""), LocalDate::class.java, null)
            }
        }
    }
}

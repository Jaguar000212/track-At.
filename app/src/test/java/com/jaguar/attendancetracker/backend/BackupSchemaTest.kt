package com.jaguar.attendancetracker.backend

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.jaguar.attendancetracker.backend.entities.*
import com.jaguar.attendancetracker.backend.enums.*
import com.jaguar.attendancetracker.ui.imExport.BackupData
import com.jaguar.attendancetracker.ui.imExport.AttendanceBackupFormat
import com.jaguar.attendancetracker.ui.imExport.LocalDateAdapter
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupRequiresPlannerRestore
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class BackupSchemaTest {
    // Adapter tests exercise date JSON directly; backup tests exercise the production codec.
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
        val backup = BackupData(
            timestamp = 123456789L, semesters = listOf(semester), subjects = listOf(subject),
            schedule = listOf(session), attendance = records,
        )
        val json = encode(backup)
        assertEquals(backup, decode(json))
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(setOf("version", "timestamp", "semesters", "subjects", "schedule", "attendance"), root.keySet())
        assertEquals(10, root["version"].asInt)
        assertEquals("2024-02-29", root["semesters"].asJsonArray[0].asJsonObject["startDate"].asString)
        assertEquals(subject.id.toString(), root["schedule"].asJsonArray[0].asJsonObject["subjectId"].asString)
        assertEquals("PRESENT", root["attendance"].asJsonArray[0].asJsonObject["status"].asString)
        assertEquals("EXTRA", root["attendance"].asJsonArray[3].asJsonObject["classType"].asString)
    }

    @Test fun omittedCollectionsStayDistinctFromExplicitEmptyCollections() {
        val omitted = BackupData(timestamp = 42L)
        val omittedJson = JsonParser.parseString(encode(omitted)).asJsonObject
        assertEquals(setOf("version", "timestamp"), omittedJson.keySet())
        assertEquals(omitted, decode(encode(omitted)))
        val empty = BackupData(10, 42L, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, decode(encode(empty)))
        assertTrue(JsonParser.parseString(encode(empty)).asJsonObject["subjects"].asJsonArray.size() == 0)
        val partial = BackupData(timestamp = 42L, attendance = emptyList())
        val partialGolden = """{"version":10,"timestamp":42,"attendance":[]}"""
        assertEquals(partial, decode(partialGolden))
        assertEquals(JsonParser.parseString(partialGolden), JsonParser.parseString(encode(partial)))
    }

    @Test fun versionTenExportMatchesGoldenFixture() {
        val data = BackupData(
            timestamp = 42L,
            semesters = listOf(Semester(id(1), "Spring", date, null, 80, false)),
            subjects = listOf(Subject(id(2), "Math", id(1), date, false, "PINK",
                null, "R1", null, 7, 6, 75)),
            schedule = listOf(SessionRecord(id(3), id(2), 2, 4, date)),
            attendance = listOf(AttendanceRecord(id(4), id(2), id(3), date,
                AttendanceStatus.PRESENT, ClassType.REGULAR)),
        )
        val golden = """
            {
              "version":10,"timestamp":42,
              "semesters":[{"id":"${id(1)}","name":"Spring","startDate":"2024-02-29",
                "targetAttendance":80,"isArchived":false}],
              "subjects":[{"id":"${id(2)}","name":"Math","semesterId":"${id(1)}",
                "startDate":"2024-02-29","isEnded":false,"color":"PINK","roomNo":"R1",
                "totalClasses":7,"attendedClasses":6,"minAttendance":75}],
              "schedule":[{"id":"${id(3)}","subjectId":"${id(2)}","orderNo":2,
                "dayOfWeek":4,"startDate":"2024-02-29"}],
              "attendance":[{"id":"${id(4)}","subjectId":"${id(2)}","sessionId":"${id(3)}",
                "date":"2024-02-29","status":"PRESENT","classType":"REGULAR"}]
            }
        """.trimIndent()
        assertEquals(JsonParser.parseString(golden), JsonParser.parseString(encode(data)))
        assertEquals(data, decode(golden))
    }

    @Test fun existingLegacyVersionEightAndNineFixturesStillImport() {
        for (version in listOf(8, 9)) {
            val order = if (version == 9) "\"orderNo\":7," else ""
            val legacy = """
                {
                  "version":$version,"timestamp":42,
                  "subjects":[{"id":"${id(2)}","name":"Math","semester":4,"startDate":"2024-02-29",
                    "isEnded":false,"color":"PINK","totalClasses":7,"attendedClasses":6,"minAttendance":75}],
                  "schedule":[{"id":"${id(3)}","subjectId":"${id(2)}",
                    ${order}"dayOfWeek":4,"startDate":"2024-02-29"}]
                }
            """.trimIndent()
            val restored = decode(legacy)
            val semester = restored.semesters!!.single()
            assertEquals(version, restored.version)
            assertEquals("Semester 4", semester.name)
            assertEquals(semester.id, restored.subjects!!.single().semesterId)
            assertEquals(id(2), restored.subjects.single().id)
            assertEquals(id(3), restored.schedule!!.single().id)
            assertEquals(if (version == 9) 7 else 0, restored.schedule.single().orderNo)
            assertNull(restored.attendance)
        }
    }

    @Test fun onlyExactPlannerDiscriminatorRedirectsLegacyImports() {
        val planner = """{"format":"track-at-planner","version":1,"board":[]}"""
        assertThrows(PlannerBackupRequiresPlannerRestore::class.java) { decode(planner) }
        val otherFormat = """{"format":"other","version":999,"timestamp":42,"attendance":[]}"""
        assertEquals(BackupData(999, 42, attendance = emptyList()), decode(otherFormat))
    }

    private fun encode(backup: BackupData) = StringBuilder().also {
        AttendanceBackupFormat.write(backup, it)
    }.toString()

    private fun decode(json: String) = AttendanceBackupFormat.read(json.reader())

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

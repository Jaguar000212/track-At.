package com.jaguar.attendancetracker.ui.imExport

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.Reader
import java.time.LocalDate
import java.util.UUID

class PlannerBackupFormatTest {
    private val date = LocalDate.of(2024, 2, 29)
    private val attendance = BackupData(
        timestamp = 42L,
        semesters = listOf(Semester(id(1), "Archived", date, date.plusDays(30), 80, true)),
        subjects = listOf(Subject(id(2), "Math", id(1), date, false, "PINK",
            "Instructor", null, "Private notes", 4, 3, 75)),
        schedule = listOf(SessionRecord(id(3), id(2), 2, 4, date)),
        attendance = listOf(
            AttendanceRecord(id(4), id(2), id(99), date, AttendanceStatus.PRESENT, ClassType.REGULAR),
            AttendanceRecord(id(5), id(2), null, date, null, ClassType.EXTRA),
        ),
    )
    private val full = PlannerBackup(
        attendance,
        listOf(BoardItem(id(6), "Read chapter λ \"A\"\n😀"), BoardItem(id(7), "Prepare slides", date, true)),
    )

    @Test fun completeEnvelopeRoundTripsWithStableIdsAndNoLegacyWireChanges() {
        val json = PlannerBackupFormat.encode(full)
        val parsed = JsonParser.parseString(json).asJsonObject
        val legacyBefore = legacyExport()
        assertEquals(setOf("format", "version", "attendance", "board"), parsed.keySet())
        assertEquals("track-at-planner", parsed["format"].asString)
        assertEquals(1, parsed["version"].asInt)
        assertEquals(JsonParser.parseString(legacyBefore), parsed["attendance"])
        assertEquals(full, PlannerBackupFormat.decode(json.reader()))
        assertEquals(json, PlannerBackupFormat.encode(PlannerBackupFormat.decode(json.reader())))
        assertEquals(legacyBefore, legacyExport())
    }

    @Test fun emptyCollectionsAreCompleteButMissingCollectionsAreNot() {
        val golden = """
            {"format":"track-at-planner","version":1,"attendance":{"version":10,"timestamp":42,
              "semesters":[],"subjects":[],"schedule":[],"attendance":[]},"board":[]}
        """.trimIndent()
        val empty = PlannerBackup(BackupData(10, 42L, emptyList(), emptyList(), emptyList(), emptyList()), emptyList())
        assertEquals(empty, PlannerBackupFormat.decode(golden.reader()))
        assertEquals(JsonParser.parseString(golden), JsonParser.parseString(PlannerBackupFormat.encode(empty)))
        reject { it.getAsJsonObject("attendance").remove("schedule") }
        reject { it.remove("board") }
        assertThrows(IllegalArgumentException::class.java) {
            PlannerBackupFormat.encode(full.copy(attendance = attendance.copy(semesters = null)))
        }
    }

    @Test fun legacyAndUnknownFormatsAndVersionsAreNotInterpretedAsPlannerBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannerBackupFormat.decode(legacyExport().reader())
        }
        reject { it.addProperty("format", "another-tool") }
        reject { it.addProperty("version", 2) }
        reject { it.addProperty("version", "1") }
        reject { it.addProperty("version", 1.0) }
        reject { it.getAsJsonObject("attendance").addProperty("version", 11) }
        reject { it.add("exams", JsonParser.parseString("[]")) }
        reject { it.getAsJsonArray("board")[0].asJsonObject.addProperty("futureFlag", true) }
    }

    @Test fun duplicateRecordIdsAndMissingForeignKeyParentsAreRejected() {
        reject { it.getAsJsonArray("board").add(it.getAsJsonArray("board")[0]) }
        reject {
            val rows = it.getAsJsonObject("attendance").getAsJsonArray("semesters")
            rows.add(rows[0])
        }
        reject {
            it.getAsJsonObject("attendance").getAsJsonArray("subjects")[0].asJsonObject
                .addProperty("semesterId", id(99).toString())
        }
        reject {
            it.getAsJsonObject("attendance").getAsJsonArray("schedule")[0].asJsonObject
                .addProperty("subjectId", id(99).toString())
        }
        reject {
            it.getAsJsonObject("attendance").getAsJsonArray("attendance")[0].asJsonObject
                .addProperty("subjectId", id(99).toString())
        }
    }

    @Test fun existingScheduleLinkCannotPointAtAnotherSubjectsClass() {
        reject {
            val attendanceSection = it.getAsJsonObject("attendance")
            val subjects = attendanceSection.getAsJsonArray("subjects")
            subjects.add(subjects[0].asJsonObject.deepCopy().apply {
                addProperty("id", id(8).toString())
            })
            attendanceSection.getAsJsonArray("attendance")[0].asJsonObject.apply {
                addProperty("subjectId", id(8).toString())
                addProperty("sessionId", id(3).toString())
            }
        }
    }

    @Test fun malformedFieldsAreRejectedBeforeDecodingEntities() {
        reject { it.getAsJsonArray("board")[0].asJsonObject.addProperty("id", "1-0-0-0-1") }
        reject { it.getAsJsonArray("board")[0].asJsonObject.addProperty("title", " \t") }
        reject { it.getAsJsonArray("board")[0].asJsonObject.addProperty("dueDate", "2025-02-29") }
        reject { it.getAsJsonArray("board")[0].asJsonObject.addProperty("isDone", "false") }
        reject {
            it.getAsJsonObject("attendance").getAsJsonArray("subjects")[0].asJsonObject
                .addProperty("totalClasses", 2147483648L)
        }
        reject {
            it.getAsJsonObject("attendance").getAsJsonArray("attendance")[0].asJsonObject
                .addProperty("status", "MISSING")
        }
    }

    @Test fun malformedJsonDuplicateKeysTrailingDataAndUnboundedInputAreRejected() {
        val good = PlannerBackupFormat.encode(full)
        val malformed = listOf(
            "", "[]", "$good {}", good.dropLast(1), good.replaceFirst("{", "{/*comment*/"),
            good.replaceFirst("{", "{\"version\":1,"),
            good.replaceFirst("true", "TRUE"), good.replaceFirst("false", "FaLsE"),
            good.replace("\"isDone\": false", "\"isDone\": NULL"),
            "\uFEFF$good",
            good.replace("Prepare slides", "Prepare\nslides"),
            good.replace("Prepare slides", "Prepare\\'slides"),
            "[".repeat(20) + "0" + "]".repeat(20),
            " ".repeat(8 * 1024 * 1024 + 1),
        )
        for (input in malformed) {
            assertThrows(IllegalArgumentException::class.java) {
                PlannerBackupFormat.decode(input.reader())
            }
        }
    }

    @Test fun readerThatNeverMakesProgressIsRejected() {
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int = 0
            override fun close() = Unit
        }
        assertThrows(IllegalArgumentException::class.java) { PlannerBackupFormat.decode(reader) }
    }

    private fun reject(change: (JsonObject) -> Unit) {
        val parsed = JsonParser.parseString(PlannerBackupFormat.encode(full)).asJsonObject
        change(parsed)
        assertThrows(IllegalArgumentException::class.java) {
            PlannerBackupFormat.decode(parsed.toString().reader())
        }
    }

    private fun legacyExport() = StringBuilder().also {
        AttendanceBackupFormat.write(attendance, it)
    }.toString()

    private fun id(n: Int) = UUID(0, n.toLong())
}

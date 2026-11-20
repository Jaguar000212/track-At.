package com.jaguar.attendancetracker.ui.imExport

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import java.util.UUID

/** Internal format proposal. Not used by the existing export or restore UI. */
internal data class PlannerBackup(
    val attendance: BackupData,
    val board: List<BoardItem>,
)

internal object PlannerBackupFormat {
    const val FORMAT = "track-at-planner"
    private const val VERSION = 1
    private const val MAX_CHARS = 8 * 1024 * 1024
    private const val MAX_DEPTH = 12
    private val gson = GsonBuilder().registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting().create()

    /** Validate everything before returning any serialized output. */
    fun encode(backup: PlannerBackup): String {
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("attendance", gson.toJsonTree(backup.attendance))
            add("board", JsonArray().apply {
                for (item in backup.board) {
                    add(JsonObject().apply {
                        addProperty("id", item.id.toString())
                        addProperty("title", item.title)
                        item.dueDate?.let { addProperty("dueDate", it.toString()) }
                        addProperty("isDone", item.isDone)
                    })
                }
            })
        }
        validate(root)
        return gson.toJson(root).also {
            require(it.length <= MAX_CHARS) { "Planner backup is too large" }
        }
    }

    /** Parse and validate the complete file. This codec never reads from or writes to Room. */
    fun decode(source: Reader): PlannerBackup {
        val root = parseBounded(source)
        validate(root)
        val attendance = gson.fromJson(root["attendance"], BackupData::class.java)
        val board = root["board"].asJsonArray.map {
            val item = it.obj()
            BoardItem(
                id = item["id"].uuid(),
                title = item["title"].string(),
                dueDate = item.nullable("dueDate")?.date(),
                isDone = item["isDone"].flag(),
            )
        }
        return PlannerBackup(attendance, board)
    }

    private fun validate(root: JsonObject) {
        root.fields(setOf("format", "version", "attendance", "board"))
        require(root["format"].string() == FORMAT) { "Unknown planner backup format" }
        require(root["version"].integer() == VERSION.toLong()) { "Unsupported planner backup version" }
        val attendance = root["attendance"].obj()
        attendance.fields(setOf("version", "timestamp", "semesters", "subjects", "schedule", "attendance"))
        require(attendance["version"].integer() == ATTENDANCE_BACKUP_VERSION.toLong()) {
            "Unsupported attendance section version"
        }
        attendance["timestamp"].integer()

        val semesters = attendance["semesters"].rows(
            required = setOf("id", "name", "startDate", "targetAttendance", "isArchived"),
            optional = setOf("endDate"),
        ) {
            it["name"].string()
            it["startDate"].date()
            it.nullable("endDate")?.date()
            it["targetAttendance"].int()
            it["isArchived"].flag()
        }
        val subjects = attendance["subjects"].rows(
            required = setOf("id", "name", "semesterId", "startDate", "isEnded", "color",
                "totalClasses", "attendedClasses", "minAttendance"),
            optional = setOf("professor", "roomNo", "notes"),
        ) {
            require(it["semesterId"].uuid() in semesters) { "Subject is missing its semester" }
            it["name"].string()
            it["startDate"].date()
            it["isEnded"].flag()
            it["color"].string()
            listOf("professor", "roomNo", "notes").forEach { name -> it.nullable(name)?.string() }
            listOf("totalClasses", "attendedClasses", "minAttendance").forEach { name -> it[name].int() }
        }
        val scheduleSubjects = mutableMapOf<UUID, UUID>()
        attendance["schedule"].rows(
            required = setOf("id", "subjectId", "orderNo", "dayOfWeek", "startDate"),
        ) {
            val subjectId = it["subjectId"].uuid()
            require(subjectId in subjects) { "Schedule item is missing its subject" }
            scheduleSubjects[it["id"].uuid()] = subjectId
            it["orderNo"].int()
            it["dayOfWeek"].int()
            it["startDate"].date()
        }
        attendance["attendance"].rows(
            required = setOf("id", "subjectId", "date", "classType"),
            optional = setOf("sessionId", "status"),
        ) {
            val subjectId = it["subjectId"].uuid()
            require(subjectId in subjects) { "Attendance record is missing its subject" }
            // This UUID is historical metadata, not a database foreign key. A deleted timetable
            // slot must not make otherwise valid attendance history unbackable.
            it.nullable("sessionId")?.uuid()?.let { sessionId ->
                val scheduleSubject = scheduleSubjects[sessionId]
                require(scheduleSubject == null || scheduleSubject == subjectId) {
                    "Attendance record and schedule item have different subjects"
                }
            }
            it["date"].date()
            it.nullable("status")?.let { status -> AttendanceStatus.valueOf(status.string()) }
            ClassType.valueOf(it["classType"].string())
        }
        root["board"].rows(required = setOf("id", "title", "isDone"), optional = setOf("dueDate")) {
            require(it["title"].string().isNotBlank()) { "Board item title is required" }
            it.nullable("dueDate")?.date()
            it["isDone"].flag()
        }
    }

    private fun JsonElement.rows(
        required: Set<String>, optional: Set<String> = emptySet(), check: (JsonObject) -> Unit,
    ): Set<UUID> {
        require(isJsonArray) { "Expected a collection" }
        val ids = mutableSetOf<UUID>()
        asJsonArray.forEach { value ->
            val item = value.obj()
            item.fields(required, optional)
            require(ids.add(item["id"].uuid())) { "Duplicate record ID in a collection" }
            check(item)
        }
        return ids
    }

    private fun JsonObject.fields(required: Set<String>, optional: Set<String> = emptySet()) {
        val actual = keySet()
        require(actual.containsAll(required)) { "Missing planner backup fields" }
        require(actual.all { it in required || it in optional }) { "Unknown planner backup fields" }
    }

    private fun JsonObject.nullable(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }
    private fun JsonElement.obj(): JsonObject = (this as? JsonObject) ?: errorFormat("Expected an object")
    private fun JsonElement.string(): String =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: errorFormat("Expected a string")
    private fun JsonElement.flag(): Boolean =
        (this as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean ?: errorFormat("Expected a boolean")
    private fun JsonElement.integer(): Long =
        (this as? JsonPrimitive)?.takeIf { it.isNumber }?.asString?.toLongOrNull()
            ?: errorFormat("Expected a whole number")
    private fun JsonElement.int(): Int = integer().also {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Number is out of range" }
    }.toInt()
    private fun JsonElement.date(): LocalDate = try {
        val text = string()
        LocalDate.parse(text).also { require(it.toString() == text) }
    } catch (_: java.time.DateTimeException) {
        errorFormat("Expected an ISO date")
    }
    private fun JsonElement.uuid(): UUID = UUID.fromString(string()).also {
        require(it.toString() == string()) { "Expected a canonical UUID" }
    }

    private fun parseBounded(source: Reader): JsonObject {
        val raw = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            require(count > 0) { "Planner backup reader made no progress" }
            require(raw.length + count <= MAX_CHARS) { "Planner backup is too large" }
            raw.append(buffer, 0, count)
        }
        try {
            val input = raw.toString()
            validateLexemes(input)
            JsonReader(StringReader(input)).use { reader ->
                reader.isLenient = false
                val root = reader.value(0).obj()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing planner backup content" }
                return root
            }
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Malformed planner backup", e)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Malformed planner backup", e)
        }
    }

    // Gson 2.10 strict JsonReader still accepts some non-JSON string characters/escapes.
    // Reject those separately, without interpreting values or loosening the reader grammar.
    private fun validateLexemes(text: String) {
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                require(c >= ' ') { "Invalid raw character in a JSON string" }
                if (escaped) {
                    require(c in "\"\\/bfnrtu") { "Invalid JSON escape" }
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else if (c == '"') {
                inString = true
            } else {
                // Gson 2.10 accepts mixed-case literals even when leniency is off.
                // Our wire format has only integer numbers, so no decimal/exponent symbols.
                require(c in "{}[]:,-0123456789truefalsn \t\r\n") { "Invalid JSON token" }
            }
        }
        require(!inString) { "Unterminated JSON string" }
    }

    private fun JsonReader.value(depth: Int): JsonElement {
        require(depth <= MAX_DEPTH) { "Planner backup is nested too deeply" }
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                beginObject()
                while (hasNext()) {
                    val name = nextName()
                    require(!obj.has(name)) { "Duplicate JSON field" }
                    obj.add(name, value(depth + 1))
                }
                endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                beginArray()
                while (hasNext()) array.add(value(depth + 1))
                endArray()
            }
            JsonToken.STRING -> JsonPrimitive(nextString())
            JsonToken.NUMBER -> JsonPrimitive(nextString().toLongOrNull() ?: errorFormat("Expected a whole number"))
            JsonToken.BOOLEAN -> JsonPrimitive(nextBoolean())
            JsonToken.NULL -> JsonNull.INSTANCE.also { nextNull() }
            else -> errorFormat("Malformed planner backup")
        }
    }

    private fun errorFormat(message: String): Nothing = throw IllegalArgumentException(message)
}

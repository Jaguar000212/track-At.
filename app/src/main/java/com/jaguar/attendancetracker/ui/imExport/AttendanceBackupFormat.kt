package com.jaguar.attendancetracker.ui.imExport

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.io.Reader
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Pure codec for the existing attendance exports. Room migrations do not change this format. */
internal object AttendanceBackupFormat {
    private val gson = GsonBuilder().registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting().create()

    fun write(backup: BackupData, writer: Appendable) {
        gson.toJson(backup, writer)
    }

    fun read(reader: Reader): BackupData {
        val jsonRoot = gson.fromJson(reader, JsonObject::class.java)
        val jsonVersion = if (jsonRoot.has("version")) {
            jsonRoot.get("version").asInt
        } else {
            throw Exception("Invalid backup file: Missing version information.")
        }
        migrateJson(jsonRoot, jsonVersion)
        return gson.fromJson(jsonRoot, BackupData::class.java)
    }

    private fun migrateJson(json: JsonObject, fromVersion: Int) {
        var currentVersion = fromVersion
        val targetVersion = ATTENDANCE_BACKUP_VERSION
        while (currentVersion < targetVersion) {
            when (currentVersion) {
                8 -> migrateToVersion9(json)
                9 -> migrateToVersion10(json)
            }
            currentVersion++
        }
    }

    private fun migrateToVersion9(json: JsonObject) {
        if (json.has("schedule")) {
            val scheduleArray = json.getAsJsonArray("schedule")
            scheduleArray.forEach { element ->
                val sessionObj = element.asJsonObject
                if (!sessionObj.has("orderNo")) {
                    sessionObj.addProperty("orderNo", 0)
                }
            }
        }
    }

    private fun migrateToVersion10(json: JsonObject) {
        if (json.has("semesters")) return // Already has semesters

        val semestersArray = JsonArray()
        val semesterMap = mutableMapOf<Int, String>()
        // BackupData's LocalDate fields go through the LocalDateAdapter registered on `gson`,
        // which (de)serializes them as ISO date strings - not the epoch-day Long Room stores
        // them as in the database. Match that format here or restoring this backup will throw
        // a DateTimeParseException when it's read back in.
        val nowIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (json.has("subjects")) {
            val subjectsArray = json.getAsJsonArray("subjects")
            subjectsArray.forEach { element ->
                val subjectObj = element.asJsonObject
                if (subjectObj.has("semester")) {
                    val oldSemInt = subjectObj.get("semester").asInt
                    val semesterId = semesterMap.getOrPut(oldSemInt) {
                        val newId = UUID.randomUUID().toString()
                        val semesterObj = JsonObject().apply {
                            addProperty("id", newId)
                            addProperty("name", "Semester $oldSemInt")
                            addProperty("startDate", nowIso)
                            addProperty("targetAttendance", 75)
                            addProperty("isArchived", false)
                        }
                        semestersArray.add(semesterObj)
                        newId
                    }
                    subjectObj.addProperty("semesterId", semesterId)
                    subjectObj.remove("semester")
                }
            }
        }
        json.add("semesters", semestersArray)
    }
}

class LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun serialize(
        src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(formatter))
    }

    override fun deserialize(
        json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?
    ): LocalDate {
        return LocalDate.parse(json?.asString, formatter)
    }
}

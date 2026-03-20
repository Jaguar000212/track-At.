package com.jaguar.attendancetracker.ui.imexport

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.jaguar.attendancetracker.backend.DB_VERSION
import com.jaguar.attendancetracker.backend.repositories.ImportExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ImExportViewModel @Inject constructor(
    private val ieRepo: ImportExportRepository, @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val gson = GsonBuilder().registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting().create()

    fun exportData(
        uri: Uri, includeSubjects: Boolean, includeSchedule: Boolean, includeAttendance: Boolean
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val backupData = withContext(Dispatchers.IO) {
                    val subjects = if (includeSubjects) ieRepo.getAllSubjects().first() else null
                    val schedule =
                        if (includeSchedule) ieRepo.getAllSessionRecords().first() else null
                    val attendance =
                        if (includeAttendance) ieRepo.getAllAttendanceRecords().first() else null

                    BackupData(
                        subjects = subjects, schedule = schedule, attendance = attendance
                    )
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            gson.toJson(backupData, writer)
                        }
                    }
                }

                Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val jsonRoot = gson.fromJson(reader, JsonObject::class.java)

                            val jsonVersion = if (jsonRoot.has("version")) {
                                jsonRoot.get("version").asInt
                            } else {
                                throw Exception("Invalid backup file: Missing version information.")
                            }
                            migrateJson(jsonRoot, jsonVersion)

                            val backupData = gson.fromJson(jsonRoot, BackupData::class.java)

                            ieRepo.upsertDataInTransaction(
                                subjects = backupData.subjects,
                                sessions = backupData.schedule,
                                attendance = backupData.attendance
                            )
                        }
                    }
                }
                Toast.makeText(context, "Import Successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                val message = if (e.message?.contains("Constraint") == true) {
                    "Data Error: Constraint violation."
                } else {
                    e.message ?: "Unknown error occurred."
                }
                Toast.makeText(context, "Import Failed: $message", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun migrateJson(json: JsonObject, fromVersion: Int) {
        var currentVersion = fromVersion
        val targetVersion = DB_VERSION
        while (currentVersion < targetVersion) {
            when (currentVersion) {
                8 -> migrateToVersion9(json)
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

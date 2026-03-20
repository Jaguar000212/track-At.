package com.jaguar.attendancetracker.ui.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.dependencies.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.util.UUID

class TrackerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val repos = EntryPointAccessors.fromApplication(
            appContext, WidgetEntryPoint::class.java
        )

        val today = LocalDate.now()
        val subjectsFlow = repos.scheduleRepo().getAllSubjects()
        val sessionsWithSubjectsFlow = repos.scheduleRepo().getSessionsWithSubjects()
        val attendanceFlow = repos.attendanceRepo().getAttendanceWithSubjects(today)

        val colors = ColorProviders(
            light = lightColorScheme(), dark = darkColorScheme()
        )

        provideContent {
            var selectedTab by remember { mutableIntStateOf(0) } // Default to 'Today'

            val subjects = subjectsFlow.collectAsState(emptyList()).value
            val allSessions = sessionsWithSubjectsFlow.collectAsState(emptyList()).value
            val todayAttendance = attendanceFlow.collectAsState(emptyList()).value

            val todayClasses = remember(allSessions, todayAttendance) {
                val dayOfWeek = today.dayOfWeek.value

                val scheduledForToday = allSessions.filter { it.session.dayOfWeek == dayOfWeek }
                    .sortedBy { it.session.orderNo }

                val attendanceMap = todayAttendance.associateBy { it.record.sessionId }

                val mergedList = scheduledForToday.map { sessionWS ->
                    attendanceMap[sessionWS.session.id] ?: AttendanceWithSubject(
                        record = AttendanceRecord(
                            id = UUID.randomUUID(),
                            sessionId = sessionWS.session.id,
                            subjectId = sessionWS.session.subjectId,
                            date = today,
                            status = null,
                            classType = ClassType.REGULAR
                        ), subject = sessionWS.subject
                    )
                }.toMutableList()

                val scheduledSessionIds = scheduledForToday.map { it.session.id }.toSet()
                val extras = todayAttendance.filter {
                    it.record.sessionId == null || it.record.sessionId !in scheduledSessionIds
                }
                mergedList.addAll(extras)

                mergedList.toList()
            }

            GlanceTheme(colors) {
                Scaffold(
                    backgroundColor = GlanceTheme.colors.background,
                ) {
                    Column {
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(16.dp)) {
                            TabItem(
                                title = "Today",
                                isSelected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = GlanceModifier.defaultWeight()
                            )
                            TabItem(
                                title = "Overview",
                                isSelected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }

                        if (selectedTab == 0) {
                            TodayWidget(sessions = todayClasses)
                        } else {
                            OverviewWidget(subjects = subjects)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TabItem(
        title: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Box(
            modifier = modifier.clickable(onClick).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title, style = TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) GlanceTheme.colors.primary else GlanceTheme.colors.onBackground
                )
            )
        }
    }
}
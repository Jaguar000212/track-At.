package com.jaguar.attendancetracker.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jaguar.attendancetracker.MainActivity
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.ui.theme.AppTypography

@Composable
fun TodayWidget(sessions: List<AttendanceWithSubject>) {
    if (sessions.isEmpty()) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No classes today", style = TextStyle(
                    color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium
                )
            )
        }
    } else {
        LazyColumn(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            items(sessions) { item ->
                SessionItem(item)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SessionItem(item: AttendanceWithSubject) {
    val statusColor = when (item.record.status) {
        AttendanceStatus.PRESENT -> ColorProvider(
            Color(StatusColor.GOOD.lightColor), Color(StatusColor.GOOD.darkColor)
        )

        AttendanceStatus.ABSENT -> ColorProvider(
            Color(StatusColor.ALERT.lightColor), Color(StatusColor.ALERT.darkColor)
        )

        AttendanceStatus.CANCELLED -> ColorProvider(
            Color(StatusColor.WARNING.lightColor), Color(StatusColor.WARNING.darkColor)
        )

        null -> GlanceTheme.colors.primary

    }

    val statusText = when (item.record.status) {
        AttendanceStatus.PRESENT -> "Present"
        AttendanceStatus.ABSENT -> "Absent"
        AttendanceStatus.CANCELLED -> "Cancelled"
        null -> "Scheduled"
    }

    Column(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(
                actionStartActivity<MainActivity>()
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = item.subject.name, style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = AppTypography.labelLarge.fontSize
                ), maxLines = 1, modifier = GlanceModifier.defaultWeight()
            )

            Text(
                text = statusText, style = TextStyle(
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppTypography.labelSmall.fontSize
                ), modifier = GlanceModifier.padding(horizontal = 8.dp)
            )
        }

        // Show "Extra Class" tag if applicable
        if (item.record.classType == ClassType.EXTRA) {
            Text(
                text = "Extra Class", style = TextStyle(
                    color = GlanceTheme.colors.secondary,
                    fontSize = AppTypography.bodySmall.fontSize
                )
            )
        }
    }
}
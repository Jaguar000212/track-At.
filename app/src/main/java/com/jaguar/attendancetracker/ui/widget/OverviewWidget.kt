package com.jaguar.attendancetracker.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jaguar.attendancetracker.MainActivity
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.ui.theme.AppTypography

@Composable
fun OverviewWidget(subjects: List<Subject>) {
    LazyColumn(
        modifier = GlanceModifier.fillMaxWidth().padding(12.dp)
    ) {
        items(subjects.filter { !it.isEnded }) { sub ->
            val percentage = sub.attendancePercentage().toInt()
            val progressColor = when {
                percentage >= sub.minAttendance -> ColorProvider(
                    Color(StatusColor.GOOD.lightColor), Color(StatusColor.GOOD.lightColor)
                )

                percentage >= (sub.minAttendance - 5) -> ColorProvider(
                    Color(StatusColor.WARNING.lightColor), Color(StatusColor.WARNING.lightColor)
                )

                else -> ColorProvider(
                    Color(StatusColor.ALERT.lightColor), Color(StatusColor.ALERT.lightColor)
                )
            }

            Column(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
                modifier = GlanceModifier.clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>("subjectId") to sub.id.toString()
                        )
                    )
                )
            ) {
                Text(
                    text = sub.name, style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = AppTypography.labelLarge.fontSize,
                    ), maxLines = 1, modifier = GlanceModifier.padding(16.dp)
                )
                LinearProgressIndicator(
                    progress = sub.attendancePercentage() / 100,
                    color = progressColor,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp).padding(end = 8.dp)
                )

            }

        }
    }
}
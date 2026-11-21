package com.jaguar.attendancetracker.ui.helpPage

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import com.jaguar.attendancetracker.R

enum class HelpTopics(
    @param:StringRes val title: Int, @param:StringRes val description: Int, val icon: Any, val pageNo: Int
) {
    DASHBOARD(
        R.string.help_topic_dashboard_title,
        R.string.help_topic_dashboard_summary,
        Icons.Outlined.Home,
        1
    ),
    SCHEDULE(
        R.string.help_topic_schedule_title,
        R.string.help_topic_schedule_summary,
        R.drawable.schedule_view,
        2
    ),
    DAY(
        R.string.help_topic_day_title,
        R.string.help_topic_day_summary,
        R.drawable.day_view,
        3
    ),
    SUBJECT(
        R.string.help_topic_subject_title,
        R.string.help_topic_subject_summary,
        R.drawable.subject,
        4
    ),
    IM_EXPORT(
        R.string.nav_import_export,
        R.string.help_topic_backup_summary,
        R.drawable.import_export,
        5
    )
}

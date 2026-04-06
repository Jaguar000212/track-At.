package com.jaguar.attendancetracker.ui.helpPage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import com.jaguar.attendancetracker.R

enum class HelpTopics(
    val title: String, val description: String, val icon: Any, val pageNo: Int
) {
    DASHBOARD(
        "Dashboard",
        "This is where you can see all your subjects and their attendance details. You can also add new subjects here.",
        Icons.Outlined.Home,
        1
    ),
    SCHEDULE(
        "Schedule Details",
        "Here you can see your schedule for the week. You can also add new classes to your schedule.",
        R.drawable.schedule_view,
        2
    ),
    DAY(
        "Day View",
        "This page shows you the attendance details for a specific day. You can see which classes you attended and which ones you missed.",
        R.drawable.day_view,
        3
    ),
    SUBJECT(
        "Subject Details",
        "This page shows you detailed information about a specific subject, including attendance records and statistics.",
        R.drawable.subject,
        4
    ),
    IM_EXPORT(
        "Import/Export",
        "This feature allows you to backup your data or import data from another device. Keep your attendance records safe!",
        R.drawable.import_export,
        5
    )
}
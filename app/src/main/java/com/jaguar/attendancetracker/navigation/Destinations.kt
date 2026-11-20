package com.jaguar.attendancetracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import com.jaguar.attendancetracker.R

enum class Destinations(
    val label: Int, val icon: Any, val route: String, val showInDrawer: Boolean = true
) {
    TODAY(R.string.nav_today, Icons.Outlined.Home, "/today"),
    BOARD(R.string.board_title, Icons.Outlined.Check, "/board"),
    DAYVIEW(R.string.nav_day, R.drawable.day_view, "/dayview"),
    DASHBOARD(R.string.nav_dashboard, Icons.AutoMirrored.Outlined.List, "/dashboard"),
    SCHEDULE(R.string.nav_schedule, R.drawable.schedule_view, "/scheduleview"),
    SUBJECT(R.string.nav_subject, Icons.AutoMirrored.Outlined.List, "/subject", false),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, "/settings", false),
    IM_EXPORT(R.string.nav_import_export, Icons.Outlined.Settings, "/im-export", false),
    INSIGHTS(R.string.insights_title, Icons.Outlined.Check, "/insights", false),
    LEAVE(R.string.leave_title, Icons.Outlined.Check, "/leave", false),
    EXAMS(R.string.exams_title, Icons.Outlined.Check, "/exams", false),
    PLANNER_BACKUP(R.string.planner_backup_title, Icons.Outlined.Settings, "/planner-backup", false),
    HELP(R.string.nav_help, Icons.Outlined.Settings, "/help", false)
}

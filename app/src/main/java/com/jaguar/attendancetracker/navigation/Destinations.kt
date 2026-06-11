package com.jaguar.attendancetracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import com.jaguar.attendancetracker.R

enum class Destinations(
    val label: String, val icon: Any, val route: String, val showInDrawer: Boolean = true
) {
    DAYVIEW("Day View", R.drawable.day_view, "/dayview"),
    DASHBOARD("Dashboard", Icons.Outlined.Home, "/dashboard"),
    SCHEDULE("Schedule View", R.drawable.schedule_view, "/scheduleview"),
    SUBJECT("Subject", Icons.AutoMirrored.Outlined.List, "/subject", false),
    SETTINGS("Settings", Icons.Outlined.Settings, "/settings", false),
    IM_EXPORT("Import-Export", Icons.Outlined.Settings, "/im-export", false),
    HELP("Help", Icons.Outlined.Settings, "/help", false)
}
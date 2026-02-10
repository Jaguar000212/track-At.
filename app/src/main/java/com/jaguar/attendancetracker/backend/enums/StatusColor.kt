package com.jaguar.attendancetracker.backend.enums

enum class StatusColor(
    val lightColor: Long, val darkColor: Long
) {
    ALERT(
        lightColor = 0xFFB3261E, darkColor = 0xFFCF6679
    ),
    WARNING(
        lightColor = 0xFFF9A825, darkColor = 0xFFFFB74D
    ),
    GOOD(
        lightColor = 0xFF2E7D32, darkColor = 0xFF81C784
    );

    fun color(isDark: Boolean): Long = if (isDark) darkColor else lightColor
}

package com.jaguar.attendancetracker.backend.enums

enum class SubjectColor(
    val light: Long, val dark: Long, val showInSelection: Boolean = true
) {
    RED(
        light = 0xFFF2B8B5, dark = 0xFF5F1410
    ),
    BLUE(
        light = 0xFFD3E3FD, dark = 0xFF102A56
    ),
    GREEN(
        light = 0xFFB7E1C1, dark = 0xFF143D2B
    ),
    YELLOW(
        light = 0xFFFFE08A, dark = 0xFF3F2E00
    ),
    PURPLE(
        light = 0xFFE8DEF8, dark = 0xFF3A2E6E
    ),
    TEAL(
        light = 0xFFB2DFDB, dark = 0xFF00363D
    ),
    PINK(
        light = 0xFFF5CDD3, dark = 0xFF4A1F24
    ),
    ORANGE(
        light = 0xFFFFD8A8, dark = 0xFF4A2A00
    ),
    GRAY(
        light = 0xFFFFFFFF, dark = 0xFF444444, false
    );

    fun color(isDark: Boolean): Long = if (isDark) dark else light
}
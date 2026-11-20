package com.jaguar.attendancetracker

import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.jaguar.attendancetracker.ui.widget.CampusBriefWidget

class CampusBriefWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CampusBriefWidget()
}

package com.jaguar.attendancetracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

internal const val OPEN_QUICK_PIN = "com.jaguar.attendancetracker.OPEN_QUICK_PIN"

internal fun quickPinIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    action = OPEN_QUICK_PIN
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}

/** A static button: no DB, learner records, recurring updates, or app-side widget state. */
class QuickPinWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val open = PendingIntent.getActivity(context, 0, quickPinIntent(context), PendingIntent.FLAG_IMMUTABLE)
        val view = RemoteViews(context.packageName, R.layout.quick_pin_widget).apply {
            setOnClickPendingIntent(R.id.quick_pin_add, open)
        }
        for (id in appWidgetIds) manager.updateAppWidget(id, view)
    }
}

package com.jaguar.attendancetracker.backend.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jaguar.attendancetracker.MainActivity
import com.jaguar.attendancetracker.R
import java.util.UUID

internal const val OPEN_EXAM_REMINDER = "com.jaguar.attendancetracker.OPEN_EXAM_REMINDER"
private const val CHANNEL = "exam_reminders"
private const val NOTICE_ID = 1

internal class AndroidReminderNotice(private val context: Context) : ReminderNotice {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun allowed(createChannel: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = manager ?: return false
        if (!nm.areNotificationsEnabled()) return false
        if (createChannel && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.exam_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        return nm.getNotificationChannel(CHANNEL)?.let { it.importance != NotificationManager.IMPORTANCE_NONE } == true
    }

    override fun post(examId: UUID) {
        if (!allowed(createChannel = false)) return
        val manager = manager ?: return
        val open = PendingIntent.getActivity(context, 0, examReminderIntent(context), PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_exam_reminder)
            .setContentTitle(context.getString(R.string.exam_reminder_notification_title))
            .setContentText(context.getString(R.string.exam_reminder_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        // Permission can change after the channel/status check.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(tag(examId), NOTICE_ID, notice)
    }

    override fun cancel(examId: UUID) { manager?.cancel(tag(examId), NOTICE_ID) }

    private fun tag(id: UUID) = UUID.nameUUIDFromBytes("notice:$id".toByteArray(Charsets.UTF_8)).toString()
}

internal fun examReminderIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
    action = OPEN_EXAM_REMINDER
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}

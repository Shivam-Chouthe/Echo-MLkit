package com.example.signal.data.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.signal.MainActivity
import com.example.signal.R

object SignalDateReminderHelper {
    const val EXTRA_MEMORY_ID = "signal_memory_id"
    private const val CHANNEL_ID = "signal_date_reminders"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun show(context: Context, memoryId: Long, title: String, reminderDate: String, summary: String, kind: Int, reminderTime: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Date reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for dates found in saved content" }
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_MEMORY_ID, memoryId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            memoryId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lead = when (kind) {
            1 -> "Coming up in 6 hours"
            2 -> "Coming up in 30 minutes"
            else -> "It's time"
        }
        val body = summary.ifBlank { "$lead: $reminderDate ${reminderTime ?: "09:00"}." }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$lead: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\nDate: $reminderDate · ${reminderTime ?: "09:00"}"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(((memoryId * 3 + kind) % Int.MAX_VALUE).toInt(), notification)
    }
}

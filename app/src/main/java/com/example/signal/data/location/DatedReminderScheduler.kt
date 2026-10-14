package com.example.signal.data.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DatedReminderScheduler {
    const val EXTRA_KIND = "signal_reminder_kind"
    private const val EVENT = 0
    private const val SIX_HOURS = 1
    private const val THIRTY_MINUTES = 2

    fun timestampForDate(dateText: String, timeText: String? = null): Long? {
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return null
        if (date.isBefore(LocalDate.now())) return null
        val time = timeText?.let { text -> runCatching { LocalTime.parse(text) }.getOrNull() }
            ?: LocalTime.of(9, 0)
        val scheduled = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return scheduled.takeIf { it > System.currentTimeMillis() }
    }

    fun schedule(context: Context, memoryId: Long, eventAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        listOf(
            SIX_HOURS to eventAt - 6 * 60 * 60 * 1000L,
            THIRTY_MINUTES to eventAt - 30 * 60 * 1000L,
            EVENT to eventAt
        ).forEach { (kind, triggerAt) ->
            if (triggerAt > now) alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, memoryId, kind)
            )
        }
    }

    fun cancel(context: Context, memoryId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(SIX_HOURS, THIRTY_MINUTES, EVENT).forEach {
            alarmManager.cancel(pendingIntent(context, memoryId, it))
        }
    }

    private fun pendingIntent(context: Context, memoryId: Long, kind: Int): PendingIntent {
        val intent = Intent(context, DatedReminderReceiver::class.java)
            .putExtra(SignalDateReminderHelper.EXTRA_MEMORY_ID, memoryId)
            .putExtra(EXTRA_KIND, kind)
        val requestCode = ((memoryId * 3 + kind) % Int.MAX_VALUE).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

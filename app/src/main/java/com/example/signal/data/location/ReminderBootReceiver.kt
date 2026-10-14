package com.example.signal.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.signal.data.local.SignalDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val memories = SignalDatabase.getDatabase(context)
                    .memoryDao().getUpcomingReminders(System.currentTimeMillis())
                memories.forEach { memory ->
                    memory.reminderAt?.let {
                        DatedReminderScheduler.schedule(context, memory.id, it)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

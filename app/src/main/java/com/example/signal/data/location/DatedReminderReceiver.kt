package com.example.signal.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.signal.data.local.SignalDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getLongExtra(SignalDateReminderHelper.EXTRA_MEMORY_ID, -1L)
        val kind = intent.getIntExtra(DatedReminderScheduler.EXTRA_KIND, 0)
        if (memoryId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val memory = SignalDatabase.getDatabase(context).memoryDao().getMemoryById(memoryId)
                    ?: return@launch
                val date = memory.reminderDate ?: return@launch
                SignalDateReminderHelper.show(
                    context,
                    memory.id,
                    memory.title,
                    date,
                    memory.summary,
                    kind,
                    memory.reminderTime
                )
            } finally {
                pending.finish()
            }
        }
    }
}

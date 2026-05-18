package com.navalrishi.busnotifier.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.navalrishi.busnotifier.BusNotifierApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BusNotifierApp
                val watches = app.database.watchDao().enabled()
                for (w in watches) app.scheduler.scheduleNext(w)
            } finally { pending.finish() }
        }
    }
}

package com.navalrishi.busnotifier.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.navalrishi.busnotifier.domain.WatchScheduler

class PollAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val watchId = intent.getLongExtra(WatchScheduler.EXTRA_WATCH_ID, -1L)
        if (watchId < 0) return
        val req = OneTimeWorkRequestBuilder<BusCheckWorker>()
            .setInputData(Data.Builder().putLong(WatchScheduler.EXTRA_WATCH_ID, watchId).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("bus_check_$watchId", ExistingWorkPolicy.REPLACE, req)
    }
}

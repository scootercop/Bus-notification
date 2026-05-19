package com.navalrishi.busnotifier.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navalrishi.busnotifier.BusNotifierApp
import com.navalrishi.busnotifier.domain.EtaCalculator
import com.navalrishi.busnotifier.domain.Notifier
import com.navalrishi.busnotifier.domain.WatchScheduler
import com.navalrishi.busnotifier.network.AtClient
import java.time.ZoneId
import java.time.ZonedDateTime

class BusCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BusNotifierApp
        val watchId = inputData.getLong(WatchScheduler.EXTRA_WATCH_ID, -1L)
        if (watchId < 0) return Result.failure()
        val watch = app.database.watchDao().byId(watchId) ?: return Result.success()
        Log.i(TAG, "watch=$watch")
        if (!watch.enabled) return Result.success()

        val calc = EtaCalculator(app.atClient)
        val res = calc.candidates(watch.routeShortName, watch.stopCode)
        Log.i(TAG, "result=$res")

        if (res is AtClient.Result.Ok) {
            val soonest = res.value.minByOrNull { it.etaMinutes }
            Log.i(TAG, "soonest=$soonest threshold=${watch.thresholdMin}")
            if (soonest != null && soonest.etaMinutes <= watch.thresholdMin) {
                Notifier.showArrival(
                    applicationContext, watchId, watch.routeShortName, watch.stopCode, soonest.etaMinutes
                )
                Log.i(TAG, "Notification updated (tripId=${soonest.tripId})")
            } else {
                Notifier.cancel(applicationContext, watchId)
                Log.i(TAG, "No candidate in threshold — notification cleared")
            }
        }

        // Reschedule the next poll
        app.scheduler.scheduleNext(watch, ZonedDateTime.now(ZoneId.of("Pacific/Auckland")).plusMinutes(1))
        return Result.success()
    }

    private companion object { const val TAG = "BusCheckWorker" }
}

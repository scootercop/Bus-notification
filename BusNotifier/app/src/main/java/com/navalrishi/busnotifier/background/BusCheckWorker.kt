package com.navalrishi.busnotifier.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navalrishi.busnotifier.BusNotifierApp
import com.navalrishi.busnotifier.data.NotifiedTrip
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
                val already = app.database.notifiedTripDao().count(watchId, soonest.tripId) > 0
                Log.i(TAG, "alreadyNotified=$already tripId=${soonest.tripId}")
                if (!already) {
                    Notifier.showArrival(
                        applicationContext, watchId, watch.routeShortName, watch.stopCode, soonest.etaMinutes
                    )
                    app.database.notifiedTripDao().insert(
                        NotifiedTrip(watchId, soonest.tripId, System.currentTimeMillis())
                    )
                    Log.i(TAG, "Notification posted")
                }
            }
            // Cleanup dedup table older than 24h
            app.database.notifiedTripDao()
                .purgeOlderThan(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        }

        // Reschedule the next poll
        app.scheduler.scheduleNext(watch, ZonedDateTime.now(ZoneId.of("Pacific/Auckland")).plusMinutes(1))
        return Result.success()
    }

    private companion object { const val TAG = "BusCheckWorker" }
}

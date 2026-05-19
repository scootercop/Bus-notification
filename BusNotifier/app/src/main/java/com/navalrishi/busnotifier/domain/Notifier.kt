package com.navalrishi.busnotifier.domain

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.navalrishi.busnotifier.MainActivity
import com.navalrishi.busnotifier.R

object Notifier {
    const val CHANNEL_ID = "bus_arrivals_v1"

    fun showArrival(ctx: Context, watchId: Long, route: String, stopCode: String, minutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val plural = if (minutes == 1) "" else "s"
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, watchId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // setOnlyAlertOnce: pings sound/heads-up the first time, then silently updates
        // the same notification on subsequent posts with the same id.
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("Bus $route — stop $stopCode")
            .setContentText("Arriving in $minutes minute$plural")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
        NotificationManagerCompat.from(ctx).notify(watchId.toInt(), builder.build())
    }

    fun cancel(ctx: Context, watchId: Long) {
        NotificationManagerCompat.from(ctx).cancel(watchId.toInt())
    }
}

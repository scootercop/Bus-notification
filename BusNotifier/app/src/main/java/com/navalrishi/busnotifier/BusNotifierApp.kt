package com.navalrishi.busnotifier

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.navalrishi.busnotifier.data.AppDatabase
import com.navalrishi.busnotifier.data.KeyStore
import com.navalrishi.busnotifier.domain.Notifier
import com.navalrishi.busnotifier.domain.WatchScheduler
import com.navalrishi.busnotifier.network.AtClient

class BusNotifierApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val keyStore by lazy { KeyStore(this) }
    val atClient by lazy { AtClient(apiKeyProvider = { keyStore.getApiKey() }) }
    val scheduler by lazy { WatchScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun createChannel() {
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(Notifier.CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                Notifier.CHANNEL_ID,
                getString(R.string.channel_arrivals_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_arrivals_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

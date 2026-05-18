package com.navalrishi.busnotifier.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.navalrishi.busnotifier.background.PollAlarmReceiver
import com.navalrishi.busnotifier.data.Watch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes the next poll time for a watch and registers a single exact alarm.
 *
 * Active days are encoded in watch.daysMask with bit i = day where Sunday=0, Saturday=6
 * (matches java.time.DayOfWeek.value % 7 since DayOfWeek.MONDAY = 1, SUNDAY = 7).
 */
class WatchScheduler(private val context: Context) {
    private val zone = ZoneId.of("Pacific/Auckland")
    private val am = context.getSystemService<AlarmManager>()!!

    fun scheduleNext(watch: Watch, fromInstant: ZonedDateTime = ZonedDateTime.now(zone)) {
        if (!watch.enabled) { cancel(watch.id); return }
        val next = computeNextPoll(watch, fromInstant) ?: run { cancel(watch.id); return }
        val pi = pendingIntent(watch.id, create = true) ?: return
        val triggerAt = next.toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(watchId: Long) {
        pendingIntent(watchId, create = false)?.let { am.cancel(it); it.cancel() }
    }

    fun computeNextPoll(watch: Watch, from: ZonedDateTime): ZonedDateTime? {
        // Walk forward up to 8 days to find the next poll slot.
        var dayOffset = 0
        while (dayOffset < 8) {
            val date = from.toLocalDate().plusDays(dayOffset.toLong())
            if (dayActive(watch.daysMask, date)) {
                val dayStart = date.atStartOfDay(from.zone).plusMinutes(watch.startMinute.toLong())
                val dayEnd = date.atStartOfDay(from.zone).plusMinutes(watch.endMinute.toLong())
                // Snap to next poll slot >= from inside [dayStart, dayEnd]
                val candidateStart = if (from.isAfter(dayStart)) {
                    val minutesIn = Duration.between(dayStart, from).toMinutes()
                    val poll = watch.pollIntervalMin.coerceAtLeast(1)
                    val slots = ((minutesIn + poll - 1) / poll)
                    dayStart.plusMinutes(slots * poll)
                } else dayStart
                if (!candidateStart.isAfter(dayEnd)) return candidateStart
            }
            dayOffset++
        }
        return null
    }

    private fun dayActive(mask: Int, date: LocalDate): Boolean {
        val bit = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
        return (mask shr bit) and 1 == 1
    }

    private fun pendingIntent(watchId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, PollAlarmReceiver::class.java).apply {
            action = "com.navalrishi.busnotifier.POLL"
            putExtra(EXTRA_WATCH_ID, watchId)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, watchId.toInt(), intent, flags)
    }

    companion object { const val EXTRA_WATCH_ID = "watchId" }
}

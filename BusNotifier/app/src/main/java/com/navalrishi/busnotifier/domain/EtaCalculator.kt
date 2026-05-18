package com.navalrishi.busnotifier.domain

import com.navalrishi.busnotifier.network.AtClient
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure ETA logic — port of check_bus.py with DST-correct Pacific/Auckland math
 * and exact route_short_name matching.
 */
class EtaCalculator(
    private val client: AtClient,
    private val clock: Clock = Clock.system(ZoneId.of("Pacific/Auckland")),
) {
    data class Candidate(val tripId: String, val etaMinutes: Int)

    /** Returns all upcoming arrivals for routeShortName at stopCode, sorted by ETA ascending. */
    suspend fun candidates(routeShortName: String, stopCode: String): AtClient.Result<List<Candidate>> {
        val stopIdResult = client.resolveStopId(stopCode)
        val stopId = when (stopIdResult) {
            is AtClient.Result.Ok -> stopIdResult.value ?: return AtClient.Result.Err("Unknown stop_code $stopCode")
            is AtClient.Result.Err -> return stopIdResult
        }

        val updatesResult = client.getTripUpdates()
        val updates = when (updatesResult) {
            is AtClient.Result.Ok -> updatesResult.value
            is AtClient.Result.Err -> return updatesResult
        }

        // Filter trips on exact short-name match. AT route_id format is
        // "<short_name>-<...>" e.g. "712-202", so split on '-' and compare first segment.
        data class ActiveTrip(val tripId: String, val delaySec: Int)
        val active = mutableListOf<ActiveTrip>()
        for (e in updates.response?.entity.orEmpty()) {
            val trip = e.tripUpdate?.trip ?: continue
            val tripId = trip.tripId ?: continue
            val routeId = trip.routeId ?: continue
            val short = routeId.substringBefore('-')
            if (short != routeShortName) continue

            val delay = e.tripUpdate.stopTimeUpdate
                .firstNotNullOfOrNull { stu -> stu.arrival?.delay ?: stu.departure?.delay }
                ?: 0
            active += ActiveTrip(tripId, delay)
        }

        val now = ZonedDateTime.now(clock)
        val result = mutableListOf<Candidate>()
        for (t in active) {
            val stRes = client.getTripStopTimes(t.tripId)
            val st = when (stRes) {
                is AtClient.Result.Ok -> stRes.value
                is AtClient.Result.Err -> continue // skip this trip, keep others
            }
            val matched = st.data.firstOrNull { row ->
                val attrs = row.attributes ?: return@firstOrNull false
                client.stopIdOf(attrs) == stopId
            }
            val time = matched?.attributes?.arrivalTime ?: matched?.attributes?.departureTime ?: continue
            val scheduled = parseGtfsTime(time, now)
            val predicted = scheduled.plusSeconds(t.delaySec.toLong())
            val minutes = Duration.between(now, predicted).toMinutes().toInt()
            if (minutes >= 0) result += Candidate(t.tripId, minutes)
        }
        return AtClient.Result.Ok(result.sortedBy { it.etaMinutes })
    }

    /** GTFS times can exceed 24h (e.g. "26:15:00" for after-midnight services). */
    internal fun parseGtfsTime(s: String, now: ZonedDateTime): ZonedDateTime {
        val parts = s.split(":")
        require(parts.size == 3) { "Bad GTFS time: $s" }
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val sec = parts[2].toInt()
        val midnight = now.toLocalDate().atStartOfDay(now.zone)
        return midnight.plusHours(h.toLong()).plusMinutes(m.toLong()).plusSeconds(sec.toLong())
    }

    @Suppress("unused") fun localTimeNow(): LocalTime = LocalTime.now(clock)
}

package com.navalrishi.busnotifier.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StopsResponse(val data: List<StopItem> = emptyList())

@Serializable
data class StopItem(val id: String, val type: String? = null)

@Serializable
data class TripUpdatesResponse(val response: TripUpdatesPayload? = null)

@Serializable
data class TripUpdatesPayload(val entity: List<TripEntity> = emptyList())

@Serializable
data class TripEntity(
    val id: String? = null,
    @SerialName("trip_update") val tripUpdate: TripUpdate? = null,
)

@Serializable
data class TripUpdate(
    val trip: TripRef? = null,
    @SerialName("stop_time_update") val stopTimeUpdate: List<StopTimeUpdate> = emptyList(),
)

@Serializable
data class TripRef(
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("route_id") val routeId: String? = null,
)

@Serializable
data class StopTimeUpdate(
    val arrival: TimeEvent? = null,
    val departure: TimeEvent? = null,
)

@Serializable
data class TimeEvent(val delay: Int? = null, val time: Long? = null)

@Serializable
data class StopTimesResponse(val data: List<StopTime> = emptyList())

@Serializable
data class StopTime(val attributes: StopTimeAttrs? = null)

@Serializable
data class StopTimeAttrs(
    @SerialName("stop_id") val stopId: JsonElement? = null,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("departure_time") val departureTime: String? = null,
)

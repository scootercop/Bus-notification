package com.navalrishi.busnotifier.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    @SerialName("stop_time_update")
    @Serializable(with = StuListSerializer::class)
    val stopTimeUpdate: List<StopTimeUpdate> = emptyList(),
)

/**
 * AT's realtime API returns `stop_time_update` as either a JSON array or a single
 * object (when the trip has exactly one upcoming stop). Accept both shapes.
 */
object StuListSerializer : KSerializer<List<StopTimeUpdate>> {
    private val delegate = ListSerializer(StopTimeUpdate.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<StopTimeUpdate> {
        val jd = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val el = jd.decodeJsonElement()) {
            is JsonArray -> el.map { jd.json.decodeFromJsonElement(StopTimeUpdate.serializer(), it) }
            is JsonObject -> listOf(jd.json.decodeFromJsonElement(StopTimeUpdate.serializer(), el))
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<StopTimeUpdate>) = delegate.serialize(encoder, value)
}

@Serializable
data class TripRef(
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("route_id") val routeId: String? = null,
)

@Serializable
data class StopTimeUpdate(
    @SerialName("stop_id") val stopId: String? = null,
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

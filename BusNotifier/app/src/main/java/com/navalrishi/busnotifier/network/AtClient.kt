package com.navalrishi.busnotifier.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AtClient(
    private val apiKeyProvider: () -> String?,
    private val http: OkHttpClient = defaultHttp(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val message: String, val code: Int? = null) : Result<Nothing>()
    }

    /** Resolve a public stop_code (number on the sign) to the internal stop_id. */
    suspend fun resolveStopId(stopCode: String): Result<String?> = get(
        url = "$BASE/gtfs/v3/stops".toHttpUrl().newBuilder()
            .addQueryParameter("filter[stop_code]", stopCode)
            .build().toString()
    ).map { body ->
        json.decodeFromString(StopsResponse.serializer(), body).data.firstOrNull()?.id
    }

    /** Realtime trip updates for all routes — caller filters. */
    suspend fun getTripUpdates(): Result<TripUpdatesResponse> = get(
        "$BASE/realtime/legacy/tripupdates"
    ).map { body -> json.decodeFromString(TripUpdatesResponse.serializer(), body) }

    /** Stoptimes for a single trip_id. */
    suspend fun getTripStopTimes(tripId: String): Result<StopTimesResponse> = get(
        "$BASE/gtfs/v3/trips/$tripId/stoptimes"
    ).map { body -> json.decodeFromString(StopTimesResponse.serializer(), body) }

    /** Pull a stop_id out of a possibly-string-or-int JSON element. */
    fun stopIdOf(attrs: StopTimeAttrs): String? = attrs.stopId?.let { el ->
        val p = el.jsonPrimitive
        p.contentOrNull
    }

    private suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider() ?: return@withContext Result.Err("AT_API_KEY not set")
        val req = Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", key)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Result.Err("HTTP ${resp.code}: ${resp.message}", resp.code)
                } else {
                    val body = resp.body?.string().orEmpty()
                    Result.Ok(body)
                }
            }
        } catch (t: Throwable) {
            Result.Err("Network error: ${t.javaClass.simpleName} ${t.message ?: ""}")
        }
    }

    private inline fun <T, R> Result<T>.map(crossinline f: (T) -> R): Result<R> = when (this) {
        is Result.Ok -> try { Result.Ok(f(value)) } catch (t: Throwable) {
            Result.Err("Parse error: ${t.message ?: t.javaClass.simpleName}")
        }
        is Result.Err -> this
    }

    private companion object {
        const val BASE = "https://api.at.govt.nz"
        fun defaultHttp() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

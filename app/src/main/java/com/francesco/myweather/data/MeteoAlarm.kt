package com.francesco.myweather.data

import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

/*
 * Official weather warnings from MeteoAlarm (the European warning service). The public feed is one JSON file per
 * country listing every warning in every language. Only Belgium is wired up so far, which is where a warning
 * can be matched to your position; there is no single free worldwide warning source.
 */

interface MeteoAlarmApi {
    @GET("api/v1/warnings/feeds-belgium")
    suspend fun belgium(): MeteoAlarmFeed

    companion object {
        const val BASE_URL = "https://feeds.meteoalarm.org/"
    }
}

@Serializable
data class MeteoAlarmFeed(val warnings: List<MeteoAlarmWarning> = emptyList())

@Serializable
data class MeteoAlarmWarning(val alert: CapAlert)

@Serializable
data class CapAlert(val identifier: String, val info: List<CapInfo> = emptyList())

@Serializable
data class CapInfo(
    val language: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val area: List<CapArea> = emptyList(),
    val parameter: List<CapParameter> = emptyList(),
)

@Serializable
data class CapArea(val areaDesc: String = "", val geocode: List<CapParameter> = emptyList())

/** CAP's generic name/value pair, used for both warning parameters and area geocodes. */
@Serializable
data class CapParameter(val valueName: String = "", val value: String = "")

data class WeatherWarning(
    /** Stable identity of this warning for this region, so it is only announced once. */
    val key: String,
    /** MeteoAlarm awareness level: 2 yellow, 3 orange, 4 red. */
    val level: Int,
    val event: String,
    val description: String,
    val area: String,
    val onset: OffsetDateTime?,
    val expires: OffsetDateTime?,
)

/** Matches a position to the MeteoAlarm regions of Belgium: the province, plus the coastal zone near the sea. */
object BelgiumRegions {
    private class Province(val code: String, val lat: Double, val lon: Double)

    // Rough centres; the nearest one wins. MeteoAlarm groups Brussels and both Brabants as "BE004".
    private val provinces = listOf(
        Province("BE21", 51.20, 4.70),  // Antwerp
        Province("BE22", 50.95, 5.45),  // Limburg
        Province("BE23", 51.00, 3.85),  // East Flanders
        Province("BE25", 51.00, 3.00),  // West Flanders
        Province("BE004", 50.80, 4.50), // Brussels and Brabant
        Province("BE32", 50.45, 3.95),  // Hainaut
        Province("BE33", 50.55, 5.75),  // Liege
        Province("BE34", 49.95, 5.45),  // Luxembourg
        Province("BE35", 50.30, 4.85),  // Namur
    )

    // The coast runs from De Panne to Knokke; "BE801" is the coastal zone around it.
    private const val COAST_ZONE_KM = 12.0
    private val coastStart = 51.09 to 2.55
    private val coastEnd = 51.37 to 3.37

    fun contains(lat: Double, lon: Double) = lat in 49.4..51.6 && lon in 2.5..6.5

    fun codesFor(lat: Double, lon: Double): Set<String> {
        if (!contains(lat, lon)) return emptySet()
        val lonScale = cos(Math.toRadians(51.0))
        val nearest = provinces.minBy { hypot(it.lat - lat, (it.lon - lon) * lonScale) }
        val codes = mutableSetOf(nearest.code)
        if (distanceToCoastKm(lat, lon) < COAST_ZONE_KM) codes += "BE801"
        return codes
    }

    private fun distanceToCoastKm(lat: Double, lon: Double): Double {
        // Flat-earth kilometres are plenty accurate over a country this small.
        val kmPerLon = 111.32 * cos(Math.toRadians(51.2))
        fun x(longitude: Double) = longitude * kmPerLon
        fun y(latitude: Double) = latitude * 110.57
        val (ay, ax) = y(coastStart.first) to x(coastStart.second)
        val (by, bx) = y(coastEnd.first) to x(coastEnd.second)
        val (py, px) = y(lat) to x(lon)
        val length2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
        val t = (((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / length2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * (bx - ax)), py - (ay + t * (by - ay)))
    }
}

class MeteoAlarmRepository(
    private val api: MeteoAlarmApi = Retrofit.Builder()
        .baseUrl(MeteoAlarmApi.BASE_URL)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MeteoAlarmApi::class.java),
) {
    /** Warnings that are active now (or start within [lookAheadHours]) for this position, at or above [minLevel]. */
    suspend fun warningsFor(
        latitude: Double,
        longitude: Double,
        minLevel: Int,
        now: OffsetDateTime = OffsetDateTime.now(),
        lookAheadHours: Long = 12,
    ): Result<List<WeatherWarning>> = runCatching {
        val codes = BelgiumRegions.codesFor(latitude, longitude)
        if (codes.isEmpty()) return@runCatching emptyList()
        api.belgium().warnings.flatMap { warningsIn(it.alert, codes, minLevel, now, lookAheadHours) }
    }

    private fun warningsIn(
        alert: CapAlert,
        codes: Set<String>,
        minLevel: Int,
        now: OffsetDateTime,
        lookAheadHours: Long,
    ): List<WeatherWarning> {
        val language = Locale.getDefault().language
        return codes.mapNotNull { code ->
            // The same warning is published once per language: keep the reader's language, else English.
            val versions = alert.info.filter { info -> info.area.any { area -> area.geocode.any { it.value == code } } }
            val info = versions.firstOrNull { it.language?.startsWith(language) == true }
                ?: versions.firstOrNull { it.language?.startsWith("en") == true }
                ?: versions.firstOrNull()
                ?: return@mapNotNull null

            val level = info.parameter.firstOrNull { it.valueName == "awareness_level" }
                ?.value?.substringBefore(';')?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val onset = info.onset?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            val expires = info.expires?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            val ended = expires != null && expires.isBefore(now)
            val farOff = onset != null && onset.isAfter(now.plusHours(lookAheadHours))
            if (level < minLevel || ended || farOff) return@mapNotNull null

            WeatherWarning(
                key = "${alert.identifier}|$code",
                level = level,
                event = info.event ?: info.headline ?: "Weather warning",
                description = info.description.orEmpty(),
                area = info.area.firstOrNull { area -> area.geocode.any { it.value == code } }?.areaDesc.orEmpty(),
                onset = onset,
                expires = expires,
            )
        }
    }
}

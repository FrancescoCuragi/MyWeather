package com.francesco.myweather.data

import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Serializable
data class CachedForecast(
    val fetchedAt: Long,
    val forecast: ForecastResponse,
    val airQuality: AirQuality? = null,
)

/** Fetches forecasts from Open-Meteo and keeps the latest one per location in a JSON file. */
class WeatherRepository(
    private val cacheDir: File,
    private val api: OpenMeteoApi = createRetrofit(OpenMeteoApi.BASE_URL).create(OpenMeteoApi::class.java),
    private val airApi: AirQualityApi = createRetrofit(AirQualityApi.BASE_URL).create(AirQualityApi::class.java),
    private val geocodingApi: GeocodingApi = createRetrofit(GeocodingApi.BASE_URL).create(GeocodingApi::class.java),
    private val historyApi: HistoryApi = createRetrofit(HistoryApi.BASE_URL).create(HistoryApi::class.java),
) {
    suspend fun searchPlaces(query: String): Result<List<GeoResult>> =
        runCatching { geocodingApi.search(query).results }

    suspend fun loadCached(latitude: Double, longitude: Double): CachedForecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                json.decodeFromString<CachedForecast>(cacheFile(latitude, longitude).readText())
            }.getOrNull()
        }

    suspend fun fetch(latitude: Double, longitude: Double): Result<CachedForecast> =
        runCatching {
            // Air quality is a bonus: a failure there must not fail the whole refresh.
            val air = runCatching { airApi.airQuality(latitude, longitude).current }.getOrNull()
            val fresh = CachedForecast(System.currentTimeMillis(), api.forecast(latitude, longitude), air)
            withContext(Dispatchers.IO) {
                cacheFile(latitude, longitude).writeText(json.encodeToString(fresh))
            }
            fresh
        }

    /** The next days from every model in [FORECAST_MODELS], for comparing them side by side. */
    suspend fun modelComparison(latitude: Double, longitude: Double): Result<ModelComparison> =
        runCatching {
            val hourly = api.modelForecast(latitude, longitude, FORECAST_MODELS.joinToString(",") { it.id }).hourly
            val times = hourly.getValue("time").jsonArray.map { (it as JsonPrimitive).content }
            val variables = OpenMeteoApi.MODEL_HOURLY_FIELDS.split(",")
            ModelComparison(
                times = times,
                values = variables.associateWith { variable ->
                    FORECAST_MODELS.mapNotNull { model ->
                        val array = hourly["${variable}_${model.id}"] as? JsonArray ?: return@mapNotNull null
                        model.id to array.map { (it as? JsonPrimitive)?.doubleOrNull }
                    }.toMap()
                },
            )
        }

    /**
     * This calendar day last year, and the average high and low for the same date over the last [years] years.
     * The archive lags real time by a few days, so a date in the last few days has no data yet; that comes back
     * as a null day rather than an error.
     */
    suspend fun historyFor(latitude: Double, longitude: Double, date: java.time.LocalDate, years: Int = 5): Result<HistoryComparison> =
        runCatching {
            val days = (1..years).mapNotNull { back ->
                val day = runCatching { date.minusYears(back.toLong()) }.getOrNull() ?: return@mapNotNull null  // 29 Feb
                val daily = historyApi.day(latitude, longitude, day.toString(), day.toString()).daily
                val high = daily.temperatureMax.firstOrNull() ?: return@mapNotNull null
                val low = daily.temperatureMin.firstOrNull() ?: return@mapNotNull null
                HistoricalDay(
                    date = day.toString(), high = high, low = low,
                    rainMm = daily.precipitation.firstOrNull() ?: 0.0,
                    weatherCode = daily.weatherCode.firstOrNull() ?: 0,
                    windMaxKmh = daily.windMax.firstOrNull() ?: 0.0,
                )
            }
            HistoryComparison(
                lastYear = days.firstOrNull { it.date == date.minusYears(1).toString() },
                averageHigh = days.takeIf { it.isNotEmpty() }?.map { it.high }?.average(),
                averageLow = days.takeIf { it.isNotEmpty() }?.map { it.low }?.average(),
                years = days.size,
            )
        }

    /** A forecast for any point on the map. Not saved: it is only shown, and kept if the user saves the place. */
    suspend fun pointForecast(latitude: Double, longitude: Double): Result<ForecastResponse> =
        runCatching { api.forecast(latitude, longitude) }

    /** Whether rain is falling or about to, from the 15-minute forecast. */
    suspend fun rainOutlook(latitude: Double, longitude: Double): Result<RainOutlook> =
        runCatching {
            val response = api.minutelyRain(latitude, longitude)
            val now = LocalDateTime.ofEpochSecond(Instant.now().epochSecond + response.utcOffsetSeconds, 0, ZoneOffset.UTC)
            val slots = response.minutely.time.map(LocalDateTime::parse)
            val rainy = { i: Int -> (response.minutely.precipitation[i] ?: 0.0) >= RAIN_PER_SLOT_MM }
            // The current slot is the one that has not finished yet.
            val current = slots.indexOfFirst { it.plusMinutes(15).isAfter(now) }.coerceAtLeast(0)
            val next = (current + 1 until slots.size).firstOrNull(rainy)
            RainOutlook(
                raining = rainy(current),
                minutesToRain = next?.let { Duration.between(now, slots[it]).toMinutes().toInt().coerceAtLeast(0) },
            )
        }

    private fun cacheFile(latitude: Double, longitude: Double) =
        File(cacheDir, "forecast_%.2f_%.2f.json".format(Locale.ROOT, latitude, longitude))

    private companion object {
        /** About 0.4 mm/h: the faintest rain worth an alert. */
        const val RAIN_PER_SLOT_MM = 0.1

        val json = Json { ignoreUnknownKeys = true }

        fun createRetrofit(baseUrl: String): Retrofit =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
    }
}

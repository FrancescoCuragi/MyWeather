package com.francesco.myweather.data

import java.io.File
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

    private fun cacheFile(latitude: Double, longitude: Double) =
        File(cacheDir, "forecast_%.2f_%.2f.json".format(Locale.ROOT, latitude, longitude))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun createRetrofit(baseUrl: String): Retrofit =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
    }
}

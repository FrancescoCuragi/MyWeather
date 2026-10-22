package com.francesco.myweather.data

import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Serializable
data class CachedForecast(
    val fetchedAt: Long,
    val forecast: ForecastResponse,
)

/** Fetches forecasts from Open-Meteo and keeps the latest one per location in a JSON file. */
class WeatherRepository(
    private val cacheDir: File,
    private val api: OpenMeteoApi = createApi(),
) {
    suspend fun loadCached(latitude: Double, longitude: Double): CachedForecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                json.decodeFromString<CachedForecast>(cacheFile(latitude, longitude).readText())
            }.getOrNull()
        }

    suspend fun fetch(latitude: Double, longitude: Double): Result<CachedForecast> =
        runCatching {
            val fresh = CachedForecast(System.currentTimeMillis(), api.forecast(latitude, longitude))
            withContext(Dispatchers.IO) {
                cacheFile(latitude, longitude).writeText(json.encodeToString(fresh))
            }
            fresh
        }

    private fun cacheFile(latitude: Double, longitude: Double) =
        File(cacheDir, "forecast_%.2f_%.2f.json".format(Locale.ROOT, latitude, longitude))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun createApi(): OpenMeteoApi =
            Retrofit.Builder()
                .baseUrl(OpenMeteoApi.BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenMeteoApi::class.java)
    }
}

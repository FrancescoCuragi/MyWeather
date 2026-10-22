package com.francesco.myweather.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class WeatherRepository(
    private val api: OpenMeteoApi = createApi(),
) {
    suspend fun forecast(latitude: Double, longitude: Double): Result<ForecastResponse> =
        runCatching { api.forecast(latitude, longitude) }

    private companion object {
        fun createApi(): OpenMeteoApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(OpenMeteoApi.BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenMeteoApi::class.java)
        }
    }
}

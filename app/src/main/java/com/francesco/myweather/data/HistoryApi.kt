package com.francesco.myweather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo's archive of past weather (reanalysis), used for "this day last year". */
interface HistoryApi {
    @GET("v1/archive")
    suspend fun day(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max",
        @Query("timezone") timezone: String = "auto",
    ): HistoryResponse

    companion object {
        const val BASE_URL = "https://archive-api.open-meteo.com/"
    }
}

@Serializable
data class HistoryResponse(val daily: HistoryDaily)

@Serializable
data class HistoryDaily(
    val time: List<String>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>,
    @SerialName("precipitation_sum") val precipitation: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("wind_speed_10m_max") val windMax: List<Double?>,
)

/** One past day, and the same calendar day in each of the past few years for an average. */
data class HistoricalDay(
    val date: String,
    val high: Double,
    val low: Double,
    val rainMm: Double,
    val weatherCode: Int,
    val windMaxKmh: Double,
)

data class HistoryComparison(val lastYear: HistoricalDay?, val averageHigh: Double?, val averageLow: Double?, val years: Int)

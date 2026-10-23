package com.francesco.myweather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("forecast_days") forecastDays: Int = 14,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponse

    /** Rain in 15-minute steps for the next few hours, used for the "rain starting soon" alert. */
    @GET("v1/forecast")
    suspend fun minutelyRain(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("minutely_15") minutely: String = "precipitation",
        @Query("forecast_minutely_15") steps: Int = 12,
        @Query("timezone") timezone: String = "auto",
    ): MinutelyResponse

    /** The same forecast from several weather models at once; keys come back as "<variable>_<model>". */
    @GET("v1/forecast")
    suspend fun modelForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = MODEL_HOURLY_FIELDS,
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto",
    ): ModelsResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val MODEL_HOURLY_FIELDS = "temperature_2m,precipitation,wind_speed_10m,pressure_msl"
        const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day," +
                "uv_index,pressure_msl,visibility,dew_point_2m"
        const val HOURLY_FIELDS =
            "temperature_2m,precipitation_probability,weather_code,is_day,precipitation,wind_speed_10m,wind_gusts_10m,pressure_msl"
        const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
    }
}

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: Current,
    val hourly: Hourly,
    val daily: Daily,
)

@Serializable
data class Current(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val feelsLike: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("is_day") val isDay: Int,
    // Added after the first cached forecasts were saved, so they must tolerate being absent.
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("pressure_msl") val pressure: Double? = null,
    val visibility: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
)

@Serializable
data class Hourly(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("is_day") val isDay: List<Int>,
    // Added for the meteogram after forecasts were already cached, so they must tolerate being absent.
    val precipitation: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressure: List<Double?> = emptyList(),
)

@Serializable
data class Daily(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>,
    val sunrise: List<String>,
    val sunset: List<String>,
)

interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5,ozone",
    ): AirQualityResponse

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    }
}

@Serializable
data class AirQualityResponse(val current: AirQuality)

@Serializable
data class AirQuality(
    @SerialName("us_aqi") val aqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val ozone: Double? = null,
)

@Serializable
data class ModelsResponse(val hourly: JsonObject)

/** One weather model Open-Meteo can serve: its API id and the name we show. */
data class ForecastModel(val id: String, val label: String)

val FORECAST_MODELS = listOf(
    ForecastModel("ecmwf_ifs025", "ECMWF"),
    ForecastModel("icon_seamless", "ICON"),
    ForecastModel("gfs_seamless", "GFS"),
    ForecastModel("meteofrance_seamless", "Météo-France"),
    ForecastModel("ukmo_seamless", "UK Met Office"),
)

/** Hourly values per variable and per model, all aligned to [times]; a model's gaps (shorter range) are null. */
data class ModelComparison(
    val times: List<String>,
    val values: Map<String, Map<String, List<Double?>>>,  // variable -> model id -> hourly values
)

@Serializable
data class MinutelyResponse(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int,
    @SerialName("minutely_15") val minutely: Minutely15,
)

@Serializable
data class Minutely15(val time: List<String>, val precipitation: List<Double?>)

/** What the next two hours look like for rain. */
data class RainOutlook(
    /** True if rain is already falling in the current 15-minute slot. */
    val raining: Boolean,
    /** Minutes until the first rainy slot after now, or null if the next two hours stay dry. */
    val minutesToRain: Int?,
)

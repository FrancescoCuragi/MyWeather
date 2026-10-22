package com.francesco.myweather.ui

import androidx.compose.runtime.compositionLocalOf
import com.francesco.myweather.data.TemperatureUnit
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WindUnit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The user's unit preferences, available to every screen without threading them through parameters. */
val LocalSettings = compositionLocalOf { UserSettings() }

/** Open-Meteo returns °C and km/h; these convert to whatever the user picked. */
fun UserSettings.temperature(celsius: Double): String {
    val value = when (temperatureUnit) {
        TemperatureUnit.Celsius -> celsius
        TemperatureUnit.Fahrenheit -> celsius * 9 / 5 + 32
    }
    return "${value.roundToInt()}°"
}

fun UserSettings.wind(kmh: Double): String = when (windUnit) {
    WindUnit.KmH -> "${kmh.roundToInt()} km/h"
    WindUnit.Ms -> "%.1f m/s".format(Locale.ROOT, kmh / 3.6)
    WindUnit.Knots -> "${(kmh / 1.852).roundToInt()} kt"
    WindUnit.Beaufort -> "${beaufort(kmh)} Bft"
}

/** Beaufort force from a speed in km/h (upper bounds of forces 0 to 11; anything above is 12). */
private fun beaufort(kmh: Double): Int {
    val upperBounds = listOf(1.0, 5.0, 11.0, 19.0, 28.0, 38.0, 49.0, 61.0, 74.0, 88.0, 102.0, 117.0)
    return upperBounds.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
}

fun UserSettings.clock(time: LocalDateTime): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))

/** Hour label for the hourly strip: "14:00" or "2 PM". */
fun UserSettings.hourLabel(time: LocalDateTime): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:00" else "h a", Locale.getDefault()))

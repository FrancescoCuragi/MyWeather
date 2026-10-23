package com.francesco.myweather.ui

import androidx.compose.runtime.compositionLocalOf
import com.francesco.myweather.data.LengthUnit
import com.francesco.myweather.data.PrecipitationUnit
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

/** Numeric versions for charts, which draw numbers instead of text; labels name the unit. */
fun UserSettings.temperatureValue(celsius: Double): Double = when (temperatureUnit) {
    TemperatureUnit.Celsius -> celsius
    TemperatureUnit.Fahrenheit -> celsius * 9 / 5 + 32
}

fun UserSettings.windValue(kmh: Double): Double = when (windUnit) {
    WindUnit.KmH -> kmh
    WindUnit.Ms -> kmh / 3.6
    WindUnit.Knots -> kmh / 1.852
    WindUnit.Beaufort -> beaufort(kmh).toDouble()
}

val UserSettings.temperatureLabel: String get() = temperatureUnit.label
val UserSettings.windLabel: String get() = windUnit.label

private const val MM_PER_INCH = 25.4
private const val METRES_PER_FOOT = 0.3048
private const val METRES_PER_MILE = 1609.344

/** Rain and snow arrive in millimetres (of water). */
fun UserSettings.precipitationValue(mm: Double): Double = when (precipitationUnit) {
    PrecipitationUnit.Millimetres -> mm
    PrecipitationUnit.Inches -> mm / MM_PER_INCH
}

/** A rain amount with its unit, with enough decimals for the unit: "2.4 mm" or "0.09 in". */
fun UserSettings.precipitation(mm: Double): String {
    val value = precipitationValue(mm)
    return when (precipitationUnit) {
        PrecipitationUnit.Millimetres -> "%.1f mm".format(Locale.ROOT, value)
        PrecipitationUnit.Inches -> "%.2f in".format(Locale.ROOT, value)
    }
}

val UserSettings.precipitationLabel: String get() = precipitationUnit.label

/** Visibility, which arrives in metres: kilometres or miles. */
fun UserSettings.distance(metres: Double): String = when (lengthUnit) {
    LengthUnit.Metric -> "%.1f km".format(Locale.ROOT, metres / 1000)
    LengthUnit.Imperial -> "%.1f mi".format(Locale.ROOT, metres / METRES_PER_MILE)
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

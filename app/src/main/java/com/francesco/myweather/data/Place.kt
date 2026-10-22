package com.francesco.myweather.data

import java.util.Locale

/** A location the app can show a forecast for: the GPS position or a saved city. */
data class Place(
    val name: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
) {
    /** Stable identity; also what the forecast cache is keyed on. */
    val key: String get() = "%.2f,%.2f".format(Locale.ROOT, latitude, longitude)
}

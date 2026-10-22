package com.francesco.myweather.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Resolves the device position to a [Place]. The caller must already hold a location permission. */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): Place? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = suspendCancellableCoroutine<Location?> { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return null
        val (name, subtitle) = reverseGeocode(location)
        return Place(name, subtitle, location.latitude, location.longitude, isCurrentLocation = true)
    }

    /** Best-effort city name for a position; falls back to a generic label offline or without a geocoder. */
    private suspend fun reverseGeocode(location: Location): Pair<String, String?> {
        val address = runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { cont.resume(it.firstOrNull()) }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                }
            }
        }.getOrNull()
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "My location"
        return name to address?.countryName
    }
}

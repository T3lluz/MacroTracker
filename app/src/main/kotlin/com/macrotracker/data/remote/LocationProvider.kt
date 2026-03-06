package com.macrotracker.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LatLon(val latitude: Double, val longitude: Double)

@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LocationProvider"
    }

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Returns the device's current location, or null if unavailable.
     * The caller MUST have already acquired location permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocation(): LatLon? = suspendCancellableCoroutine { cont ->
        // Try lastLocation first — fast & battery-friendly
        fusedClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "lastLocation: ${location.latitude}, ${location.longitude}")
                    cont.resume(LatLon(location.latitude, location.longitude))
                } else {
                    // No cached location — request a fresh one
                    Log.d(TAG, "lastLocation was null, requesting fresh location")
                    requestFreshLocation(cont)
                }
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "lastLocation failed: ${e.message}")
                requestFreshLocation(cont)
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(
        cont: kotlinx.coroutines.CancellableContinuation<LatLon?>,
    ) {
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "freshLocation: ${location.latitude}, ${location.longitude}")
                    cont.resume(LatLon(location.latitude, location.longitude))
                } else {
                    Log.w(TAG, "Fresh location was also null")
                    cont.resume(null)
                }
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Fresh location failed: ${e.message}")
                cont.resume(null)
            }

        cont.invokeOnCancellation { cts.cancel() }
    }

    @Suppress("DEPRECATION")
    suspend fun getLocationName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Unknown"
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            "Unknown location"
        }
    }
}



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
        private const val LOCATION_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Short-lived cache so rapid successive calls (e.g. retry after network error)
    // reuse the same fix instead of each requesting a new GPS signal.
    private var cachedLocation: LatLon? = null
    private var locationCacheTimestamp: Long = 0L

    /** Clears the in-memory location cache so the next [getLocation] requests a fresh fix. */
    fun clearCache() {
        cachedLocation = null
        locationCacheTimestamp = 0L
    }

    /**
     * Returns the device's precise current location, or null if unavailable.
     * Results are cached for [LOCATION_CACHE_TTL_MS] to avoid redundant GPS fixes.
     * Pass [forceRefresh] to bypass the cache (e.g. user-triggered widget refresh).
     * Falls back to BALANCED_POWER_ACCURACY if GPS cannot produce a fix (e.g. indoors).
     * The caller MUST have already acquired ACCESS_FINE_LOCATION permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocation(forceRefresh: Boolean = false): LatLon? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedLocation != null && now - locationCacheTimestamp < LOCATION_CACHE_TTL_MS) {
            Log.d(TAG, "Returning cached location")
            return cachedLocation
        }

        // Try a fresh HIGH_ACCURACY (GPS) fix first
        val precise = requestLocation(Priority.PRIORITY_HIGH_ACCURACY)
        if (precise != null) {
            Log.d(TAG, "GPS fix (accuracy=${precise.accuracy}m): ${precise.latitude}, ${precise.longitude}")
            val result = LatLon(precise.latitude, precise.longitude)
            cachedLocation = result
            locationCacheTimestamp = now
            return result
        }

        // GPS unavailable (indoors / no signal) — fall back to network-based fix
        Log.d(TAG, "GPS fix unavailable, falling back to BALANCED_POWER_ACCURACY")
        val fallback = requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        return if (fallback != null) {
            Log.d(TAG, "Network fix (accuracy=${fallback.accuracy}m): ${fallback.latitude}, ${fallback.longitude}")
            val result = LatLon(fallback.latitude, fallback.longitude)
            cachedLocation = result
            locationCacheTimestamp = now
            result
        } else {
            Log.w(TAG, "Both GPS and network location unavailable")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { location: Location? ->
                    cont.resume(location)
                }
                .addOnFailureListener { e: Exception ->
                    Log.e(TAG, "getCurrentLocation($priority) failed: ${e.message}")
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
                // Build a precise, human-readable name: street + neighbourhood/city
                val street = addr.thoroughfare ?: addr.subThoroughfare
                val neighbourhood = addr.subLocality
                val city = addr.locality ?: addr.subAdminArea
                val region = addr.adminArea
                val country = addr.countryCode

                when {
                    street != null && neighbourhood != null -> "$street, $neighbourhood"
                    street != null && city != null -> "$street, $city"
                    neighbourhood != null && city != null -> "$neighbourhood, $city"
                    city != null && region != null && city != region -> "$city, $region"
                    city != null && country != null -> "$city, $country"
                    city != null -> city
                    region != null -> region
                    else -> "Unknown location"
                }
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            "Unknown location"
        }
    }
}


package com.macrotracker.data.health

import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** GPS sample along an exercise route. */
data class ActivityRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val time: Instant? = null,
)

/** Downsampled heart-rate sample for sparkline charts. */
data class ActivityHrPoint(
    val time: Instant,
    val bpm: Long,
)

/**
 * One workout/session synced through Health Connect (Garmin, Google Fit,
 * Samsung Health, Strava, Polar, and others).
 */
data class HealthActivity(
    val id: String,
    val title: String,
    val exerciseType: Int,
    val startTime: Instant,
    val endTime: Instant,
    val sourcePackage: String,
    val sourceLabel: String,
    val deviceLabel: String?,
    val distanceKm: Double?,
    val caloriesKcal: Double?,
    val steps: Long?,
    val avgHr: Long?,
    val maxHr: Long?,
    val minHr: Long?,
    val elevationGainM: Double?,
    val route: List<ActivityRoutePoint>,
    val routeConsentRequired: Boolean,
    val laps: List<ActivityLap> = emptyList(),
    val hrSamples: List<ActivityHrPoint> = emptyList(),
) {
    val duration: Duration get() = Duration.between(startTime, endTime).coerceAtLeast(Duration.ZERO)

    val typeLabel: String get() = exerciseTypeLabel(exerciseType)

    val isOutdoorType: Boolean get() = isOutdoorExercise(exerciseType)

    val hasRoute: Boolean get() = route.size >= 2

    val avgPaceMinPerKm: Double?
        get() {
            val km = distanceKm ?: return null
            if (km < 0.05) return null
            val minutes = duration.seconds / 60.0
            if (minutes <= 0) return null
            return minutes / km
        }

    val avgSpeedKmh: Double?
        get() {
            val km = distanceKm ?: return null
            val hours = duration.seconds / 3600.0
            if (hours <= 0 || km <= 0) return null
            return km / hours
        }
}

data class ActivityLap(
    val index: Int,
    val duration: Duration,
    val distanceKm: Double?,
)

data class RouteBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

/** GPS points plus whether Health Connect still needs a one-time route OK. */
data class ActivityRouteResolution(
    val points: List<ActivityRoutePoint>,
    val consentRequired: Boolean,
)

/**
 * Web Mercator viewport in fractional OSM tile coordinates (Y grows south).
 * [west]/[east]/[north]/[south] are padded and aspect-fitted to the view.
 */
data class RouteMapViewport(
    val zoom: Int,
    val west: Double,
    val north: Double,
    val east: Double,
    val south: Double,
) {
    val spanX: Double get() = (east - west).coerceAtLeast(1e-9)
    val spanY: Double get() = (south - north).coerceAtLeast(1e-9)
    val tileX0: Int get() = floor(west).toInt()
    val tileY0: Int get() = floor(north).toInt()
    val tileX1: Int get() = (ceil(east) - 1.0).toInt().coerceAtLeast(tileX0)
    val tileY1: Int get() = (ceil(south) - 1.0).toInt().coerceAtLeast(tileY0)

    fun fractionX(lng: Double): Float =
        ((lngToTileX(lng, zoom) - west) / spanX).toFloat()

    fun fractionY(lat: Double): Float =
        ((latToTileY(lat, zoom) - north) / spanY).toFloat()
}

fun exerciseTypeLabel(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> "Run"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> "Ride"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    -> "Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    -> "Strength"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> "Row"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
    -> "Stairs"
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Climb"
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Ski"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snowboard"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "Snowshoe"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
    -> "Skate"
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> "Surf"
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "Paddle"
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "Sail"
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golf"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "Table tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basketball"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Soccer"
    ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "Volleyball"
    ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "Baseball"
    ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "Softball"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Football"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "AFL"
    ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "Rugby"
    ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "Cricket"
    ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "Handball"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
    ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY,
    -> "Hockey"
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial arts"
    ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxing"
    ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> "Fencing"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "Class"
    ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> "Boot camp"
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
    ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "Gymnastics"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretch"
    ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "Breathwork"
    ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "Disc"
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> "Paraglide"
    ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
    ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
    -> "Racquet"
    ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "Water polo"
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> "Wheelchair"
    ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> "Scuba"
    else -> "Workout"
}

fun isOutdoorExercise(type: Int): Boolean = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING,
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
    -> true
    else -> false
}

fun activitySourceLabel(packageName: String, manufacturer: String?): String {
    val fromPkg = when {
        packageName.contains("garmin", ignoreCase = true) -> "Garmin"
        packageName.contains("strava", ignoreCase = true) -> "Strava"
        packageName.contains("polar", ignoreCase = true) -> "Polar"
        packageName.contains("coros", ignoreCase = true) -> "COROS"
        packageName.contains("suunto", ignoreCase = true) -> "Suunto"
        packageName.contains("fitbit", ignoreCase = true) -> "Fitbit"
        packageName.contains("peloton", ignoreCase = true) -> "Peloton"
        packageName.contains("whoop", ignoreCase = true) -> "WHOOP"
        packageName.contains("ouraring", ignoreCase = true) -> "Oura"
        packageName.contains("shealth", ignoreCase = true) ||
            packageName.contains("sec.android.app.shealth", ignoreCase = true) -> "Samsung Health"
        packageName.contains("google.android.apps.fitness", ignoreCase = true) -> "Google Fit"
        packageName.contains("huawei.health", ignoreCase = true) -> "Huawei Health"
        packageName.contains("xiaomi", ignoreCase = true) ||
            packageName.contains("mihealth", ignoreCase = true) -> "Mi Fitness"
        packageName.contains("macrotracker", ignoreCase = true) -> "DailyDash"
        else -> null
    }
    if (fromPkg != null) return fromPkg
    val mfr = manufacturer?.trim().orEmpty()
    if (mfr.isNotEmpty() && !mfr.equals("unknown", ignoreCase = true)) {
        return mfr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
    return "Health Connect"
}

fun defaultActivityTitle(type: Int, start: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val hour = start.atZone(zone).hour
    val period = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    return "$period ${exerciseTypeLabel(type).lowercase(Locale.US)}"
}

fun formatActivityDuration(duration: Duration): String {
    val totalSec = duration.seconds.coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatActivityDistance(distanceKm: Double?): String? {
    if (distanceKm == null || distanceKm <= 0) return null
    return if (distanceKm < 1.0) {
        "${(distanceKm * 1000).roundToInt()} m"
    } else {
        String.format(Locale.US, "%.2f km", distanceKm)
    }
}

fun formatPace(minPerKm: Double?): String? {
    if (minPerKm == null || minPerKm <= 0 || !minPerKm.isFinite()) return null
    val totalSeconds = (minPerKm * 60).roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
}

fun formatElevation(meters: Double?): String? {
    if (meters == null || meters < 1.0) return null
    return "${meters.roundToInt()} m"
}

private val ACTIVITY_WHEN_TODAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val ACTIVITY_WHEN_WEEK = DateTimeFormatter.ofPattern("EEE · h:mm a", Locale.US)
private val ACTIVITY_WHEN_FULL = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

fun formatActivityWhen(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = start.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val date = local.toLocalDate()
    return when {
        date == today -> "Today · ${local.format(ACTIVITY_WHEN_TODAY)}"
        date == today.minusDays(1) -> "Yesterday · ${local.format(ACTIVITY_WHEN_TODAY)}"
        date.isAfter(today.minusDays(6)) -> local.format(ACTIVITY_WHEN_WEEK)
        else -> local.format(ACTIVITY_WHEN_FULL)
    }
}

fun downsampleRoute(points: List<ActivityRoutePoint>, maxPoints: Int = 180): List<ActivityRoutePoint> {
    if (points.size <= maxPoints || maxPoints < 3) return points
    val step = (points.size - 1).toDouble() / (maxPoints - 1)
    val out = ArrayList<ActivityRoutePoint>(maxPoints)
    var i = 0
    while (i < maxPoints) {
        val idx = min(points.lastIndex, (i * step).roundToInt())
        out.add(points[idx])
        i++
    }
    if (out.last() != points.last()) {
        out[out.lastIndex] = points.last()
    }
    return out
}

fun downsampleHr(samples: List<ActivityHrPoint>, maxPoints: Int = 48): List<ActivityHrPoint> {
    if (samples.size <= maxPoints || maxPoints < 2) return samples
    val step = (samples.size - 1).toDouble() / (maxPoints - 1)
    return List(maxPoints) { i ->
        samples[min(samples.lastIndex, (i * step).roundToInt())]
    }
}

fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun routeDistanceKm(points: List<ActivityRoutePoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        total += haversineKm(a.latitude, a.longitude, b.latitude, b.longitude)
    }
    return total
}

/**
 * Cumulative ascent in metres. Ignores tiny GPS jitter under [minStepM].
 */
fun routeElevationGainM(points: List<ActivityRoutePoint>, minStepM: Double = 1.0): Double {
    var gain = 0.0
    var prev: Double? = null
    for (p in points) {
        val alt = p.altitudeMeters ?: continue
        val last = prev
        if (last != null) {
            val delta = alt - last
            if (delta >= minStepM) gain += delta
        }
        prev = alt
    }
    return gain
}

fun routeBounds(points: List<ActivityRoutePoint>): RouteBounds? {
    if (points.isEmpty()) return null
    var minLat = points[0].latitude
    var maxLat = points[0].latitude
    var minLng = points[0].longitude
    var maxLng = points[0].longitude
    for (p in points) {
        minLat = min(minLat, p.latitude)
        maxLat = max(maxLat, p.latitude)
        minLng = min(minLng, p.longitude)
        maxLng = max(maxLng, p.longitude)
    }
    return RouteBounds(minLat, maxLat, minLng, maxLng)
}

/** Web Mercator Y so north-south distances stay geographically honest. */
fun mercatorY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val rad = Math.toRadians(clamped)
    return ln(tan(Math.PI / 4.0 + rad / 2.0))
}

fun pickFeaturedActivity(activities: List<HealthActivity>): HealthActivity? {
    if (activities.isEmpty()) return null
    return activities.firstOrNull { it.hasRoute }
        ?: activities.firstOrNull { it.routeConsentRequired }
        ?: activities.firstOrNull { it.isOutdoorType }
        ?: activities.first()
}

fun pointsFromExerciseRoute(route: ExerciseRoute?): List<ActivityRoutePoint> {
    val raw = route?.route.orEmpty()
    if (raw.isEmpty()) return emptyList()
    return downsampleRoute(
        raw.map { loc ->
            ActivityRoutePoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitudeMeters = loc.altitude?.inMeters,
                time = loc.time,
            )
        },
    )
}

fun resolveActivityRoute(result: ExerciseRouteResult): ActivityRouteResolution = when (result) {
    is ExerciseRouteResult.Data -> ActivityRouteResolution(
        points = pointsFromExerciseRoute(result.exerciseRoute),
        consentRequired = false,
    )
    is ExerciseRouteResult.ConsentRequired -> ActivityRouteResolution(
        points = emptyList(),
        consentRequired = true,
    )
    else -> ActivityRouteResolution(points = emptyList(), consentRequired = false)
}

fun activityNeedsRouteConsent(
    points: List<ActivityRoutePoint>,
    hcConsentRequired: Boolean,
    exerciseType: Int,
): Boolean {
    if (points.size >= 2) return false
    return hcConsentRequired || isOutdoorExercise(exerciseType)
}

fun lngToTileX(lng: Double, zoom: Int): Double {
    val n = 1 shl zoom
    return (lng + 180.0) / 360.0 * n
}

fun latToTileY(lat: Double, zoom: Int): Double {
    val n = 1 shl zoom
    return (1.0 - mercatorY(lat) / Math.PI) / 2.0 * n
}

/**
 * Padded, aspect-fitted tile viewport so the GPS path sits on real map tiles
 * without stretching north-south vs east-west.
 */
fun buildRouteMapViewport(
    points: List<ActivityRoutePoint>,
    viewAspect: Double = 2.0,
    maxZoom: Int = 16,
    minZoom: Int = 3,
    maxTilesX: Int = 5,
    maxTilesY: Int = 4,
): RouteMapViewport? {
    if (points.size < 2) return null
    val bounds = routeBounds(points) ?: return null
    var minLat = bounds.minLat
    var maxLat = bounds.maxLat
    var minLng = bounds.minLng
    var maxLng = bounds.maxLng
    val latPad = max((maxLat - minLat) * 0.22, 0.0008)
    val lngPad = max((maxLng - minLng) * 0.22, 0.0008)
    minLat -= latPad
    maxLat += latPad
    minLng -= lngPad
    maxLng += lngPad
    val aspect = viewAspect.coerceIn(0.4, 4.0)

    var zoom = maxZoom
    var viewport: RouteMapViewport? = null
    while (zoom >= minZoom) {
        var west = lngToTileX(minLng, zoom)
        var east = lngToTileX(maxLng, zoom)
        var north = latToTileY(maxLat, zoom)
        var south = latToTileY(minLat, zoom)
        var spanX = (east - west).coerceAtLeast(0.4)
        var spanY = (south - north).coerceAtLeast(0.4)
        val geoAspect = spanX / spanY
        if (geoAspect > aspect) {
            val extra = spanX / aspect - spanY
            north -= extra / 2
            south += extra / 2
        } else {
            val extra = spanY * aspect - spanX
            west -= extra / 2
            east += extra / 2
        }
        val tilesX = ceil(east).toInt() - floor(west).toInt()
        val tilesY = ceil(south).toInt() - floor(north).toInt()
        viewport = RouteMapViewport(zoom, west, north, east, south)
        if (tilesX <= maxTilesX && tilesY <= maxTilesY) break
        zoom--
    }
    return viewport
}

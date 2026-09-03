package com.macrotracker.data.health

import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.macrotracker.data.local.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthActivityTest {

    private val noon = LocalDate.of(2026, 8, 27).atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun walkingLabelAndOutdoor() {
        assertEquals("Walk", exerciseTypeLabel(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertTrue(isOutdoorExercise(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertTrue(!isOutdoorExercise(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
        assertEquals("Strength", exerciseTypeLabel(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
    }

    @Test
    fun garminPackageMapsToGarmin() {
        assertEquals(
            "Garmin",
            activitySourceLabel("com.garmin.android.apps.connectmobile", "Garmin"),
        )
        assertEquals("Strava", activitySourceLabel("com.strava", null))
        assertEquals("Google Fit", activitySourceLabel("com.google.android.apps.fitness", null))
        assertEquals("Samsung Health", activitySourceLabel("com.sec.android.app.shealth", null))
        assertEquals("Health Connect", activitySourceLabel("com.unknown.app", null))
        assertEquals("Polar", activitySourceLabel("com.other", "Polar"))
    }

    @Test
    fun formatsDurationDistancePaceElevation() {
        assertEquals("12:05", formatActivityDuration(Duration.ofMinutes(12).plusSeconds(5)))
        assertEquals("1:02:03", formatActivityDuration(Duration.ofHours(1).plusMinutes(2).plusSeconds(3)))
        assertEquals("450 m", formatActivityDistance(0.45))
        assertEquals("5.24 km", formatActivityDistance(5.24))
        assertNull(formatActivityDistance(0.0))
        assertEquals("5:30 /km", formatPace(5.5))
        assertEquals("48 m", formatElevation(48.2))
        assertNull(formatElevation(0.4))
    }

    @Test
    fun defaultTitleUsesTimeOfDay() {
        val zone = ZoneId.of("UTC")
        val morning = LocalDate.of(2026, 8, 27).atTime(7, 30).atZone(zone).toInstant()
        assertEquals(
            "Morning walk",
            defaultActivityTitle(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, morning, zone),
        )
        val evening = LocalDate.of(2026, 8, 27).atTime(18, 10).atZone(zone).toInstant()
        assertEquals(
            "Evening run",
            defaultActivityTitle(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, evening, zone),
        )
    }

    @Test
    fun haversineAndRouteDistanceAroundABlock() {
        // ~111 m north (0.001 deg lat)
        val km = haversineKm(51.5, -0.12, 51.501, -0.12)
        assertTrue(km in 0.10..0.12)

        val points = listOf(
            ActivityRoutePoint(51.5, -0.12),
            ActivityRoutePoint(51.501, -0.12),
            ActivityRoutePoint(51.501, -0.119),
        )
        val dist = routeDistanceKm(points)
        assertTrue(dist > 0.15)
    }

    @Test
    fun elevationGainIgnoresJitter() {
        val points = listOf(
            ActivityRoutePoint(0.0, 0.0, altitudeMeters = 10.0),
            ActivityRoutePoint(0.0, 0.0, altitudeMeters = 10.4),
            ActivityRoutePoint(0.0, 0.0, altitudeMeters = 18.0),
            ActivityRoutePoint(0.0, 0.0, altitudeMeters = 16.0),
            ActivityRoutePoint(0.0, 0.0, altitudeMeters = 25.0),
        )
        val gain = routeElevationGainM(points, minStepM = 1.0)
        assertEquals(16.6, gain, 0.01)
    }

    @Test
    fun downsampleKeepsStartAndEnd() {
        val points = (0 until 500).map { i ->
            ActivityRoutePoint(51.5 + i * 0.0001, -0.12)
        }
        val down = downsampleRoute(points, 50)
        assertEquals(50, down.size)
        assertEquals(points.first(), down.first())
        assertEquals(points.last(), down.last())
    }

    @Test
    fun featuredPrefersRouteThenConsentThenLatest() {
        val indoor = sampleActivity(
            "gym",
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        )
        val outdoor = sampleActivity("walk")
        val consent = sampleActivity("need-map", routeConsentRequired = true)
        val withRoute = sampleActivity(
            "mapped",
            route = listOf(
                ActivityRoutePoint(51.5, -0.12),
                ActivityRoutePoint(51.51, -0.11),
            ),
        )
        assertEquals("mapped", pickFeaturedActivity(listOf(indoor, outdoor, consent, withRoute))?.id)
        assertEquals("need-map", pickFeaturedActivity(listOf(indoor, outdoor, consent))?.id)
        assertEquals("walk", pickFeaturedActivity(listOf(indoor, outdoor))?.id)
        assertEquals("gym", pickFeaturedActivity(listOf(indoor))?.id)
        assertNull(pickFeaturedActivity(emptyList()))
    }

    @Test
    fun paceFromDistanceAndDuration() {
        val activity = sampleActivity(
            "run",
            start = noon,
            end = noon.plusSeconds(30 * 60),
            distanceKm = 5.0,
        )
        val pace = activity.avgPaceMinPerKm
        requireNotNull(pace)
        assertEquals(6.0, pace, 0.05)
        assertEquals("6:00 /km", formatPace(pace))
    }

    @Test
    fun widgetOrderInsertsActivitiesAfterDailyHealth() {
        val old = "DAILY_HEALTH:true,BODY_STATS:true,HISTORY:true"
        val migrated = SettingsRepository.migrateHealthWidgetOrder(old)
        assertEquals(
            "DAILY_HEALTH:true,ACTIVITIES:true,BODY_STATS:true,HISTORY:true",
            migrated,
        )
        assertEquals(
            migrated,
            SettingsRepository.migrateHealthWidgetOrder(migrated),
        )
        val legacy = "BODY_STATS:true,HISTORY:true"
        val fromLegacy = SettingsRepository.migrateHealthWidgetOrder(legacy)
        assertTrue(fromLegacy.startsWith("DAILY_HEALTH:true,ACTIVITIES:true,"))
    }

    @Test
    fun resolveRouteFromHealthConnectResults() {
        val t0 = Instant.parse("2026-08-27T12:00:00Z")
        val data = resolveActivityRoute(
            ExerciseRouteResult.Data(
                ExerciseRoute(
                    listOf(
                        ExerciseRoute.Location(t0, 51.5, -0.12),
                        ExerciseRoute.Location(t0.plusSeconds(12), 51.501, -0.119),
                    ),
                ),
            ),
        )
        assertEquals(2, data.points.size)
        assertEquals(51.5, data.points.first().latitude, 0.0)
        assertFalse(data.consentRequired)

        val consent = resolveActivityRoute(ExerciseRouteResult.ConsentRequired())
        assertTrue(consent.points.isEmpty())
        assertTrue(consent.consentRequired)

        val none = resolveActivityRoute(ExerciseRouteResult.NoData())
        assertTrue(none.points.isEmpty())
        assertFalse(none.consentRequired)
    }

    @Test
    fun routeAttemptClearsConsentWhenUserDeniesOrEmpty() {
        val waiting = sampleActivity("need-map", routeConsentRequired = true)
        val denied = activityAfterRouteAttempt(waiting, null)
        assertFalse(denied.routeConsentRequired)
        assertTrue(denied.route.isEmpty())

        val granted = activityAfterRouteAttempt(
            waiting,
            ExerciseRoute(
                listOf(
                    ExerciseRoute.Location(noon, 51.5, -0.12),
                    ExerciseRoute.Location(noon.plusSeconds(20), 51.501, -0.119),
                ),
            ),
        )
        assertFalse(granted.routeConsentRequired)
        assertEquals(2, granted.route.size)
        assertEquals("need-map", granted.id)
    }

    @Test
    fun outdoorWorkoutsOfferRouteConsentWhenListOmitsGps() {
        assertFalse(
            activityNeedsRouteConsent(
                points = listOf(
                    ActivityRoutePoint(51.5, -0.12),
                    ActivityRoutePoint(51.51, -0.11),
                ),
                hcConsentRequired = false,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ),
        )
        assertTrue(
            activityNeedsRouteConsent(
                points = emptyList(),
                hcConsentRequired = true,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ),
        )
        assertTrue(
            activityNeedsRouteConsent(
                points = emptyList(),
                hcConsentRequired = false,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ),
        )
        assertFalse(
            activityNeedsRouteConsent(
                points = emptyList(),
                hcConsentRequired = false,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ),
        )
    }

    @Test
    fun mapViewportKeepsRouteInsideFrameAndUsesWebMercatorTiles() {
        assertEquals(0.5, lngToTileX(0.0, 0), 1e-9)
        assertEquals(0.5, latToTileY(0.0, 0), 1e-6)
        assertEquals(1.0, lngToTileX(0.0, 1), 1e-9)

        val points = listOf(
            ActivityRoutePoint(51.5074, -0.1278),
            ActivityRoutePoint(51.5088, -0.1260),
            ActivityRoutePoint(51.5095, -0.1242),
            ActivityRoutePoint(51.5102, -0.1225),
        )
        val viewport = buildRouteMapViewport(points, viewAspect = 2.0)
        requireNotNull(viewport)
        assertTrue(viewport.zoom in 3..16)
        points.forEach { point ->
            val x = viewport.fractionX(point.longitude)
            val y = viewport.fractionY(point.latitude)
            assertTrue(x in 0.05f..0.95f)
            assertTrue(y in 0.05f..0.95f)
        }
        val tilesX = viewport.tileX1 - viewport.tileX0 + 1
        val tilesY = viewport.tileY1 - viewport.tileY0 + 1
        assertTrue(tilesX in 1..5)
        assertTrue(tilesY in 1..4)
    }

    @Test
    fun routeAttemptMarksTheTrackResolvedEitherWay() {
        val pending = sampleActivity("pending", routeConsentRequired = true).copy(routeResolved = false)
        assertFalse(pending.routeResolved)

        // Denied / no GPS still counts as resolved, so the row stops showing
        // "Loading map…" forever.
        assertTrue(activityAfterRouteAttempt(pending, null).routeResolved)

        val granted = activityAfterRouteAttempt(
            pending,
            ExerciseRoute(
                listOf(
                    ExerciseRoute.Location(noon, 51.5, -0.12),
                    ExerciseRoute.Location(noon.plusSeconds(20), 51.501, -0.119),
                ),
            ),
        )
        assertTrue(granted.routeResolved)
        assertTrue(granted.hasRoute)
    }

    @Test
    fun routesPermissionAloneIsNotEnoughToClaimHealthData() {
        // READ_EXERCISE_ROUTES reads nothing by itself — treating it as data
        // access showed an all-zero dashboard as a successful read.
        assertFalse(
            HealthConnectRepository.EXERCISE_ROUTES_PERMISSION in
                HealthConnectRepository.DATA_PERMISSIONS,
        )
        assertTrue(HealthConnectRepository.STEPS_PERMISSION in HealthConnectRepository.DATA_PERMISSIONS)
        assertEquals(
            HealthConnectRepository.PERMISSIONS.size - 1,
            HealthConnectRepository.DATA_PERMISSIONS.size,
        )
    }

    @Test
    fun activityHistoryCoversAFullMonth() {
        assertTrue(HealthConnectRepository.ACTIVITY_HISTORY_DAYS >= 30)
        assertTrue(HealthConnectRepository.ACTIVITY_HISTORY_LIMIT >= 30)
        assertTrue(HealthConnectRepository.EAGER_ROUTE_COUNT < HealthConnectRepository.ACTIVITY_HISTORY_LIMIT)
    }

    private fun sampleActivity(
        id: String,
        start: Instant = noon,
        end: Instant = noon.plusSeconds(1800),
        distanceKm: Double? = null,
        route: List<ActivityRoutePoint> = emptyList(),
        routeConsentRequired: Boolean = false,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ) = HealthActivity(
        id = id,
        title = "Test",
        exerciseType = exerciseType,
        startTime = start,
        endTime = end,
        sourcePackage = "com.garmin.android.apps.connectmobile",
        sourceLabel = "Garmin",
        deviceLabel = "Forerunner 965",
        distanceKm = distanceKm,
        caloriesKcal = 220.0,
        steps = 4000,
        avgHr = 132,
        maxHr = 154,
        minHr = 98,
        elevationGainM = 42.0,
        route = route,
        routeConsentRequired = routeConsentRequired,
    )
}

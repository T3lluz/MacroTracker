# DailyDash — Agent Guide

## Project Identity
Single-module Android app (Kotlin + Jetpack Compose). The **app label is "DailyDash"**; the package/module name is `com.macrotracker`. Keep both names in mind — they differ intentionally.

## Build & API Keys
```bash
./gradlew assembleDebug      # standard debug build
./gradlew installDebug       # build + deploy to connected device
```
API keys go in `local.properties` (never committed):
```
GEMINI_API_KEY=...
YOUTUBE_API_KEY=...
```
At runtime, `SettingsRepository` exposes a user-editable Gemini key (Settings UI) that takes priority over the build-time key. `NutritionAiRepository.apiKey` handles the fallback chain.

## Architecture Overview
```
com.macrotracker/
  DailyDashApp.kt          ← @HiltAndroidApp; configures Coil with browser User-Agent for F1 CDN
  MainActivity.kt          ← single activity, edge-to-edge, sets DailyDashTheme + MainScreen
  data/
    local/                 ← Room DB (macro_tracker.db): MacroLogEntity, GoalsEntity, MacroDao
                              (incl. `DayTotals` batch-totals projection + `getTotalsForDates()` query),
                              MacroRepository (suspend fns; `getDailySummary`, `getDailySummariesRange`,
                              `getDailySummariesBetween` — all use 2 DB round-trips via batch query),
                              SettingsRepository (two SharedPrefs: `macro_tracker_settings` +
                              `health_connect_settings`; per-metric health toggles + master toggle
                              + `weatherEnabled`/`calendarEnabled` all as `StateFlow`)
    remote/                ← Gemini AI via OkHttp directly (NutritionAiRepository, WeatherAiRepository),
                              WeatherRepository, LocationProvider
    health/                ← HealthConnectRepository (read-only; lazy client; PERMISSIONS companion set);
                              reads: Steps, HeartRate, RestingHeartRate, OxygenSaturation,
                              RespiratoryRate, Distance, FloorsClimbed, ActiveCaloriesBurned,
                              SleepSession, TotalCaloriesBurned; has throttle cache to avoid
                              hammering Health Connect IPC on rapid ViewModel refreshes
    f1/                    ← F1Repository via Ktor + OpenF1 API (https://api.openf1.org/v1/);
                              15-min in-memory + SharedPrefs disk cache; F1RepositoryEntryPoint for widgets
    youtube/               ← YouTubeRepository via RSS feeds (no API key); tracked channels in SharedPrefs
    calendar/              ← CalendarRepository (READ_CALENDAR permission)
  di/
    AppModule.kt           ← all @Provides (DB, DAO, OkHttpClient, KtorClient);
                              @Binds abstract modules for F1 and YouTube interface → impl
  ui/
    screens/               ← one file per tab screen (HomeScreen, HealthScreen, AIScreen, HistoryScreen,
                              SettingsScreen) + sub-screens (StatsScreen, HelpScreen, CameraScanScreen)
                              + onboarding/ (SplashScreen overlay, WelcomeScreen, PermissionsScreen, TutorialScreen)
    viewmodel/             ← one @HiltViewModel per screen; UI state as sealed classes via StateFlow;
                              includes OnboardingViewModel (manages onboardingCompleted + splashShown flags);
                              DashboardViewModel (per-metric Health Connect StateFlows with today/yesterday
                              comparison — used directly by HealthScreen, NOT via DashboardScreen);
                              YouTubeViewModel (YouTube feed + channel search — consumed by YoutubeCard
                              component directly via hiltViewModel(), not from a screen ViewModel);
                              F1UiState.kt (dedicated file for the F1 sealed interface used by HomeViewModel)
    navigation/            ← Screen.kt (sealed class, 5 bottom-nav tabs) + OnboardingRoutes (const routes)
                              + DailyDashNavHost.kt
    components/            ← shared Composables (MacroCard, PillNavigationBar, DraggableWidgetColumn,
                              WidgetEditor, WidgetExpandBar, …)
    theme/                 ← Color, Theme, Animation (MacroMotion object — single source for all specs)
    util/                  ← HapticHelper (Compose-friendly performHapticFeedback wrapper, ui/util/Haptics.kt)
                              + LastUpdatedText composable + rememberRelativeTime (ui/util/LastUpdated.kt)
  widget/                  ← Glance-based home-screen widgets:
                              DashboardWidget, MacrosWidget, HealthWidget, WeatherWidget, CalendarWidget,
                              F1CountdownWidget, F1StandingsWidget, F1ScheduleWidget (+ *Receiver.kt for each);
                              WidgetComponents.kt (shared Glance composables + WidgetSizes grid constants);
                              DashboardWidgetDataProvider (reads DB/Health Connect/Weather directly — no Hilt);
                              F1WidgetDataProvider (15-min memory+disk cache);
                              RefreshWidgetAction / RefreshF1WidgetAction (Glance ActionCallbacks);
                              WidgetUpdater + WidgetRefreshWorker;
                              F1WidgetColors.kt (F1Clr token class + teamColorProvider/podiumColor helpers);
                              F1WidgetStatus.kt (F1WidgetStatusTag composable + statusTagText/f1WidgetEmptyMessage);
                              DashboardWidgetData.kt (DashboardWidgetData snapshot + HourlyForecast + CalendarEvent)
  util/                    ← HapticUtils (raw VibrationEffect-based haptics, used outside Compose)
```

## Key Patterns

### Dependency Injection
Hilt throughout. `AppModule.kt` is the only `@Provides` module. Concrete implementations are bound to interfaces via separate abstract `@Binds` modules (`F1DataModule`, `YouTubeDataModule`). **Glance widgets cannot receive injected deps normally** — they use `EntryPointAccessors`, e.g. `F1RepositoryEntryPoint`.

### UI State
Each screen's ViewModel exposes sealed-class state via `StateFlow`. Example pattern from `HomeViewModel`:
```kotlin
sealed class WeatherUiState { object Loading; data class Success(...); data class Error(val message: String) }
private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
val weatherState: StateFlow<WeatherUiState> = _weatherState
```

When a sealed interface is shared across a screen and its sub-components, it lives in its own file (e.g. `F1UiState.kt` in `ui/viewmodel/`).

Health Connect metrics use `HealthMetricUiState(value, today, yesterday, isEnabled)` from `ui/components/BodyStats.kt`. Use `calculatePercentageChange(today, yesterday)` to derive the delta arrow shown on each metric card.

**Throttled loading pattern** — both `DashboardViewModel` and `HealthViewModel` skip reloads if called within 30 s of the last load:
```kotlin
fun loadDataThrottled() {
    if (lastLoadMs > 0 && System.currentTimeMillis() - lastLoadMs < 30_000L) return
    loadData()
}
```
`DashboardViewModel.loadData()` also cancels any in-flight job first (`loadJob?.cancel()`) to prevent pile-up on rapid calls.

### Navigation
`DailyDashNavHost` owns all routes. The **`SplashOverlay` is a full-window Compose overlay** placed above the `Scaffold` in `MainScreen` — it is **not a nav destination** and lives in `OnboardingViewModel.splashShown`. The rest of the onboarding flow (`WelcomeScreen`, `PermissionsScreen`, `TutorialScreen`) **are** nav destinations via `OnboardingRoutes.WELCOME/PERMISSIONS/TUTORIAL`. Transitions are defined exclusively in `MacroMotion` (`ui/theme/Animation.kt`); do not hardcode tween/spring values elsewhere.

Sub-screens (`stats`, `help`, `camera_scan`) are composed inside `DailyDashNavHost` with `MacroMotion.subScreenEnter/Exit/PopEnter/PopExit` transitions and are navigated to imperatively from their parent screens (e.g. `SettingsScreen` → `help`, `AIScreen`/`HealthScreen` → `camera_scan`).

### Home Screen Widgets (draggable)
Widget order and visibility are persisted as a single colon-and-comma encoded string in SharedPrefs:
```
"WEATHER:true,CALENDAR:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,YOUTUBE:true"
```
`DraggableWidgetColumn` + `WidgetEditor` read/write this via `SettingsRepository`.

The **Health screen** uses the same draggable pattern with a separate key (`healthWidgetOrder`):
```
"BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true"
```

### App Widgets (Glance)
All Glance widgets are refreshed together via `WidgetUpdater.updateAllWidgets(context)` (call from the app) or `WidgetRefreshWorker` (periodic WorkManager task, 15-min interval, requires network). F1 widgets share a disk/memory cache through `F1WidgetDataProvider`. Full widget list: `DashboardWidget`, `MacrosWidget`, `HealthWidget`, `WeatherWidget`, `CalendarWidget`, `F1CountdownWidget`, `F1StandingsWidget`, `F1ScheduleWidget`.

`DashboardWidgetDataProvider` reads Room, Health Connect, weather cache, and calendar directly without Hilt (same no-injection pattern as `F1WidgetDataProvider` — use `EntryPointAccessors` when an interface is needed). `WidgetComponents.kt` houses all shared Glance composables and the `WidgetSizes` grid-constant object (cell formula: `74×n − 2 dp`; min 2×2, max 5×3).

F1 widget theming goes through `F1WidgetColors.kt`: instantiate `F1Clr` for the palette, call `teamColorProvider(hex)` to parse a team hex string into a Glance `ColorProvider`, and `podiumColor(position, c)` for gold/silver/bronze medal colours. Status tags (last-updated, stale, syncing) are rendered via `F1WidgetStatus.kt` (`F1WidgetStatusTag`, `statusTagText`, `f1WidgetEmptyMessage`). The dashboard widget snapshot type is `DashboardWidgetData` in `DashboardWidgetData.kt` (also contains `HourlyForecast` and widget-layer `CalendarEvent`).

### External APIs
| Service | Client | Notes |
|---|---|---|
| Gemini AI | OkHttp (direct JSON) | Key from `SettingsRepository` > `BuildConfig` |
| OpenF1 | Ktor (`HttpClient`) | Base URL `https://api.openf1.org/v1/`; browser User-Agent set in `AppModule` |
| YouTube | RSS feed (OkHttp) | No API key; tracked channels in SharedPrefs |
| Weather | HTTP (WeatherRepository) | AI summary via Gemini |
| Health Connect | SDK | Read-only; lazy client; gracefully returns null if SDK unavailable |

### Compose Strong Skipping
`composeCompiler { enableStrongSkippingMode = true }` is set in `app/build.gradle.kts`. Composables with unstable parameters will skip recomposition automatically — avoid fighting this with `@Stable`/`@Immutable` unless you observe real correctness issues.

## Important Files to Read First
- `di/AppModule.kt` — understand what is injected and how
- `data/local/Entities.kt` — the two Room entities (only calories + protein tracked)
- `ui/navigation/Screen.kt` + `DailyDashNavHost.kt` — full route map
- `ui/theme/Animation.kt` (`MacroMotion`) — all animation specs
- `ui/viewmodel/DashboardViewModel.kt` — per-metric Health Connect states (today/yesterday) consumed by `HealthScreen`
- `ui/components/BodyStats.kt` — `HealthMetricUiState` data class + `calculatePercentageChange()`
- `widget/WidgetUpdater.kt` + `WidgetRefreshWorker.kt` — widget update strategy
- `widget/WidgetComponents.kt` — shared Glance composables + `WidgetSizes` grid constants
- `ui/screens/onboarding/` — multi-step onboarding flow (SplashScreen overlay + 3 nav-routed screens)


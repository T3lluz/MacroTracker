<p align="center">
  <img src="docs/assets/icon.png" alt="DailyDash icon" width="120" height="120" />
</p>

<h1 align="center">DailyDash</h1>

<p align="center">
  <strong>Your day on one screen.</strong><br />
  Nutrition, health, weather, calendar, F1, and YouTube — laid out your way.
</p>

<p align="center">
  <a href="https://github.com/T3lluz/MacroTracker/releases/latest"><img src="https://img.shields.io/github/v/release/T3lluz/MacroTracker?style=for-the-badge&color=4F7CFF&label=Download%20APK" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android%2026%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 26+" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Compose" />
  <img src="https://img.shields.io/badge/Privacy-On%20device-22C55E?style=for-the-badge" alt="On-device privacy" />
</p>

<p align="center">
  <a href="https://github.com/T3lluz/MacroTracker/releases/latest"><strong>Get the latest APK</strong></a>
  ·
  <a href="#quick-start">Build from source</a>
  ·
  <a href="#features">Features</a>
</p>

---

## Why DailyDash?

Most apps do one thing. DailyDash is the glanceable home for your whole day — macros next to the weather, race countdown next to your calendar, health metrics next to today’s YouTube picks.

Drag widgets into the order you care about. Hide the rest. Everything stays on your phone unless you choose to talk to AI.

---

## Features

### Your personal dashboard
Weather, Formula 1, YouTube, calendar events, and health stats on one customisable home screen. Long-press to reorder. Toggle anything on or off — show only what matters.

### Macro & nutrition tracking
Log food by typing, scanning a nutrition label with the camera, or describing a meal in plain English. Track calories and protein against daily goals, with progress that turns red when you overshoot. Swipe to delete. Chart the last 7 / 14 / 30 days on the Health tab.

### AI-powered estimates
Ask things like *“large bowl of porridge with banana”* and get a macro estimate you can log in one tap. Pick **Gemini**, **OpenAI**, or **OpenRouter** in Settings, paste your own key, and (for OpenRouter) choose a cheap model with list prices shown.

### Live info at a glance
| Widget | What you see |
| --- | --- |
| **Weather** | Local conditions via Yr.no (location permission) |
| **Calendar** | Today’s upcoming events |
| **F1** | Next race countdown, standings, and the full schedule |
| **YouTube** | Latest videos from channels you track (RSS — no key required) |

### Health Connect (optional)
Layer in steps, heart rate, resting HR, SpO₂, respiratory rate, distance, floors climbed, sleep, and active / total calories. Today vs yesterday deltas on every metric. All reads stay on-device — nothing is uploaded.

### Home-screen widgets
Pin Glance widgets to your Android home screen:

- **Dashboard** — macros, health, weather, calendar & more
- **Nutrition** — calorie & protein progress
- **Health** — steps, heart rate, sleep, active calories
- **Weather** · **Calendar**
- **F1: Next Race** · **Standings** · **Schedule**

Widgets refresh together (in-app or every ~30 minutes in the background).

### Built for privacy
Food logs and settings live in a local Room database and SharedPreferences. AI calls go only to the provider you configure — your food history is never sent as context for analytics. Health Connect data stays on the device.

---

## Tabs at a glance

| Tab | What it’s for |
| --- | --- |
| **Home** | Greeting, live widgets, Quick Add |
| **Health** | Body stats, macro trends, recent logs, goals progress |
| **AI** | Plain-English meal estimates + camera label scan |
| **Settings** | Goals, AI provider & keys, connections, help |

---

## Quick start

**Requirements:** Android Studio *(or JDK 17 + Android SDK 35)*, device or emulator on **API 26+**.

```bash
cp local.properties.example local.properties
# set sdk.dir — and optional build-time API keys

./gradlew assembleDebug
./gradlew installDebug   # deploy to a connected device
```

Keys entered in **Settings** override anything baked in from `local.properties`:

```properties
GEMINI_API_KEY=
OPENAI_API_KEY=
OPENROUTER_API_KEY=
YOUTUBE_API_KEY=
```

---

## Install & updates

Every merge to `master` publishes a tester-signed APK via **Build & Release APK**:

1. Bumps `versionCode` / `versionName` when needed  
2. Builds a minified release APK  
3. Publishes `DailyDash-{version}-vc{code}.apk` to [GitHub Releases](https://github.com/T3lluz/MacroTracker/releases)

Install once from Releases — later builds update in-app when the version code is higher, then DailyDash relaunches automatically.

Manual run: **Actions → Build & Release APK → Run workflow**.

---

## Project map

```
app/src/main/kotlin/com/macrotracker/
  ui/          screens, components, theme, navigation
  data/        Room, Settings, AI, weather, F1, YouTube, Health Connect
  widget/      Glance home-screen widgets
  di/          Hilt modules
```

Package name is `com.macrotracker`; the app label is **DailyDash**.  
Deeper architecture notes for agents live in [`AGENTS.md`](AGENTS.md).

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Release APK (tester-signed) | `./gradlew assembleRelease` |
| Install debug | `./gradlew installDebug` |

Gradle already enables parallel builds, the build cache, and configuration cache (`gradle.properties`). CI caches Gradle home via `gradle/actions/setup-gradle` and relies on the preinstalled Android SDK on `ubuntu-latest` (no separate SDK setup/cache steps).

---

## Security notes

- Never commit `local.properties`, `.env`, or real API keys  
- `app/tester.jks` is the shared **tester** signing key (intentional for sideloaded updates)  
- Prefer entering AI keys in Settings on device rather than baking them into the APK  

---

<p align="center">
  <sub>DailyDash — one screen for the day that actually matters.</sub>
</p>

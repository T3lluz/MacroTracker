# DailyDash

Your day on one screen — nutrition, health, weather, calendar, F1, and YouTube.

Kotlin · Jetpack Compose · single-module Android app  
Package: `com.macrotracker`

---

## What you get

| Area | Details |
| --- | --- |
| Nutrition | Log calories & protein, goals, trends, camera food scan |
| Health | Health Connect metrics with today vs yesterday deltas |
| Home widgets | Drag-to-reorder cards + Glance home-screen widgets |
| Live data | Weather, calendar, F1 (OpenF1), YouTube RSS |
| AI | Gemini, OpenAI, or OpenRouter — pick provider + key in Settings |
| Updates | Merge to `master` → GitHub Release APK → in-app update |

---

## Quick start

**Requirements:** Android Studio (or JDK 17 + Android SDK 35), device/emulator on API 26+.

```bash
cp local.properties.example local.properties
# set sdk.dir and any API keys you want at build time

./gradlew assembleDebug
./gradlew installDebug   # optional: deploy to a connected device
```

Keys in Settings override build-time keys from `local.properties`.

```properties
GEMINI_API_KEY=
OPENAI_API_KEY=
OPENROUTER_API_KEY=
YOUTUBE_API_KEY=
```

---

## Releases

Every merge to `master` runs **Build & Release APK**:

1. Auto-bumps `versionCode` / `versionName` if that build was already published  
2. Builds a minified release APK signed with the shared tester keystore  
3. Publishes `DailyDash-{version}-vc{code}.apk` with concise release notes (PR links) to [GitHub Releases](https://github.com/T3lluz/MacroTracker/releases)

Install once from Releases; later builds update in-app when the version code is higher. After install, DailyDash relaunches automatically.

Manual run: Actions → **Build & Release APK** → Run workflow (optional release notes override).

---

## Project map

```
app/src/main/kotlin/com/macrotracker/
  ui/          screens, components, theme, navigation
  data/        Room, Settings, AI, weather, F1, YouTube, Health Connect
  widget/      Glance home-screen widgets
  di/          Hilt modules
```

Agent-oriented architecture notes live in [`AGENTS.md`](AGENTS.md).

---

## Build notes

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Release APK (tester-signed) | `./gradlew assembleRelease` |
| Install debug | `./gradlew installDebug` |

Gradle already enables parallel builds, the build cache, and configuration cache (`gradle.properties`). CI caches Gradle home plus Android SDK platforms/build-tools so release builds stay fast after the first warm run.

---

## Security

- Never commit `local.properties`, `.env`, or real API keys  
- `app/tester.jks` is the shared **tester** signing key (intentional for sideloaded updates)  
- Prefer entering AI keys in Settings on device rather than baking them into the APK

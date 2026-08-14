<div align="center">
  <img src="docs/assets/icon.png" width="72" alt="DailyDash" />
  <h1>DailyDash</h1>
  <p>Your day on one screen — nutrition, health, weather, calendar, F1, GitHub, YouTube, and Twitch.</p>
  <p>
    <a href="https://github.com/T3lluz/MacroTracker/releases/latest"><img alt="Download APK" src="https://img.shields.io/github/v/release/T3lluz/MacroTracker?label=download&color=4F7CFF" /></a>
    <img alt="Android 26+" src="https://img.shields.io/badge/Android%2026%2B-3DDC84?logo=android&logoColor=white" />
    <img alt="Kotlin Compose" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" />
    <img alt="On-device" src="https://img.shields.io/badge/privacy-on--device-22C55E" />
  </p>
</div>

Drag home widgets into the order you care about and hide the rest. Food logs and Health Connect reads stay on the phone unless you choose to talk to AI.

| | Integration | Connects with | Notes |
| :---: | --- | --- | --- |
| <img src="docs/integrations/weather.svg" width="20" height="20" alt="" /> | [Weather](#weather) | [Yr.no](https://www.yr.no/) | Location permission |
| <img src="docs/integrations/calendar.svg" width="20" height="20" alt="" /> | [Calendar](#calendar) | Device calendars | Calendar permission |
| <img src="docs/integrations/health-connect.svg" width="20" height="20" alt="" /> | [Health Connect](#health-connect) | Android Health Connect | Read-only, per-metric toggles |
| <img src="docs/integrations/f1.svg" width="20" height="20" alt="" /> | [Formula 1](#formula-1) | [OpenF1](https://openf1.org/) | No key |
| <img src="docs/integrations/github.svg" width="20" height="20" alt="" /> | [GitHub](#github) | Device Code OAuth | `repo` + `read:user` |
| <img src="docs/integrations/youtube.svg" width="20" height="20" alt="" /> | [YouTube](#youtube) | RSS + <img src="docs/integrations/google.svg" width="14" height="14" alt="" /> Google | Connect Google to import subscriptions |
| <img src="docs/integrations/twitch.svg" width="20" height="20" alt="" /> | [Twitch](#twitch) | Helix + Device Code | `user:read:follows` via twitch.tv/activate |
| <img src="docs/integrations/gemini.svg" width="20" height="20" alt="" /> | [Gemini](#ai) | API key in Settings | Meal estimates and label scan |
| <img src="docs/integrations/openai.svg" width="20" height="20" alt="" /> | [OpenAI](#ai) | API key in Settings | Meal estimates and label scan |
| <img src="docs/integrations/openrouter.svg" width="20" height="20" alt="" /> | [OpenRouter](#ai) | API key + model picker | Cheap models with list prices |

## Features

### Home dashboard

Long-press to reorder. Toggle anything on or off.

| | Widget | What you see |
| :---: | --- | --- |
| <img src="docs/integrations/weather.svg" width="20" height="20" alt="" /> | **Weather** | Local conditions via Yr.no |
| <img src="docs/integrations/calendar.svg" width="20" height="20" alt="" /> | **Calendar** | Today's upcoming events |
| <img src="docs/integrations/f1.svg" width="20" height="20" alt="" /> | **F1** | Next race countdown, standings, and the full schedule |
| <img src="docs/integrations/github.svg" width="20" height="20" alt="" /> | **GitHub** | Issues, PRs, activity, and repos across your account |
| <img src="docs/integrations/youtube.svg" width="20" height="20" alt="" /> | **YouTube** | Latest videos from channels you track |
| <img src="docs/integrations/twitch.svg" width="20" height="20" alt="" /> | **Twitch** | Live board from followed channels |
| <img src="docs/integrations/nutrition.svg" width="20" height="20" alt="" /> | **Nutrition** | Calories and protein against daily goals, plus Quick Add |
| <img src="docs/integrations/health-connect.svg" width="20" height="20" alt="" /> | **Health** | Steps, heart, sleep, and more when Health Connect is enabled |

### <img src="docs/integrations/weather.svg" width="22" height="22" alt="" /> Weather

Local conditions and clothing hints from [Yr.no](https://www.yr.no/), using the device location. Toggle on in Settings → Connections.

### <img src="docs/integrations/calendar.svg" width="22" height="22" alt="" /> Calendar

Today's upcoming events from calendars on the device. Toggle on in Settings → Connections (calendar permission).

### <img src="docs/integrations/f1.svg" width="22" height="22" alt="" /> Formula 1

Next race countdown, driver/constructor standings, and the season schedule via the [OpenF1](https://openf1.org/) API. No API key. The same data powers the three F1 home-screen widgets.

### <img src="docs/integrations/github.svg" width="22" height="22" alt="" /> GitHub

Account-wide issues, PRs, activity, and repos — not a single project. Connect with Device Code OAuth (Custom Tabs → `github.com/login/device`, scopes `repo` + `read:user`). Add `GITHUB_CLIENT_ID` to `local.properties` (OAuth App Client ID, no secret in the APK). A leftover PAT still works until you disconnect.

### <img src="docs/integrations/youtube.svg" width="22" height="22" alt="" /> YouTube

Latest videos from channels you track, via RSS — no key required. **Connect Google** imports your YouTube subscriptions into Watching (YouTube Data API v3 + Android OAuth client for `com.macrotracker`). `YOUTUBE_API_KEY` is optional and unused for RSS.

### <img src="docs/integrations/twitch.svg" width="22" height="22" alt="" /> Twitch

Live board from followed channels. Connect via Device Code (`twitch.tv/activate`, scope `user:read:follows`) so SMS 2FA works. Needs `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET` in `local.properties` (Confidential app; redirect URL is unused at runtime).

### Nutrition

Log food by typing, scanning a nutrition label with the camera, or describing a meal in plain English. Track calories and protein against daily goals — progress turns red when you overshoot. Swipe to delete. Chart the last 7 / 14 / 30 days on the Health tab.

### AI

Ask things like *“large bowl of porridge with banana”* and log the estimate in one tap. Camera label scan uses the same provider. Pick one in Settings, paste your own key — stored keys override anything baked in from `local.properties`.

| | Provider | Key | Notes |
| :---: | --- | --- | --- |
| <img src="docs/integrations/gemini.svg" width="20" height="20" alt="" /> | **Gemini** | `GEMINI_API_KEY` | Free keys from [aistudio.google.com](https://aistudio.google.com/) (`AIza…`) |
| <img src="docs/integrations/openai.svg" width="20" height="20" alt="" /> | **OpenAI** | `OPENAI_API_KEY` | [platform.openai.com](https://platform.openai.com/) (`sk-…`) |
| <img src="docs/integrations/openrouter.svg" width="20" height="20" alt="" /> | **OpenRouter** | `OPENROUTER_API_KEY` | [openrouter.ai/keys](https://openrouter.ai/keys) (`sk-or-…`). Settings shows a cheap-model picker with list prices |

Estimates are approximations — the result shows high / medium / low confidence. For precise tracking, scan a label or enter values yourself.

### <img src="docs/integrations/health-connect.svg" width="22" height="22" alt="" /> Health Connect

Optional, read-only, on-device. Toggle the master switch in Settings → Connections, then enable individual metrics.

Steps, heart rate, resting HR, SpO₂, respiratory rate, distance, floors climbed, sleep, and active / total calories — each with a today vs yesterday delta.

The Health tab has its own draggable layout: Daily Health rings, body stats, macro trends, recent logs, and goals.

### Home-screen widgets

Pin Glance widgets to the Android home screen. They refresh together from the app, or about every 30 minutes in the background.

| Widget | What you see |
| --- | --- |
| **Dashboard** | Macros, health, weather, calendar, and more |
| **Nutrition** | Calorie and protein progress |
| **Health** | Steps, heart rate, sleep, active calories |
| **Weather** · **Calendar** | Conditions and today's events |
| **F1: Next Race** · **Standings** · **Schedule** | Race weekend at a glance |

### Privacy

Food logs and settings live in a local Room database and SharedPreferences. AI calls go only to the provider you configure — food history is never sent as analytics context. Health Connect data never leaves the device.

## Tabs

| Tab | What it's for |
| --- | --- |
| **Home** | Greeting, live widgets, Quick Add |
| **Health** | Body stats, macro trends, recent logs, goals |
| **AI** | Plain-English meal estimates and camera label scan |
| **Settings** | Goals, AI provider and keys, connections, help |

## Install

**Sideload the APK** from [Releases](https://github.com/T3lluz/MacroTracker/releases/latest), or build from source.

**Requirements:** Android Studio *(or JDK 17 + Android SDK 36)*, device or emulator on **API 26+**.

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
TWITCH_CLIENT_ID=
TWITCH_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_TOKEN=          # optional PAT fallback; prefer OAuth
```

YouTube **Connect Google** does not use `YOUTUBE_API_KEY` — it needs a Google Cloud Android OAuth client for package `com.macrotracker` (tester.jks SHA-1 is in `local.properties.example`). Published APKs also need Actions secrets `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`, and `GH_OAUTH_CLIENT_ID` (GitHub forbids secrets named `GITHUB_*`).

Every merge to `master` publishes a tester-signed APK. Install once from Releases — later builds update in-app when the version code is higher. DailyDash downloads the APK, installs it, relaunches, and shows **What's new**. If the system blocks the relaunch, a tap-to-open notification appears.

## Dev

Package name is `com.macrotracker`; the app label is **DailyDash**.

```
app/src/main/kotlin/com/macrotracker/
  ui/          screens, components, theme, navigation
  data/        Room, Settings, AI, weather, F1, YouTube, Twitch, GitHub, Health Connect
  widget/      Glance home-screen widgets
  di/          Hilt modules
```

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Release APK (tester-signed) | `./gradlew assembleRelease` |
| Install debug | `./gradlew installDebug` |

- Never commit `local.properties`, `.env`, or real API keys
- `app/tester.jks` is the shared **tester** signing key (intentional for sideloaded updates)
- Prefer entering AI keys in Settings on device rather than baking them into the APK

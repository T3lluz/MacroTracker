# Firebase App Distribution Setup

## 1) One-time Firebase setup

1. In Firebase Console, open your Android app with package name `com.fstal.macrotracker`.
2. Go to **App Distribution** and enable it.
3. Copy your Firebase Android **App ID** (format like `1:1234567890:android:abcdef...`).
4. Create a CI token (local machine):

```bash
npx firebase-tools login:ci
```

This command prints `FIREBASE_TOKEN`. 5. Download `google-services.json` for Android app `com.fstal.macrotracker`.

## 2) GitHub repository secrets

Add these repo secrets in GitHub: **Settings → Secrets and variables → Actions**.

- `FIREBASE_APP_ID` (required)
- `FIREBASE_TOKEN` (required)
- `GOOGLE_SERVICES_JSON` (required, full raw JSON content)
- `FIREBASE_TESTER_GROUPS` (optional, comma-separated groups)

## 3) Distribute from GitHub Actions

Use workflow [firebase-app-distribution.yml](.github/workflows/firebase-app-distribution.yml).

1. Open **Actions** → **Firebase App Distribution**
2. Click **Run workflow**
3. Optionally pass:
   - `release_notes`
   - `testers` (comma-separated emails)
   - `groups` (comma-separated group aliases)

The workflow builds release APK, uploads it as an artifact, then sends it to Firebase testers.

## 4) Distribute from local machine

### Build APK

```bash
npm run apk:build
```

APK is copied to `dist/MacroTracker-release.apk`.

### Upload APK

Set environment variables and run:

```bash
npm run firebase:distribute
```

Required environment variables:

- `FIREBASE_APP_ID`
- `FIREBASE_TOKEN`

Local file required:

- `google-services.json` in project root

Optional environment variables:

- `APK_PATH` (custom APK path)
- `FIREBASE_TESTERS` (emails)
- `FIREBASE_GROUPS` or `FIREBASE_TESTER_GROUPS` (group aliases)
- `FIREBASE_RELEASE_NOTES`

## Notes

- Your Android release build currently uses debug signing in [android/app/build.gradle](android/app/build.gradle#L135-L145). This works for testing, but configure a real release keystore before production.
- Local Android build should use Java 17 for compatibility.

#!/usr/bin/env bash
# Rename the release APK to the in-app update contract and emit release metadata.
# Contract: DailyDash-{versionName}-vc{versionCode}.apk
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"
APK_SRC="app/build/outputs/apk/release/app-release.apk"

if [ -n "${VERSION_NAME:-}" ] && [ -n "${VERSION_CODE:-}" ]; then
  : # provided by the bump step
else
  VERSION_NAME=$(grep -E 'versionName\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
  VERSION_CODE=$(grep -E 'versionCode\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/[^0-9]//g')
fi

TAG_NAME="v${VERSION_NAME}"
RELEASE_NAME="DailyDash ${VERSION_NAME} (build ${VERSION_CODE})"

if [ -n "${INPUT_NOTES:-}" ]; then
  NOTES="$INPUT_NOTES"
else
  NOTES=$(printf '%s\n' \
    "$RELEASE_NAME" \
    "" \
    "Tester build signed with the shared keystore." \
    "Installs as an in-app update when versionCode is higher.")
fi

if [ ! -f "$APK_SRC" ]; then
  echo "Release APK not found at $APK_SRC" >&2
  find app/build/outputs/apk -type f -name '*.apk' 2>/dev/null || true
  exit 1
fi

APK_NAME="DailyDash-${VERSION_NAME}-vc${VERSION_CODE}.apk"
cp "$APK_SRC" "$APK_NAME"

{
  echo "tag=${TAG_NAME}"
  echo "name=${RELEASE_NAME}"
  echo "apk=${APK_NAME}"
  echo "version_name=${VERSION_NAME}"
  echo "version_code=${VERSION_CODE}"
  echo "notes<<EOF"
  echo "$NOTES"
  echo "EOF"
} >> "$GITHUB_OUTPUT"

echo "Packaged ${APK_NAME}"

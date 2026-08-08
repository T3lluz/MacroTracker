#!/usr/bin/env bash
# Bump versionCode/versionName when the current build was already released.
# Required for in-app updates (higher versionCode) and unique GitHub release tags.
set -euo pipefail

GRADLE_FILE="app/build.gradle.kts"

read_version() {
  VERSION_NAME=$(grep -E 'versionName\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
  VERSION_CODE=$(grep -E 'versionCode\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/[^0-9]//g')
}

next_name() {
  local base="$1" code="$2"
  if [[ "$base" =~ ^([0-9]+\.[0-9]+\.)([0-9]+)$ ]]; then
    echo "${BASH_REMATCH[1]}${code}"
  else
    echo "${base}-${code}"
  fi
}

apk_released() {
  local code="$1"
  gh api "repos/${GITHUB_REPOSITORY}/releases?per_page=100" \
    --jq "[.[].assets[].name | select(test(\"^DailyDash-.*-vc${code}\\\\.apk$\"; \"i\"))] | length"
}

tag_taken() {
  local name="$1"
  git rev-parse "refs/tags/v${name}" >/dev/null 2>&1 \
    || gh release view "v${name}" >/dev/null 2>&1
}

git fetch --tags --force origin
read_version

if tag_taken "$VERSION_NAME" || [ "$(apk_released "$VERSION_CODE")" -gt 0 ]; then
  NEW_CODE=$((VERSION_CODE + 1))
  NEW_NAME="$(next_name "$VERSION_NAME" "$NEW_CODE")"

  while tag_taken "$NEW_NAME" || [ "$(apk_released "$NEW_CODE")" -gt 0 ]; do
    NEW_CODE=$((NEW_CODE + 1))
    NEW_NAME="$(next_name "$VERSION_NAME" "$NEW_CODE")"
  done

  sed -i -E "s/(versionCode\\s*=\\s*)${VERSION_CODE}/\\1${NEW_CODE}/" "$GRADLE_FILE"
  sed -i -E "s/(versionName\\s*=\\s*\")${VERSION_NAME}(\")/\\1${NEW_NAME}\\2/" "$GRADLE_FILE"

  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git add "$GRADLE_FILE"
  git commit -m "chore: bump version to ${NEW_NAME} (vc${NEW_CODE}) [skip ci]"
  git push origin HEAD:master

  VERSION_NAME="$NEW_NAME"
  VERSION_CODE="$NEW_CODE"
  echo "Auto-bumped to ${VERSION_NAME} (vc${VERSION_CODE})"
else
  echo "Using ${VERSION_NAME} (vc${VERSION_CODE})"
fi

{
  echo "version_name=${VERSION_NAME}"
  echo "version_code=${VERSION_CODE}"
  echo "commit_sha=$(git rev-parse HEAD)"
} >> "$GITHUB_OUTPUT"

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
RELEASE_NAME="DailyDash ${VERSION_NAME}"
COMMIT_SHA="${COMMIT_SHA:-$(git rev-parse HEAD)}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

clean_title() {
  local title="$1"
  title="${title#QA: }"
  title="$(printf '%s' "$title" | sed -E 's/^\[([^]]+)\][[:space:]]*//; s/[[:space:]]+/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')"
  if [ -z "$title" ]; then
    title="Update"
  fi
  printf '%s' "$title"
}

is_noise_title() {
  local title="$1"
  local lower="${title,,}"
  if [[ "$lower" == chore:\ bump\ version* ]]; then return 0; fi
  if [[ "$lower" == *"[skip ci]"* ]]; then return 0; fi
  if [[ "$lower" == merge\ pull\ request* || "$lower" == merge\ branch* ]]; then return 0; fi
  if [[ "$lower" == bumped\ version* || "$lower" == update\ version* ]]; then return 0; fi
  return 1
}

add_bullet() {
  local title="$1"
  local url="${2:-}"
  title="$(clean_title "$title")"
  if is_noise_title "$title"; then
    return
  fi
  if [ -n "$url" ]; then
    bullets+=("- [${title}](${url})")
  else
    bullets+=("- ${title}")
  fi
}

build_release_notes() {
  if [ -n "${INPUT_NOTES:-}" ]; then
    printf '%s\n' "$INPUT_NOTES"
    return
  fi

  local prev=""
  prev="$(git tag -l 'v*' --sort=-v:refname | grep -v "^${TAG_NAME}$" | head -1 || true)"

  local api_args=(
    -f "tag_name=${TAG_NAME}"
    -f "target_commitish=${COMMIT_SHA}"
  )
  if [ -n "$prev" ]; then
    api_args+=(-f "previous_tag_name=${prev}")
  fi

  local raw=""
  if raw="$(gh api "repos/${REPO}/releases/generate-notes" "${api_args[@]}" --jq .body 2>/dev/null)"; then
    :
  else
    raw=""
  fi

  bullets=()
  local line title url
  while IFS= read -r line; do
    if [[ "$line" =~ ^\*\ (.+)\ by\ @[^[:space:]]+\ in\ (https://github\.com/[^[:space:]]+/pull/[0-9]+)$ ]]; then
      add_bullet "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
    elif [[ "$line" =~ ^[-*]\ \[([^]]+)\]\((https://github\.com/[^[:space:]]+/pull/[0-9]+)\) ]]; then
      add_bullet "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
    fi
  done <<< "$raw"

  if [ "${#bullets[@]}" -eq 0 ] && [ -n "$prev" ]; then
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      add_bullet "$line"
    done < <(git log --no-merges --pretty=format:'%s' "${prev}..${COMMIT_SHA}" | head -20)
  fi

  # Deduplicate while preserving order.
  if [ "${#bullets[@]}" -gt 0 ]; then
    local deduped=()
    local b seen
    for b in "${bullets[@]}"; do
      seen=0
      for d in "${deduped[@]+"${deduped[@]}"}"; do
        if [ "$d" = "$b" ]; then seen=1; break; fi
      done
      if [ "$seen" -eq 0 ]; then
        deduped+=("$b")
      fi
    done
    bullets=("${deduped[@]}")
  fi

  {
    echo "<!-- dailydash-version: ${VERSION_NAME} vc${VERSION_CODE} -->"
    echo "## What's new"
    if [ "${#bullets[@]}" -gt 0 ]; then
      printf '%s\n' "${bullets[@]}"
    else
      echo "- Stability and polish improvements"
    fi
    echo
    echo "[View release on GitHub](https://github.com/${REPO}/releases/tag/${TAG_NAME})"
  }
}

# Shallow CI checkouts omit tags; notes need the previous v* tag (and git-log
# fallback needs enough history to walk prev..HEAD).
git fetch --tags --force origin >/dev/null 2>&1 || true
if ! git rev-parse --verify --quiet HEAD^ >/dev/null; then
  git fetch --deepen=80 origin >/dev/null 2>&1 || true
fi

NOTES="$(build_release_notes)"

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
echo "----- notes -----"
echo "$NOTES"

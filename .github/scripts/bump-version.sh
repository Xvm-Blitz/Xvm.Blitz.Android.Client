#!/usr/bin/env bash
set -euo pipefail

Bump="${1:-}"
if [[ -z "$Bump" ]]; then
  echo "Usage: $0 <patch|minor|major>" >&2
  exit 1
fi

case "$Bump" in
  patch|minor|major) ;;
  *)
    echo "Invalid bump type: $Bump (expected patch, minor, or major)" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
GRADLE_PATH="$REPO_ROOT/app/build.gradle.kts"

if [[ ! -f "$GRADLE_PATH" ]]; then
  echo "File not found: $GRADLE_PATH" >&2
  exit 1
fi

gradle_content="$(cat "$GRADLE_PATH")"

if [[ ! "$gradle_content" =~ versionName[[:space:]]*=[[:space:]]*\"([0-9]+)\.([0-9]+)\.([0-9]+)\" ]]; then
  echo "versionName not found in app/build.gradle.kts" >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"
current="${major}.${minor}.${patch}"

case "$Bump" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
esac

version_text="${major}.${minor}.${patch}"

if [[ ! "$gradle_content" =~ versionCode[[:space:]]*=[[:space:]]*([0-9]+) ]]; then
  echo "versionCode not found in app/build.gradle.kts" >&2
  exit 1
fi

new_version_code=$((BASH_REMATCH[1] + 1))

echo "Bumping version ($Bump): $current -> $version_text (versionCode $new_version_code)"

tmp="$(mktemp)"
sed -E \
  -e "s/versionName[[:space:]]*=[[:space:]]*\"[0-9]+\.[0-9]+\.[0-9]+\"/versionName = \"${version_text}\"/" \
  -e "s/versionCode[[:space:]]*=[[:space:]]*[0-9]+/versionCode = ${new_version_code}/" \
  "$GRADLE_PATH" > "$tmp"
mv "$tmp" "$GRADLE_PATH"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "VERSION=$version_text"
    echo "VERSION_CODE=$new_version_code"
  } >> "$GITHUB_OUTPUT"
fi

echo "New version: $version_text (versionCode $new_version_code)"

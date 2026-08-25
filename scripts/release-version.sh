#!/usr/bin/env bash
# Map a GitHub release tag (vMAJOR.MINOR.PATCH[-prerelease]) to Android
# versionName / versionCode. versionCode must stay greater than historical
# sideload APKs (versionCode 2) so devices accept in-place updates.
set -euo pipefail

self_test() {
  expect() {
    local tag="$1" want_name="$2" want_code="$3"
    local got
    got="$(parse_tag "$tag")"
    local got_name got_code
    got_name="$(echo "$got" | awk -F= '/^VERSION_NAME=/{print $2}')"
    got_code="$(echo "$got" | awk -F= '/^VERSION_CODE=/{print $2}')"
    if [[ "$got_name" != "$want_name" || "$got_code" != "$want_code" ]]; then
      echo "FAIL $tag: got name=$got_name code=$got_code (want $want_name / $want_code)" >&2
      exit 1
    fi
  }

  expect v1.0.1 1.0.1 1000001
  expect v1.0.2 1.0.2 1000002
  expect v2.3.41 2.3.41 2003041
  expect v1.2.3-rc.1 1.2.3-rc.1 1002003

  parse_tag v1.0.0 >/dev/null
  if parse_tag 1.0.2 >/dev/null 2>&1; then
    echo "FAIL: tag without v prefix should be rejected" >&2
    exit 1
  fi
  if parse_tag v1.0 >/dev/null 2>&1; then
    echo "FAIL: incomplete semver should be rejected" >&2
    exit 1
  fi
  if parse_tag v0.0.2 >/dev/null 2>&1; then
    echo "FAIL: versionCode 2 must be rejected (not greater than installed sideloads)" >&2
    exit 1
  fi

  echo "release-version self-test passed"
}

parse_tag() {
  local tag="${1:-}"
  if [[ ! "$tag" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)(-[A-Za-z0-9.-]+)?$ ]]; then
    echo "Invalid release tag '$tag'. Expected vMAJOR.MINOR.PATCH (optional -prerelease)." >&2
    return 1
  fi

  local major="${BASH_REMATCH[1]}"
  local minor="${BASH_REMATCH[2]}"
  local patch="${BASH_REMATCH[3]}"
  local suffix="${BASH_REMATCH[4]:-}"

  if (( major > 2100 || minor > 999 || patch > 999 )); then
    echo "Version component out of range for versionCode (major<=2100, minor/patch<=999)." >&2
    return 1
  fi

  local version_name="${major}.${minor}.${patch}${suffix}"
  local version_code=$((major * 1000000 + minor * 1000 + patch))

  # Sideloaded debug APKs shipped versionCode 2. GitHub release APKs must be higher.
  if (( version_code <= 2 )); then
    echo "versionCode $version_code from tag $tag must be greater than 2." >&2
    return 1
  fi

  echo "VERSION_NAME=${version_name}"
  echo "VERSION_CODE=${version_code}"
}

if [[ "${1:-}" == "--self-test" ]]; then
  self_test
  exit 0
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <tag>|--self-test" >&2
  echo "  tag must match vMAJOR.MINOR.PATCH with optional -prerelease suffix" >&2
  exit 1
fi

parse_tag "$1"

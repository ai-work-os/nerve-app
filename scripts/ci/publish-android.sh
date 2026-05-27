#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_DIR}"

PUBLISH_ROOT="${NERVE_PUBLISH_ROOT:-/var/www/html}"
APK_URL="${NERVE_APK_URL:-http://100.75.43.90/nerve-app.apk}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
VERSION_FILE="${PUBLISH_ROOT}/nerve-app-version.json"
APK_TARGET="${PUBLISH_ROOT}/nerve-app.apk"

read_gradle_value() {
  local key="$1"
  sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*\"?([^\"[:space:]]+)\"?.*/\1/p" app/build.gradle.kts | head -n 1
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "${value}"
}

find_aapt() {
  if [[ -n "${AAPT:-}" && -x "${AAPT}" ]]; then
    printf '%s\n' "${AAPT}"
    return 0
  fi

  local sdk
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${HOME}/Android/Sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    [[ -n "${sdk}" && -d "${sdk}/build-tools" ]] || continue
    find "${sdk}/build-tools" -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n 1
  done | tail -n 1
}

install_file() {
  local source="$1"
  local target="$2"
  if [[ -w "$(dirname "${target}")" ]]; then
    install -m 0644 "${source}" "${target}"
  else
    sudo install -m 0644 "${source}" "${target}"
  fi
}

version_code="$(read_gradle_value versionCode)"
version_name="$(read_gradle_value versionName)"

if [[ -z "${version_code}" || -z "${version_name}" ]]; then
  echo "Failed to read versionCode/versionName from app/build.gradle.kts" >&2
  exit 1
fi

notes="${RELEASE_NOTES:-v${version_name}}"

./gradlew :app:assembleDebug

if [[ ! -f "${APK_PATH}" ]]; then
  echo "APK not found at ${APK_PATH}" >&2
  exit 1
fi

aapt="$(find_aapt)"
if [[ -z "${aapt}" || ! -x "${aapt}" ]]; then
  echo "aapt not found. Set AAPT or install Android SDK build-tools under ANDROID_HOME." >&2
  exit 1
fi

badging="$("${aapt}" dump badging "${APK_PATH}")"
package_line="${badging%%$'\n'*}"
apk_version_code="$(printf '%s\n' "${package_line}" | sed -nE "s/.*versionCode='([^']+)'.*/\1/p")"
apk_version_name="$(printf '%s\n' "${package_line}" | sed -nE "s/.*versionName='([^']+)'.*/\1/p")"

if [[ "${apk_version_code}" != "${version_code}" || "${apk_version_name}" != "${version_name}" ]]; then
  echo "APK version mismatch: gradle=${version_code}/${version_name}, apk=${apk_version_code}/${apk_version_name}" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

json_tmp="${tmp_dir}/nerve-app-version.json"
apk_tmp="${tmp_dir}/nerve-app.apk"
cp "${APK_PATH}" "${apk_tmp}"
mkdir -p app/build/outputs

cat > "${json_tmp}" <<JSON
{
  "versionCode": ${version_code},
  "versionName": "$(json_escape "${version_name}")",
  "url": "$(json_escape "${APK_URL}")",
  "notes": "$(json_escape "${notes}")"
}
JSON
cp "${json_tmp}" app/build/outputs/nerve-app-version.json

install_file "${apk_tmp}" "${APK_TARGET}"
install_file "${json_tmp}" "${VERSION_FILE}"

published_badging="$("${aapt}" dump badging "${APK_TARGET}")"
published_line="${published_badging%%$'\n'*}"
published_code="$(printf '%s\n' "${published_line}" | sed -nE "s/.*versionCode='([^']+)'.*/\1/p")"
published_name="$(printf '%s\n' "${published_line}" | sed -nE "s/.*versionName='([^']+)'.*/\1/p")"

if [[ "${published_code}" != "${version_code}" || "${published_name}" != "${version_name}" ]]; then
  echo "Published APK version mismatch: json=${version_code}/${version_name}, apk=${published_code}/${published_name}" >&2
  exit 1
fi

echo "Published ${APK_TARGET} and ${VERSION_FILE} (${version_code}/${version_name})"

#!/usr/bin/env bash
# Start the customer app in Expo Go on a running Android emulator, pointed at a local backend.
#
#   scripts/dev/run-mobile-local.sh                    # backend on :8080, Metro on :19000
#   BACKEND_PORT=8083 scripts/dev/run-mobile-local.sh  # backend on another port
#   PHYSICAL_DEVICE=1 scripts/dev/run-mobile-local.sh  # phone on the same Wi-Fi (uses this PC's LAN IP)
#
# The Android emulator reaches this PC as 10.0.2.2. Start the emulator first
# (Android Studio > Device Manager). Expo installs Expo Go on it if needed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-8080}"
METRO_PORT="${METRO_PORT:-19000}"   # 8081 (Expo's default) is often taken by other software

SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$SDK/emulator:$PATH"

if [[ "${PHYSICAL_DEVICE:-0}" == "1" ]]; then
  HOST="$(ipconfig 2>/dev/null | grep -m1 -oE 'IPv4[^:]*: *[0-9.]+' | grep -oE '[0-9.]+$' || hostname -I | awk '{print $1}')"
else
  HOST="10.0.2.2"
fi
export EXPO_PUBLIC_API_BASE_URL="${EXPO_PUBLIC_API_BASE_URL:-http://${HOST}:${BACKEND_PORT}}"

echo "API: ${EXPO_PUBLIC_API_BASE_URL}   Metro: ${METRO_PORT}"
cd "$ROOT/mobile"
if [[ -t 0 ]]; then
  # Interactive: Expo opens the app (and offers to install/upgrade Expo Go).
  exec npx expo start --android --port "$METRO_PORT"
fi
# Non-interactive (CI, background shells): Expo can't answer prompts, so start Metro
# and open the project in the Expo Go already on the emulator once Metro is up.
(
  for _ in $(seq 1 60); do
    curl -s -m 2 "http://localhost:${METRO_PORT}/status" | grep -q running && break
    sleep 2
  done
  LAN_IP="$(ipconfig 2>/dev/null | grep -m1 -oE 'IPv4[^:]*: *[0-9.]+' | grep -oE '[0-9.]+$' || echo 10.0.2.2)"
  adb shell am start -a android.intent.action.VIEW -d "exp://${LAN_IP}:${METRO_PORT}" >/dev/null 2>&1 || true
) &
exec npx expo start --port "$METRO_PORT"

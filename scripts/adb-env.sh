#!/usr/bin/env bash
# Source this file before running gradle/adb against the phone:
#   source scripts/adb-env.sh
#
# Exports JAVA_HOME / ANDROID_HOME, mDNS-discovers the phone's Wireless ADB
# connect endpoint, and runs `adb connect` if not already attached.
#
# The connect port rotates every time Wireless debugging is toggled or the
# phone reboots — that's why we discover via mDNS rather than hardcoding it.
# Override PHONE_HOST=<ip:port> if mDNS is blocked on your network.

export JAVA_HOME=/home/matt/.local/jdk/jdk-17.0.19+10
export ANDROID_HOME=/home/matt/.local/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if [ -z "${PHONE_HOST:-}" ]; then
    PHONE_HOST=$(adb mdns services 2>/dev/null \
        | awk '$2=="_adb-tls-connect._tcp" {print $3; exit}')
fi

if [ -z "$PHONE_HOST" ]; then
    echo "adb: no paired phone found via mDNS." >&2
    echo "  - Confirm Wireless debugging is on (Settings → Developer options)." >&2
    echo "  - Or set PHONE_HOST=<ip:port> from the Wireless debugging screen." >&2
    echo "  - Or re-pair: adb pair <ip:pair-port>  then re-source this file." >&2
    return 1 2>/dev/null || exit 1
fi

if ! adb devices | awk 'NR>1 {print $1}' | grep -qx "${PHONE_HOST}"; then
    echo "adb: connecting to ${PHONE_HOST}…"
    adb connect "${PHONE_HOST}" || {
        echo "adb: connect failed. Re-pair via the phone's Wireless debugging screen." >&2
        return 1 2>/dev/null || exit 1
    }
fi

adb devices

#!/usr/bin/env bash
# Source this file before running gradle/adb against a paired device:
#   source scripts/adb-env.sh
#
# Exports JAVA_HOME / ANDROID_HOME, mDNS-discovers paired Wireless ADB
# connect endpoints, and runs `adb connect`. If multiple devices are
# paired, presents an interactive menu and disconnects the rest so
# `installDebug` has a single transport. Non-interactive shells fall
# back to the first match.
#
# The connect port rotates every time Wireless debugging is toggled or
# the device reboots — that's why we discover via mDNS rather than
# hardcoding it. Override PHONE_HOST=<ip:port> if mDNS is blocked.

export JAVA_HOME=/home/matt/.local/jdk/jdk-17.0.19+10
export ANDROID_HOME=/home/matt/.local/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

_havencore_pick_host() {
    # Echoes the chosen host:port on stdout. Diagnostics go to stderr.
    if [ -n "${PHONE_HOST:-}" ]; then
        printf '%s\n' "$PHONE_HOST"
        return 0
    fi

    local services
    services=$(adb mdns services 2>/dev/null \
        | awk '$2=="_adb-tls-connect._tcp" {print $3}')

    [ -z "$services" ] && return 1

    local -a hosts=()
    local h
    while IFS= read -r h; do
        [ -n "$h" ] && hosts+=("$h")
    done <<< "$services"

    if [ "${#hosts[@]}" -eq 1 ]; then
        printf '%s\n' "${hosts[0]}"
        return 0
    fi

    if [ ! -t 0 ]; then
        echo "adb: multiple paired devices; using first (non-interactive shell)." >&2
        printf '%s\n' "${hosts[0]}"
        return 0
    fi

    # Connect to each so we can query its product model for the menu.
    for h in "${hosts[@]}"; do
        adb connect "$h" >/dev/null 2>&1 || true
    done

    echo "adb: multiple paired devices found:" >&2
    local i=1
    for h in "${hosts[@]}"; do
        local model
        model=$(adb -s "$h" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
        [ -z "$model" ] && model="(unknown)"
        printf '  %d) %-18s  %s\n' "$i" "$model" "$h" >&2
        i=$((i+1))
    done

    local choice
    read -r -p "Select device [1-${#hosts[@]}]: " choice
    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -gt "${#hosts[@]}" ]; then
        echo "adb: invalid selection." >&2
        return 1
    fi

    local chosen=${hosts[$((choice-1))]}
    # Drop the IP:port transports for the unselected devices so
    # `installDebug` doesn't see "more than one device".
    for h in "${hosts[@]}"; do
        [ "$h" = "$chosen" ] && continue
        adb disconnect "$h" >/dev/null 2>&1 || true
    done
    printf '%s\n' "$chosen"
}

PHONE_HOST=$(_havencore_pick_host)
_pick_status=$?
unset -f _havencore_pick_host

if [ $_pick_status -ne 0 ] || [ -z "$PHONE_HOST" ]; then
    echo "adb: no paired device selected." >&2
    echo "  - Confirm Wireless debugging is on (Settings → Developer options)." >&2
    echo "  - Or set PHONE_HOST=<ip:port> from the Wireless debugging screen." >&2
    echo "  - Or re-pair: adb pair <ip:pair-port>  then re-source this file." >&2
    unset _pick_status
    return 1 2>/dev/null || exit 1
fi
unset _pick_status

if ! adb devices | awk 'NR>1 {print $1}' | grep -qx "${PHONE_HOST}"; then
    echo "adb: connecting to ${PHONE_HOST}…"
    adb connect "${PHONE_HOST}" || {
        echo "adb: connect failed. Re-pair via the device's Wireless debugging screen." >&2
        return 1 2>/dev/null || exit 1
    }
fi

adb devices

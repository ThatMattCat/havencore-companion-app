# HavenCore Companion App

Android companion to [HavenCore](https://github.com/ThatMattCat/havencore) — the
self-hosted AI smart-home assistant. Eventually delivers in-app voice chat, the
Android default-assistant slot (long-press home / power), and push
notifications for autonomy briefings via UnifiedPush + ntfy.

This is **Phase 0**: settings + connectivity probe only. No chat UI, no voice,
no push yet.

## Status

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Repo scaffold, settings screen, `/api/conversations` health check | **here** |
| 1 | Text chat over `/ws/chat`, history list | planned |
| 2 | In-app voice (push-to-talk over STT/TTS HTTP) | planned |
| 3 | `VoiceInteractionService` — Android default-assistant slot | planned |
| 4 | Push via UnifiedPush + self-hosted ntfy | planned |
| 5 | Todo / shopping list (blocked on `todo.*` MCP work) | planned |

Master plan + architecture decisions live alongside the agent code.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- OkHttp 4.12 + kotlinx-serialization-json 1.7
- Jetpack DataStore (Preferences) for persisted settings
- minSdk 29 (Android 10) — earliest reliable `VoiceInteractionService` target
- compileSdk / targetSdk 35
- AGP 8.7.3, Gradle 8.10.2, JDK 17

## Build

Standard Android: open in **Android Studio Hedgehog or newer** (ships JDK 17
and SDK), or `./gradlew assembleDebug` with `JAVA_HOME` (JDK 17) and
`ANDROID_HOME` set. Output APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

## Day-to-day dev loop — Wireless ADB from a Linux build host

The primary workflow: build on a Linux server, push to the phone over
Wireless ADB on the LAN. `scripts/adb-env.sh` handles environment setup and
phone discovery; source it once per shell.

### One-time pairing (Android 11+)

1. Phone → Settings → Developer options → **Wireless debugging** → On
2. Tap **Pair device with pairing code** (the modal — _not_ the main screen's
   IP & Port; those are different). Note the pair `host:port` and 6-digit
   code.
3. From the build host:
   ```bash
   adb pair <pair-host:port>   # then enter the 6-digit code
   ```

Pairing persists across phone reboots; you only repeat it if the phone is
wiped or the server's adb keys change.

### Each shell session

```bash
source scripts/adb-env.sh                                     # PATHs + mDNS-discover + adb connect
./gradlew installDebug                                        # build + push to phone
adb shell am start -n ai.havencore.companion/.MainActivity    # launch
adb logcat | grep -i havencore                                # tail logs
```

The phone's _connect_ port (separate from the pair port) rotates every time
Wireless debugging is toggled or the phone reboots. The helper script
auto-discovers it via mDNS — no need to hardcode. If mDNS is blocked on your
network, override:

```bash
PHONE_HOST=10.0.0.115:39961 source scripts/adb-env.sh
```

### Build host toolchain

The helper script assumes:
- JDK 17 at `/home/matt/.local/jdk/jdk-17.0.19+10`
- Android SDK at `/home/matt/.local/android-sdk` (platform-tools,
  `platforms;android-35`, `build-tools;35.0.0`)
- Gradle 8.10.2 (also fetched by the wrapper)

Adjust paths in `scripts/adb-env.sh` if your install lives elsewhere.

## In-app

1. Enter your HavenCore agent URL (e.g. `http://havencore.local`,
   `http://10.0.0.20`, or whatever the nginx gateway publishes).
2. Optionally edit the device name (defaults to `Build.MODEL`).
3. Tap **Save** → tap **Test connection**.
4. Expect "Connected." with a conversation count. On failure the error
   string surfaces (timeout / refused / unknown host / HTTP code).

## Configuration

Phase 0 is LAN-only. Cleartext HTTP is permitted globally via
`network_security_config.xml`; remote access goes through Tailscale or a VPN.
Reverse-proxy auth (nginx Basic Auth, bearer token) is a follow-up tracked on
the master plan.

## License

LGPL-2.1 — matches the upstream HavenCore repo.

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

Open the project in **Android Studio Hedgehog or newer** (it ships JDK 17 and
the Android SDK). Gradle sync downloads everything else.

Or from the command line, with `JAVA_HOME` pointing at JDK 17 and
`ANDROID_HOME` set:

```bash
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Install + run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Open the app.
2. Enter your HavenCore agent's URL (e.g. `http://havencore.local`,
   `http://192.168.1.20`, or whatever your nginx gateway publishes).
3. Optionally edit the device name (defaults to `Build.MODEL`).
4. Tap **Save**.
5. Tap **Test connection** — expect "Connected." with a conversation count.
   On failure, the error string surfaces (timeout / refused / unknown host /
   HTTP code).

## Configuration

Phase 0 is LAN-only. Cleartext HTTP is permitted globally via
`network_security_config.xml`; remote access goes through Tailscale or a VPN.
Reverse-proxy auth (nginx Basic Auth, bearer token) is a follow-up tracked on
the master plan.

## License

LGPL-2.1 — matches the upstream HavenCore repo.

# CLAUDE.md

This file provides guidance to Claude Code when working in the HavenCore
companion app repo.

## What this is

A native Kotlin Android app — user-facing companion to
[HavenCore](https://github.com/ThatMattCat/havencore), the self-hosted AI
smart-home assistant. Eventually delivers in-app voice chat, the Android
default-assistant slot (long-press home / power), and push notifications via
UnifiedPush + ntfy. The agent itself lives in the sibling repo at
`/home/matt/code/havencore`.

**Phase 0 (settings + connectivity probe) and Phase 1 (text chat over
`/ws/chat` + history list with resume) are both complete and verified
end-to-end on a real phone.** Master plan at
`/home/matt/.claude/plans/we-should-have-a-functional-orbit.md`. Phase 0
acceptance plan at
`/home/matt/.claude/plans/we-previously-planned-stateful-tarjan.md`. Phase 1
acceptance plan at
`/home/matt/.claude/plans/replicated-spinning-sundae.md`.

Phase 2 (voice in-app — push-to-talk over `/api/stt/transcribe` and
`/api/tts/speak`) is the next phase.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- AGP 8.7.3, Gradle 8.10.2, JDK 17 (Eclipse Temurin)
- minSdk 29 / target 35 — earliest reliable `VoiceInteractionService`
- OkHttp 4.12 + kotlinx-serialization-json 1.7.3
- Navigation Compose 2.8.4 — three routes: `settings` / `history` /
  `chat?sessionId={sessionId}`
- Jetpack DataStore (Preferences) for persisted settings (incl.
  `last_session_id`, written on every WS `session` event)
- Manual DI (no Hilt) — `AppContainer` instantiated in `HavenCoreApp.onCreate()`
- `applicationId = ai.havencore.companion` (user owns `havencore.ai`)

Versions pinned in `gradle/libs.versions.toml`. Lint flags newer (AGP 9.x,
Compose BOM 2026.04.01) — bump deliberately, not blindly.

## Day-to-day dev loop — Wireless ADB

Build host is this Linux server. Test device is the user's Android phone on
the same LAN, paired once via Wireless debugging. The connect port rotates
each time Wireless debugging is toggled or the phone reboots; the helper
script discovers it via mDNS so most days you never touch IPs.

```bash
source scripts/adb-env.sh        # PATHs + mDNS-discover + adb connect
./gradlew installDebug           # build + push to phone in one step
adb shell am start -n ai.havencore.companion/.MainActivity
adb logcat | grep -i 'havencore\|ChatWs\|ChatVM'   # tail logs (Phase 1 tags)
```

Override discovery if mDNS is blocked: `PHONE_HOST=10.0.0.115:39961 source scripts/adb-env.sh`.

Pairing persists across reboots — only re-pair (`adb pair <ip:pair-port>`
with the 6-digit code from the phone) if the phone is wiped or the server's
adb keys change. Full one-time-pairing instructions in `README.md`.

If `adb devices` shows two transports for the same phone (mDNS alias plus a
stale `ip:port`), `adb disconnect <ip:port>` to drop the duplicate before
running `am start` / `installDebug` — otherwise adb refuses with "more than
one device/emulator".

## Toolchain locations on the build host

- JDK 17: `/home/matt/.local/jdk/jdk-17.0.19+10`
- Android SDK: `/home/matt/.local/android-sdk` (`platform-tools`,
  `platforms;android-35`, `build-tools;35.0.0`)
- Gradle 8.10.2: `/home/matt/.local/gradle/gradle-8.10.2` (also fetched by
  the wrapper)
- `local.properties` (gitignored) sets `sdk.dir`.

## Project layout

```
app/src/main/kotlin/ai/havencore/companion/
├── HavenCoreApp.kt              # Application class — manual DI container
├── MainActivity.kt              # single-activity Compose host (delegates to HavenNav)
├── data/
│   ├── ServerConfig.kt
│   └── SettingsRepository.kt    # DataStore<Preferences>: baseUrl, deviceName, lastSessionId
├── net/
│   ├── HavenCoreClient.kt       # shared OkHttp client builder
│   ├── ConversationsApi.kt      # GET /api/conversations probe (Phase 0)
│   ├── ChatProtocol.kt          # HavenJson + WS DTOs + REST DTOs + parseChatFrame
│   ├── ChatApi.kt               # history list + resume REST
│   └── ChatWsSession.kt         # WS supervisor with reconnect ladder
└── ui/
    ├── nav/HavenNav.kt          # NavHost + route table
    ├── settings/{SettingsScreen.kt, SettingsViewModel.kt}
    ├── history/{HistoryScreen.kt, HistoryViewModel.kt}
    ├── chat/
    │   ├── ChatScreen.kt
    │   ├── ChatViewModel.kt
    │   ├── ChatUiState.kt
    │   ├── ResumeMapper.kt      # OpenAI messages -> Turn list
    │   └── components/{UserBubble, AssistantTurnCard, ToolCallCard, ReasoningCard, MetricChips, SummaryResetDivider, ConnectionBanner}.kt
    └── theme/{Color.kt, Theme.kt, Type.kt}
```

Future surfaces per master plan: `voice/` (`VoiceInteractionService`),
`audio/` (mic capture, TTS playback), `push/` (UnifiedPush), `ui/todo/`.

## Backend the app talks to

The agent exposes its API on port 6002, fronted by nginx on port 80.
Currently consumed:

- `GET /api/conversations` — connectivity probe (Phase 0) and history list
  (Phase 1)
- `POST /api/conversations/{session_id}/resume` — Phase 1, hydrates prior
  messages in OpenAI format
- `WS /ws/chat` — Phase 1, see `docs/wire-protocol.md` for the framing
  details that aren't obvious from the master plan (lowercase event types,
  no `RESPONSE_CHUNK`, `session_id` honored only on first frame, etc.)

Phase 2 will add `POST /api/stt/transcribe`, `POST /api/tts/speak`, and
`GET /api/tts/voices`. The authoritative source for all of these is
`/home/matt/code/havencore/services/agent/selene_agent/api/`.

CORS is `*` and there is no auth on `/api/*` — the app is LAN-only; cleartext
is permitted globally via `network_security_config.xml`. Tighten when remote
access becomes real.

## Voice-friendly content

Anything the user may hear via TTS (assistant slot in Phase 3+) must avoid
emojis and special characters — TTS limitation enforced by HavenCore's system
prompt. Keep assistant-facing text plain. UI-only Compose strings can use any
unicode.

## Conventions

- **Commits**: Conventional Commits, mirroring the agent repo —
  `feat(chat):`, `fix(net):`, `docs:`, `chore:`. Same
  `Co-Authored-By: Claude` trailer on AI-assisted commits. Branch off `main`,
  squash-merge style.
- **Phased commit cadence**: when working through a plan file's numbered
  build sequence, do one step at a time — write files, run
  `./gradlew :app:assembleDebug` to confirm it builds, show the diff with a
  per-file summary, wait for explicit approval, then commit. Each commit is
  a clean rollback point. Bundle in-flight fixes (e.g. amending an earlier
  file) into the current commit and call them out in the diff summary
  rather than rewriting history.
- **No emojis in code or docs** unless explicitly requested.
- **License**: LGPL-2.1, mirroring havencore. Don't change without asking.

## Workflow gotchas

- First build on a fresh host pulls the AGP/Compose dependency graph
  (hundreds of MB). Subsequent Compose-only builds finish in <30s. Adding
  `material-icons-extended` (Phase 1) bumps the dep graph but doesn't change
  the per-build cost meaningfully.
- After phone reboots, re-source `scripts/adb-env.sh` — the connect port
  rotates.
- `installDebug` requires the phone to be unlocked when the activity launches
  or lifecycle starts can cancel.
- Lint warnings about newer dep versions are advisory — pinned versions are
  the validated set; bump deliberately and re-run `./gradlew assembleDebug`
  + `./gradlew :app:lintDebug` after.
- `summary_reset` cannot be triggered manually — the agent has no
  `/summarize` endpoint. It fires on idle-timeout sweep (we send `-1` to opt
  out) or on context-size threshold. Verify the renderer organically over
  long conversations or by spoofing a frame in `ChatVM` for one-shot UI
  checks.

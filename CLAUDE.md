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

**Phases 0 (settings + connectivity probe), 1 (text chat over `/ws/chat`
+ history list with resume), 2 (in-app push-to-talk voice over
`/api/stt/transcribe` and `/api/tts/speak`), 3 (default-assistant
slot via `VoiceInteractionService` — long-press home / power, lockscreen,
and `Intent.ACTION_ASSIST`), and 4 (UnifiedPush + ntfy push notifications
with deep-link tap-through to chat) are all complete and verified
end-to-end on a real phone.** Master plan at
`/home/matt/.claude/plans/we-should-have-a-functional-orbit.md`. Phase 0
acceptance plan at
`/home/matt/.claude/plans/we-previously-planned-stateful-tarjan.md`. Phase 1
acceptance plan at
`/home/matt/.claude/plans/replicated-spinning-sundae.md`. Phase 2
acceptance plan at
`/home/matt/.claude/plans/i-want-to-misty-wind.md`. Phase 3 acceptance
plan at `/home/matt/.claude/plans/i-want-to-rosy-finch.md`. Phase 4
acceptance plan at `/home/matt/.claude/plans/i-want-to-quiet-thunder.md`.

Phase 4 OEM gotcha (Samsung et al.): the *distributor* (ntfy app) holds
the long-lived socket to the ntfy server, not us — so background-app
limits hit ntfy first. Setup help in Settings directs users to exempt
ntfy from battery optimization; HavenCore exemption is belt-and-
suspenders only (broadcasts wake our cold-killed package via the
manifest-registered `PushReceiver` already). Phase 4 also pinned the
UnifiedPush connector at 3.0.10 deliberately — the 3.1+ line requires
a Kotlin stdlib bump beyond our pinned 2.0.21, which is its own
deliberate decision.

Phase 3 OEM gotcha (Samsung): the Digital Assistant role picker on
Samsung filters out a `VoiceInteractionService` whose package does not
also declare a `RecognitionService`, even when STT is proxied to a
server. `voice/HavenStubRecognitionService` is a no-op stub that exists
purely to satisfy that filter — every request fails fast with
`ERROR_RECOGNIZER_BUSY`. Same applies to the role-request intent path:
`RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` resolves on
Samsung but silently no-ops, so `DefaultAssistantHelper.pickerIntent()`
unconditionally sends users to `Settings.ACTION_VOICE_INPUT_SETTINGS`
instead.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- AGP 8.7.3, Gradle 8.10.2, JDK 17 (Eclipse Temurin)
- minSdk 31 / target 35 — Android 12+, lets `AudioManager.setCommunicationDevice()`
  be the only BT mic routing path (no `startBluetoothSco` fallback)
- OkHttp 4.12 + kotlinx-serialization-json 1.7.3
- Navigation Compose 2.8.4 — three routes: `settings` / `history` /
  `chat?sessionId={sessionId}`. Push tap-through deep-links into the
  third via a `pendingSessionId` `StateFlow` MainActivity owns and
  HavenNav consumes in a `LaunchedEffect`.
- UnifiedPush connector 3.0.10 — pinned to the Kotlin-2.0.21-compatible
  3.0.x patch line; 3.1+ requires Kotlin stdlib 2.2+
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
adb logcat | grep -i 'havencore\|ChatWs\|ChatVM\|MicRec\|TtsPlay\|Voice:VIS\|Voice:Sess\|Push:Recv\|Push:Reg\|Push:Api'   # tail logs
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
├── HavenCoreApp.kt              # Application class — manual DI container (incl. appContext, push channel registration)
├── MainActivity.kt              # single-activity Compose host (delegates to HavenNav); onNewIntent feeds EXTRA_SESSION_ID into pendingSessionId StateFlow
├── data/
│   ├── ServerConfig.kt
│   └── SettingsRepository.kt    # DataStore<Preferences>: baseUrl, deviceName, lastSessionId, autoSpeak, default_assistant_prompt_seen, push_enabled, push_device_id, push_endpoint, push_distributor_pkg
├── audio/
│   ├── MicRecorder.kt           # AAC-in-MP4 capture with peak-amplitude polling for hasSpeech() gate
│   └── TtsPlayer.kt             # Media3 ExoPlayer wrapper for TTS playback
├── net/
│   ├── HavenCoreClient.kt       # shared OkHttp client builder
│   ├── ConversationsApi.kt      # GET /api/conversations probe (Phase 0)
│   ├── ChatProtocol.kt          # HavenJson + WS DTOs + REST DTOs + parseChatFrame
│   ├── ChatApi.kt               # history list + resume REST
│   ├── ChatWsSession.kt         # WS supervisor with reconnect ladder
│   ├── SttApi.kt                # POST /api/stt/transcribe multipart upload
│   ├── TtsApi.kt                # POST /api/tts/speak
│   └── PushApi.kt               # POST/DELETE /api/push/register (Phase 4)
├── push/                        # Phase 4 — UnifiedPush + ntfy
│   ├── PushChannel.kt                  # havencore_autonomy notification channel
│   ├── PushPayload.kt                  # @Serializable envelope + tolerant parser
│   ├── PushNotifier.kt                 # NotificationCompat builder; severity -> priority/vibration; tap PendingIntent w/ EXTRA_SESSION_ID
│   ├── PushReceiver.kt                 # MessagingReceiver — onMessage / onNewEndpoint / onRegistrationFailed / onUnregistered
│   ├── PushManager.kt                  # orchestrates UnifiedPush.register/unregister + agent register/deregister; PushUi sealed state
│   └── DeviceIdProvider.kt             # generate-or-read UUIDv4 in DataStore
├── voice/                       # Phase 3 — default-assistant slot
│   ├── HavenAssistService.kt           # VoiceInteractionService (logs only)
│   ├── HavenAssistSessionService.kt    # VoiceInteractionSessionService (factory)
│   ├── HavenAssistSession.kt           # VoiceInteractionSession — orchestrates round-trip
│   ├── HavenStubRecognitionService.kt  # no-op RecognitionService (Samsung filter satisfaction)
│   ├── DefaultAssistantHelper.kt       # RoleManager wrapper
│   ├── AssistUiState.kt                # Phase enum + state
│   ├── AssistLifecycleOwner.kt         # Lifecycle/SavedState/ViewModelStore shim for ComposeView
│   └── AssistOverlay.kt                # Compose bottom sheet
└── ui/
    ├── nav/HavenNav.kt          # NavHost + route table
    ├── settings/{SettingsScreen.kt, SettingsViewModel.kt}  # incl. DefaultAssistantBanner
    ├── history/{HistoryScreen.kt, HistoryViewModel.kt}
    ├── chat/
    │   ├── ChatScreen.kt        # incl. one-shot default-assistant prompt dialog
    │   ├── ChatViewModel.kt
    │   ├── ChatUiState.kt
    │   ├── ResumeMapper.kt      # OpenAI messages -> Turn list
    │   └── components/{UserBubble, AssistantTurnCard, ToolCallCard, ReasoningCard, MetricChips, SummaryResetDivider, ConnectionBanner}.kt
    └── theme/{Color.kt, Theme.kt, Type.kt}
```

Future surfaces per master plan: `ui/todo/`.

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
- `POST /api/stt/transcribe` — Phase 2, multipart audio upload
- `POST /api/tts/speak` — Phase 2, JSON body with text + voice/format/speed
- `POST /api/push/register`, `DELETE /api/push/register/{device_id}` —
  Phase 4, agent stores a `(device_id, device_label, endpoint)` row per
  registered device. Body is snake_case JSON (`device_id`, `device_label`,
  `endpoint`, `platform`); the agent's `NtfyNotifier` later POSTs the
  push payload directly to each row's `endpoint`.

Phase 3 reuses all of the above; the assist session opens its own
`ChatWsSession` so the foreground chat WS is not knocked off, but binds
to the same `lastSessionId` so voice-from-assist turns land in History
next to typed/voice-in-app turns. The authoritative source for the
agent's HTTP / WS surfaces is
`/home/matt/code/havencore/services/agent/selene_agent/api/`.

Phase 4 is **inbound-only** push: the agent fans out to registered
endpoints (URLs the user's UnifiedPush distributor produced), the ntfy
server forwards bytes byte-for-byte to the distributor, the distributor
broadcasts to our `PushReceiver`. The companion app does not run a
foreground service or hold its own socket — that's the architectural
advantage of UnifiedPush. Tap-through deep-links into
`chat?sessionId={sessionId}` if the payload includes `session_id`,
otherwise just foregrounds the app.

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

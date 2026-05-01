# CLAUDE.md

Guidance for Claude Code working in the HavenCore companion app repo.

## What this is

A native Kotlin Android app — user-facing companion to
[HavenCore](https://github.com/ThatMattCat/havencore), the self-hosted
AI smart-home assistant. The agent itself lives in the sibling repo at
`/home/matt/code/havencore`. The companion app currently delivers:

- Text chat over `/ws/chat` with a history list and resume
- In-app push-to-talk voice over `/api/stt/transcribe` and `/api/tts/speak`
- The Android default-assistant slot (long-press home / power,
  lockscreen, `Intent.ACTION_ASSIST`) via a `VoiceInteractionService`
- Inbound push notifications via UnifiedPush + ntfy with deep-link
  tap-through into chat

`README.md` is the user-facing description; this file is for working in
the codebase.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- AGP 8.7.3, Gradle 8.10.2, JDK 17 (Eclipse Temurin)
- minSdk 31 / target 35 — Android 12+, lets `AudioManager.setCommunicationDevice()`
  be the only BT mic routing path (no `startBluetoothSco` fallback)
- OkHttp 4.12 + kotlinx-serialization-json 1.7.3
- Navigation Compose 2.8.4 — three routes: `settings` / `history` /
  `chat?sessionId={sessionId}`
- UnifiedPush connector 3.0.10 — pinned to the Kotlin-2.0.21-compatible
  3.0.x patch line; 3.1+ requires Kotlin stdlib 2.2+
- Jetpack DataStore (Preferences) for persisted settings
- Manual DI (no Hilt) — `AppContainer` instantiated in `HavenCoreApp.onCreate()`
- `applicationId = ai.havencore.companion` (user owns `havencore.ai`)

Versions pinned in `gradle/libs.versions.toml`. Lint flags newer
(AGP 9.x, Compose BOM 2026.04.01) — bump deliberately, not blindly.

## Day-to-day dev loop — Wireless ADB

Build host is this Linux server. Test device is the user's Android
phone on the same LAN, paired once via Wireless debugging. The connect
port rotates each time Wireless debugging is toggled or the phone
reboots; the helper script discovers it via mDNS so most days you never
touch IPs.

```bash
source scripts/adb-env.sh        # PATHs + mDNS-discover + adb connect
./gradlew installDebug           # build + push to phone in one step
adb shell am start -n ai.havencore.companion/.MainActivity
adb logcat | grep -i 'havencore\|ChatWs\|ChatVM\|MicRec\|TtsPlay\|Voice:VIS\|Voice:Sess\|Push:Recv\|Push:Reg\|Push:Api'   # tail logs
```

Override discovery if mDNS is blocked:
`PHONE_HOST=10.0.0.115:39961 source scripts/adb-env.sh`.

Pairing persists across reboots — only re-pair (`adb pair <ip:pair-port>`
with the 6-digit code from the phone) if the phone is wiped or the
server's adb keys change. Full one-time-pairing instructions in
`README.md`.

If `adb devices` shows two transports for the same phone (mDNS alias
plus a stale `ip:port`), `adb disconnect <ip:port>` to drop the
duplicate before running `am start` / `installDebug` — otherwise adb
refuses with "more than one device/emulator".

## Toolchain locations on the build host

- JDK 17: `/home/matt/.local/jdk/jdk-17.0.19+10`
- Android SDK: `/home/matt/.local/android-sdk` (`platform-tools`,
  `platforms;android-35`, `build-tools;35.0.0`)
- Gradle 8.10.2: `/home/matt/.local/gradle/gradle-8.10.2` (also fetched
  by the wrapper)
- `local.properties` (gitignored) sets `sdk.dir`.

## Project layout

```
app/src/main/kotlin/ai/havencore/companion/
├── HavenCoreApp.kt              # Application class — manual DI container
├── MainActivity.kt              # single-activity Compose host; onNewIntent feeds EXTRA_SESSION_ID into pendingSessionId StateFlow for push deep-links
├── data/
│   ├── ServerConfig.kt
│   ├── ThemeMode.kt             # System / Light / Dark enum (DataStore-persisted)
│   └── SettingsRepository.kt    # DataStore<Preferences>: baseUrl, deviceName, lastSessionId, autoSpeak, default_assistant_prompt_seen, push_*, silence_timeout_ms, dynamic_color, theme_mode
├── audio/
│   ├── MicRecorder.kt           # AAC-in-MP4 capture with peak-amplitude polling for hasSpeech() gate; exposes currentAmplitude for live endpointing
│   └── TtsPlayer.kt             # Media3 ExoPlayer wrapper for TTS playback
├── net/
│   ├── HavenCoreClient.kt       # shared OkHttp client builder
│   ├── ConversationsApi.kt      # GET /api/conversations probe + history list
│   ├── ChatProtocol.kt          # HavenJson + WS DTOs + REST DTOs + parseChatFrame
│   ├── ChatApi.kt               # history list + resume REST
│   ├── ChatWsSession.kt         # WS supervisor with reconnect ladder
│   ├── SttApi.kt                # POST /api/stt/transcribe multipart upload
│   ├── TtsApi.kt                # POST /api/tts/speak
│   └── PushApi.kt               # POST/DELETE /api/push/register
├── push/                        # UnifiedPush + ntfy
│   ├── PushChannel.kt                  # havencore_autonomy notification channel
│   ├── PushPayload.kt                  # @Serializable envelope + tolerant parser
│   ├── PushNotifier.kt                 # NotificationCompat builder; severity -> priority/vibration; tap PendingIntent w/ EXTRA_SESSION_ID
│   ├── PushReceiver.kt                 # MessagingReceiver — onMessage / onNewEndpoint / onRegistrationFailed / onUnregistered
│   ├── PushManager.kt                  # orchestrates UnifiedPush.register/unregister + agent register/deregister; PushUi sealed state
│   └── DeviceIdProvider.kt             # generate-or-read UUIDv4 in DataStore
├── voice/                       # default-assistant slot — see docs/voice-assist.md
│   ├── HavenAssistService.kt           # VoiceInteractionService
│   ├── HavenAssistSessionService.kt    # VoiceInteractionSessionService (factory)
│   ├── HavenAssistSession.kt           # round-trip orchestrator + silence watcher
│   ├── HavenStubRecognitionService.kt  # no-op RecognitionService (Samsung filter satisfaction)
│   ├── DefaultAssistantHelper.kt       # RoleManager wrapper
│   ├── AssistUiState.kt                # Phase enum + state
│   ├── AssistLifecycleOwner.kt         # Lifecycle/SavedState/ViewModelStore shim for ComposeView
│   ├── AssistOverlay.kt                # bottom-sheet Compose surface
│   └── AssistVisualizers.kt            # phase-specific 120.dp hero composables
└── ui/
    ├── nav/HavenNav.kt          # NavHost + route table
    ├── settings/{SettingsScreen.kt, SettingsViewModel.kt}
    ├── history/{HistoryScreen.kt, HistoryViewModel.kt}
    ├── chat/
    │   ├── ChatScreen.kt
    │   ├── ChatViewModel.kt    # exposes micAmplitude flow for input-bar halo
    │   ├── ChatUiState.kt
    │   ├── ResumeMapper.kt      # OpenAI messages -> Turn list
    │   └── components/{UserBubble, AssistantTurnCard, ToolCallCard (ToolCallRow), ReasoningCard (ReasoningRow), MetricChips, SummaryResetDivider, AutoSpeakToggle, MicButton}.kt
    ├── components/             # canonical recipes — see docs/design-system.md
    │   ├── HeroDisc.kt          # 120 dp hero + AccentDisc (32 dp inline leading icon)
    │   ├── StatusPill.kt
    │   ├── Banner.kt            # severity-driven inline status row
    │   ├── States.kt            # EmptyState / LoadingState / ErrorState
    │   ├── AnimatedSwap.kt      # phase-swap recipe (fade + rise + size)
    │   └── BottomSheetSurface.kt
    └── theme/{Color.kt, Theme.kt, Type.kt, Tokens.kt, Shapes.kt}  # Quiet Tech identity; see docs/design-system.md
```

## Backend the app talks to

The agent exposes its API on port 6002, fronted by nginx on port 80.
Currently consumed:

- `GET /api/conversations` — connectivity probe and history list
- `POST /api/conversations/{session_id}/resume` — hydrates prior
  messages in OpenAI format
- `WS /ws/chat` — text turn channel; framing gotchas in
  `docs/wire-protocol.md` (lowercase event types, no `RESPONSE_CHUNK`,
  `session_id` honored only on first frame, etc.)
- `POST /api/stt/transcribe` — multipart audio upload
- `POST /api/tts/speak` — JSON body with text + voice/format/speed
- `POST /api/push/register`, `DELETE /api/push/register/{device_id}` —
  agent stores a `(device_id, device_label, endpoint)` row per
  registered device; its `NtfyNotifier` later POSTs payloads directly
  to each row's `endpoint`

The assist overlay opens its own `ChatWsSession` so the foreground
chat WS is not knocked off, but binds to the same `lastSessionId` so
voice-from-assist turns land in History next to typed and voice-in-app
turns. The authoritative source for the agent's HTTP / WS surfaces is
`/home/matt/code/havencore/services/agent/selene_agent/api/`.

Push is **inbound-only**: the agent fans out to registered endpoints
(URLs the user's UnifiedPush distributor produced), the ntfy server
forwards bytes byte-for-byte to the distributor, the distributor
broadcasts to our `PushReceiver`. The companion app does not run a
foreground service or hold its own socket — that's the architectural
advantage of UnifiedPush, but it does mean OEM background-app limits
hit the *distributor* (ntfy app) first; battery-optimization exemption
guidance for the ntfy app lives in the Settings screen.

CORS is `*` and there is no auth on `/api/*` — the app is LAN-only;
cleartext is permitted globally via `network_security_config.xml`.
Tighten when remote access becomes real.

## Reference docs

- `docs/wire-protocol.md` — WS framing + REST + STT/TTS + push payload
  gotchas. The agent source is the source of truth, but this captures
  the small set of facts that aren't obvious from skimming agent
  docstrings.
- `docs/voice-assist.md` — assist-slot architecture: overlay UI design,
  phase → visualizer mapping, auto-endpointing silence watcher, and the
  Samsung-specific gotchas (`RecognitionService` stub, role-request
  intent fallback to `Settings.ACTION_VOICE_INPUT_SETTINGS`).
- `docs/design-system.md` — color / typography / shape / spacing /
  motion / elevation tokens, component patterns, vibe-shift recipes,
  and the pre-merge UI checklist. Read before any UI change.

## Design system

`docs/design-system.md` is the source of truth for the look and feel.
The voice assist overlay (`voice/AssistOverlay.kt` +
`voice/AssistVisualizers.kt`) is the visual reference; every other
surface should feel like its sibling.

Hard rules for new UI:

- Spacing, radius, motion, elevation, hero sizes come from
  `HavenTokens` (`ui/theme/Tokens.kt`). Color comes from
  `MaterialTheme.colorScheme`. Shape comes from `MaterialTheme.shapes`.
  Type comes from `MaterialTheme.typography`. Hardcoded `dp` literals
  only inside `ui/theme/` or as one-off pixel-precise visual constants
  with a justifying comment. No `Color(0x...)` literal outside
  `ui/theme/Color.kt`.
- Before adding a new card / banner / pill / state surface, check
  `ui/components/` first — most patterns are already named. The
  canonical recipes (`StatusPill`, `AnimatedSwap`, `HeroDisc`,
  `EmptyState` / `LoadingState` / `ErrorState`, `Banner`,
  `BottomSheetSurface`) are listed in the design doc.
- Walk the pre-merge UI checklist at the bottom of
  `docs/design-system.md` before committing UI changes.

The token migration is staged. The foundation is in place:
`ui/theme/Tokens.kt`, `ui/theme/Shapes.kt`, the full
`HavenLightColors` / `HavenDarkColors` schemes in `Color.kt`, and the
wired `HavenCoreTheme` (color + shapes + typography). What's left is
migrating screens one at a time (Settings → History → Chat →
AssistOverlay) to consume these instead of literal `dp` / `Color(0x...)`
/ `RoundedCornerShape(N.dp)`. While migration is in flight, prefer
reading from `MaterialTheme` and `HavenTokens` over duplicating
literals — even in screens that haven't been migrated yet, new code
should follow the rules.

## Voice-friendly content

Anything the user may hear via TTS must avoid emojis and special
characters — TTS limitation enforced by HavenCore's system prompt.
Keep assistant-facing text plain. UI-only Compose strings can use any
unicode.

## Conventions

- **Commits**: Conventional Commits, mirroring the agent repo —
  `feat(chat):`, `fix(net):`, `docs:`, `chore:`. Same
  `Co-Authored-By: Claude` trailer on AI-assisted commits. Branch off
  `main`, squash-merge style.
- **No emojis in code or docs** unless explicitly requested.
- **License**: LGPL-2.1, mirroring havencore. Don't change without
  asking.
- **UI work**: read `docs/design-system.md` first — it covers tokens,
  component patterns, color roles, the pre-merge checklist, and the
  vibe-shift hooks. Adding a new screen, card, banner, pill, or state
  surface? Start there.

## Workflow gotchas

- First build on a fresh host pulls the AGP/Compose dependency graph
  (hundreds of MB). Subsequent Compose-only builds finish in <30s.
- After phone reboots, re-source `scripts/adb-env.sh` — the connect
  port rotates.
- `installDebug` requires the phone to be unlocked when the activity
  launches or lifecycle starts can cancel.
- Lint warnings about newer dep versions are advisory — pinned versions
  are the validated set; bump deliberately and re-run
  `./gradlew assembleDebug` + `./gradlew :app:lintDebug` after.
- `summary_reset` cannot be triggered manually — the agent has no
  `/summarize` endpoint. It fires on idle-timeout sweep (we send `-1`
  to opt out) or on context-size threshold. Verify the renderer
  organically over long conversations or by spoofing a frame in
  `ChatVM` for one-shot UI checks.

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
- Device-side actions: the agent emits `device_action` WS events
  alongside its normal `tool_call` / `tool_result` pair when it calls a
  device-targeted MCP tool. Wired actions cover an alarm intent
  (`set_alarm` → `AlarmClock.ACTION_SET_ALARM` against the user's Clock
  app) and a camera-capture+upload primitive shared by four MCP tools
  (`take_photo`, `identify_object_in_photo`, `read_text_from_image`,
  `who_is_in_view`) that bounce a JPEG back to the agent via
  `POST /api/companion/upload` so the LLM can see the image.
- Wall-display mode: opt-in foreground microphone service that runs an
  on-device "hey selene" wake-word detector (openWakeWord +
  Silero VAD), captures the post-wake utterance, and launches
  `MainActivity` in fullscreen kiosk mode so the chat path
  transcribes + sends without a user tap. Off by default; intended
  for a docked tablet. See `docs/wake-word.md`.

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

Build host is this Linux server. Test devices are the user's Android
phone and (for wall-display work) a docked tablet, each paired once via
Wireless debugging. The connect port rotates each time Wireless
debugging is toggled or a device reboots; the helper script discovers
paired devices via mDNS so most days you never touch IPs.

```bash
source scripts/adb-env.sh        # PATHs + mDNS-discover + adb connect
./gradlew installDebug           # build + push in one step
adb shell am start -n ai.havencore.companion/.MainActivity
adb logcat | grep -i 'havencore\|ChatWs\|ChatVM\|MicRec\|TtsPlay\|Voice:VIS\|Voice:Sess\|Push:Recv\|Push:Reg\|Push:Api\|WakeWord'   # tail logs
```

If multiple devices are paired (phone + wall-display tablet is the
common case), the script lists them by `ro.product.model` and prompts
for a pick in interactive shells, then disconnects the unselected
transports so `installDebug` sees a single device. Non-interactive
shells fall back to the first mDNS match — pin the target explicitly
with `PHONE_HOST=<ip:port> source scripts/adb-env.sh` to avoid pushing
to the wrong device. The same override unblocks mDNS-blocked networks.

Pairing persists across reboots — only re-pair (`adb pair <ip:pair-port>`
with the 6-digit code from the device) if it's wiped or the server's
adb keys change. Full one-time-pairing instructions in `README.md`.

If `adb devices` shows two transports for the same device (mDNS alias
plus a stale `ip:port`), `adb disconnect <ip:port>` to drop the
duplicate before running `am start` / `installDebug` — otherwise adb
refuses with "more than one device/emulator". The picker handles
*cross-device* duplicates; same-device duplicates still need a manual
disconnect.

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
│   ├── SettingsRepository.kt    # DataStore<Preferences>: baseUrl, deviceName, lastSessionId, autoSpeak, default_assistant_prompt_seen, push_*, silence_timeout_ms, dynamic_color, theme_mode, companion_camera_*_enabled, wall_display_enabled, wakeword_model_asset, wakeword_threshold_milli
│   └── DeviceAction.kt          # sealed DeviceAction with five variants (SetAlarm + TakePhoto / IdentifyObjectInPhoto / ReadTextFromImage / WhoIsInView sharing a CameraCapture sub-interface keyed by toolCallId) + DeviceActionResult; fromEvent maps ChatEvent.DeviceAction wire payload to typed action
├── device/
│   ├── DeviceActionDispatcher.kt # dispatches both fire-and-forget intents (e.g. AlarmClock.ACTION_SET_ALARM with FLAG_ACTIVITY_NEW_TASK + EXTRA_SKIP_UI) and the camera capture+upload round-trip; checks Settings toggles before launching CaptureActivity / posting to CompanionUploadApi
│   └── CaptureActivity.kt        # transparent ComponentActivity that fires the system camera intent, writes the JPEG into cacheDir/photos via FileProvider, and delivers the file to a per-tool_call_id callback registered by the dispatcher
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
│   ├── CompanionUploadApi.kt    # POST /api/companion/upload multipart (tool_call_id + device_id + JPEG); used by camera tools to round-trip a photo back to the agent
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
├── wakeword/                    # wall-display always-listening service — see docs/wake-word.md
│   ├── MicrophoneForegroundService.kt  # foreground mic service; owns controller; rewrites notification per phase; launches MainActivity w/ EXTRA_KIOSK + EXTRA_CAPTURE_PATH on a captured utterance
│   ├── WakeWordController.kt           # Listening <-> Capturing state machine + engine lifecycle + mic hand-off; SharedFlow<Event>: ListeningStarted / Detected / Captured / Failed / Stopped
│   ├── WakeCaptureSession.kt           # post-wake AudioRecord with warm-up + retry loop (S24 silent-handoff mitigation) and Silero VAD-driven endpointing; writes wake-*.wav
│   ├── SileroVad.kt                    # ONNX wrapper for silero_vad.onnx; auto-detects split-h/c vs unified-state layouts; exposes isDegraded
│   ├── PcmWavWriter.kt                 # streaming 16-bit mono PCM-16 with patched RIFF headers
│   └── WakeWordChannel.kt              # havencore_wakeword notification channel (IMPORTANCE_LOW)
└── ui/
    ├── nav/HavenNav.kt          # NavHost + route table
    ├── settings/{SettingsScreen.kt, SettingsViewModel.kt}
    ├── history/{HistoryScreen.kt, HistoryViewModel.kt}
    ├── chat/
    │   ├── ChatScreen.kt
    │   ├── ChatViewModel.kt    # exposes micAmplitude flow for input-bar halo; reduces ChatEvent.DeviceAction by dispatching the parsed action and appending a TurnEvent.DeviceActionItem
    │   ├── ChatUiState.kt       # TurnEvent.DeviceActionItem alongside ToolPair / Reasoning
    │   ├── ResumeMapper.kt      # OpenAI messages -> Turn list
    │   └── components/
    │       ├── UserBubble.kt
    │       ├── AssistantTurnCard.kt
    │       ├── ToolCallCard.kt           # ToolCallRow
    │       ├── ReasoningCard.kt          # ReasoningRow
    │       ├── DeviceActionCard.kt       # DeviceActionRow — display arms per DeviceAction variant
    │       ├── MetricChips.kt
    │       ├── SummaryResetDivider.kt
    │       ├── AutoSpeakToggle.kt
    │       └── MicButton.kt
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

The agent also emits `device_action` events on `/ws/chat` whose
companion-side handling lives in `data/DeviceAction.kt` +
`device/DeviceActionDispatcher.kt` + the `ChatViewModel` /
`HavenAssistSession` reducers. The agent enumerates which tool names
trigger a device-action event in a `DEVICE_ACTION_TOOLS` frozenset on
its `orchestrator.py`; the wire envelope is documented in
`docs/wire-protocol.md`.

The five wired actions split into two shapes. `set_alarm` is a
fire-and-forget Intent (`AlarmClock.ACTION_SET_ALARM`). The four camera
tools (`take_photo`, `identify_object_in_photo`, `read_text_from_image`,
`who_is_in_view`) all share one capture-and-upload primitive: the
dispatcher launches `device/CaptureActivity.kt`, awaits the JPEG via a
per-`tool_call_id` callback, then `net/CompanionUploadApi.upload` POSTs
the bytes to `/api/companion/upload` so the agent can resolve the
matching tool-call future and feed the resulting `image_url` back to
the LLM. Each camera tool is gated by its own `companion_camera_*_enabled`
Settings toggle (`take_photo` is the master gate; the other three
require master + their own switch). `DeviceActionResult` carries
camera-only states beyond `Fired` / `Failed` / `NoHandler` /
`Unsupported`: `InProgress`, `Uploading`, `Uploaded`, `Disabled`
(toggle off), and `Cancelled` (user backed out of the camera).

Adding a new action means: define the MCP tool agent-side, add it to
`DEVICE_ACTION_TOOLS`, add a `sealed DeviceAction` variant + parser
branch + dispatcher branch + a display-row arm in `DeviceActionCard.kt`
here.

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
- `docs/wake-word.md` — wall-display always-listening service:
  openwakeword + Silero VAD stack, ONNX asset layout, the
  `Listening ↔ Capturing` state machine, the Galaxy S24 silent-handoff
  workaround, notification narration as the on-device diagnostic
  surface, diagnostic log tags, and the kiosk activity launch
  contract. Read before touching anything under `wakeword/` or the
  wake hand-off in `HavenNav` / `ChatViewModel.ingestWakeCapture`.
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

Tokens live in `ui/theme/`: `Tokens.kt` (spacing, motion, elevation,
hero sizes), `Shapes.kt`, and `Color.kt` (the full
`HavenLightColors` / `HavenDarkColors` schemes plus the wired
`HavenCoreTheme` for color + shapes + typography). Screens consume
them via `MaterialTheme` (color, shapes, typography) and `HavenTokens`
(spacing, motion, elevation, hero sizes). Hardcoded `dp` literals
belong only inside `ui/theme/` or as one-off pixel-precise constants
with a justifying comment; `Color(0x...)` belongs only in
`ui/theme/Color.kt`. The pre-merge UI checklist in
`docs/design-system.md` is the enforcement mechanism.

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
- After a device reboots, re-source `scripts/adb-env.sh` — the connect
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

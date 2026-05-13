# Wake-word + wall-display mode

A second voice entry point: a foreground microphone service that
continuously listens for "hey selene" via openWakeWord and, on
detection, captures the user's request, opens chat in fullscreen
kiosk mode, transcribes the utterance, and sends it on the same
`/ws/chat` socket the rest of the app uses.

Opt-in via **Settings → Wall-display mode → Enable wall-display mode**.
Default off — phone users get the unchanged behavior. Intended for a
docked tablet (Lenovo Tab M11 in our case) that lives on a shelf and
serves as a room satellite. Works on a phone for testing.

For the assist-slot overlay (long-press home / lockscreen, one-shot,
not always-on) see [`voice-assist.md`](voice-assist.md). The two
surfaces are independent: assist is a system-initiated round trip,
wall-display is an always-listening service rooted in our own
foreground process.

## Architecture at a glance

```
┌─ MicrophoneForegroundService ──────────────────────────────────────┐
│  (foreground service, microphone type)                             │
│                                                                    │
│  ┌─ WakeWordController ────────────────────────────────────────┐   │
│  │  state machine:  Listening ──▶ Capturing ──▶ Listening      │   │
│  │                                                             │   │
│  │  ┌─ WakeWordEngine ─┐         ┌─ WakeCaptureSession ─┐      │   │
│  │  │ openwakeword lib │ ◀── 1 ──│ AudioRecord(MIC)     │      │   │
│  │  │ owns AudioRecord │   ─ 2 ─▶│ + SileroVad endpoint │      │   │
│  │  │ on MIC source    │         │ ─▶ wake-*.wav        │      │   │
│  │  └──────────────────┘         └──────────────────────┘      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  emits notification text per phase:                                │
│    "Listening for 'hey selene'"                                    │
│    "Detected (score=0.71). Capturing…"                             │
│    "Captured 2211ms. Opening chat…"                                │
│    "Failed at capture: silent_audio_after_handoff"                 │
└─────────────────────┬──────────────────────────────────────────────┘
                      │ Intent(MainActivity, EXTRA_KIOSK,
                      │        EXTRA_CAPTURE_PATH=/cache/wake-*.wav)
                      ▼
┌─ MainActivity ─────────────────────────────────────────────────────┐
│  setTheme(Theme_HavenCoreCompanion_Kiosk) before super.onCreate    │
│  applyKioskWindow(): fullscreen + show-when-locked + screen-on     │
│  consumeWakeExtras: pendingWakeCapture.value = path                │
└─────────────────────┬──────────────────────────────────────────────┘
                      ▼
┌─ HavenNav ─────────────────────────────────────────────────────────┐
│  LaunchedEffect(pendingCapture):                                   │
│    nav.navigate("chat?sessionId=$lastSid") { launchSingleTop=true }│
└─────────────────────┬──────────────────────────────────────────────┘
                      ▼
┌─ ChatScreen / ChatViewModel ───────────────────────────────────────┐
│  vm.ingestWakeCapture(file)                                        │
│  → runTranscribeAndSend: POST /api/stt/transcribe (multipart wav)  │
│  → awaitWsReady (status.first { Connected } w/ 8 s bound)          │
│  → sendMessage(transcript, fromVoice = true)                       │
└────────────────────────────────────────────────────────────────────┘
```

`1`: `WakeWordEngine` holds an `AudioRecord` while listening — the lib
owns it; we don't read raw frames.
`2`: on detection, the engine releases the mic, `WakeCaptureSession`
opens its own `AudioRecord` for the post-wake utterance. Single-owner
mic — see the [audio-handoff gotcha](#galaxy-s24-silent-handoff) below.

## Files

- `wakeword/MicrophoneForegroundService.kt` — `Service` with
  `FOREGROUND_SERVICE_TYPE_MICROPHONE`. Owns the controller, posts the
  persistent notification, rewrites the notification body per
  controller event, launches `MainActivity` with `EXTRA_KIOSK` +
  `EXTRA_CAPTURE_PATH` on a captured utterance.
- `wakeword/WakeWordController.kt` — state machine
  (`Listening` ↔ `Capturing`) + engine lifecycle + mic hand-off
  orchestration. Exposes a `SharedFlow<Event>` (`ListeningStarted`,
  `Detected`, `Captured`, `Failed`, `Stopped`) that the service
  observes.
- `wakeword/WakeCaptureSession.kt` — opens an `AudioRecord` after
  detection, runs the warm-up + retry loop that mitigates the Samsung
  silent-handoff bug, reads frames until Silero VAD reports sustained
  silence, writes a WAV. One call per detection.
- `wakeword/SileroVad.kt` — ONNX wrapper for `silero_vad.onnx`.
  Auto-detects split-h/c vs unified-state layouts. Exposes
  `isDegraded` so the capture loop can short-circuit if an inference
  fails irrecoverably.
- `wakeword/PcmWavWriter.kt` — streams 16-bit mono PCM into a WAV file,
  patches RIFF/data sizes on close (safe to interrupt mid-capture).
- `wakeword/WakeWordChannel.kt` — registers the `havencore_wakeword`
  notification channel (`IMPORTANCE_LOW`, no sound or vibration).
- `HavenCoreApp.maybeStartWakeWordService()` — autostart hook in
  `Application.onCreate`; starts the service on cold boot if the
  toggle was already on. The OS restarts our process across reboots,
  so this is sufficient without a `BOOT_COMPLETED` receiver.
- `ui/settings/SettingsViewModel.setWallDisplayEnabled` — toggle
  handler. Writes the DataStore flag and calls
  `ContextCompat.startForegroundService` (or `stopService` with
  `ACTION_STOP`) so the change takes effect immediately.
- `data/SettingsRepository` — keys `wall_display_enabled`,
  `wakeword_model_asset` (defaults to `wakeword/hey_selene.onnx`),
  `wakeword_threshold_milli` (defaults to `420` → 0.420f probability).

The chat-side hand-off path:
- `MainActivity.consumeWakeExtras` reads `EXTRA_KIOSK` and
  `EXTRA_CAPTURE_PATH` (cold-start via `onCreate`, warm via
  `onNewIntent`) and writes them into `pendingWakeCapture` /
  `applyKioskWindow`.
- `ui/nav/HavenNav` — the wake-handoff `LaunchedEffect` keys on
  `pendingCapture` alone, bails on null, and uses `launchSingleTop`
  without `popUpTo` (see [the ChatViewModel-duplication
  gotcha](#duplicate-chatviewmodel-on-the-wake-handoff) below).
- `ui/chat/ChatViewModel.ingestWakeCapture` — public entry point for
  the foreground service hand-off; mirrors the PTT path's
  state-machine but reads a pre-recorded WAV instead of the
  `MicRecorder` stream. `runTranscribeAndSend` awaits the WS handshake
  before calling `sendMessage`.

## ONNX asset layout

Four ONNX files; the openwakeword-android-kt library hard-codes the
front-end paths at the AssetManager root, while our own paths are
under `assets/wakeword/`:

```
app/src/main/assets/
├── embedding_model.onnx        openWakeWord front-end embedding model
├── melspectrogram.onnx         openWakeWord front-end feature model
└── wakeword/
    ├── hey_selene.onnx         custom-trained classifier (sigmoid in graph)
    └── silero_vad.onnx         end-of-utterance VAD (snakers4/silero-vad)
```

All four are `.gitignore`d; `assets/wakeword/.gitkeep` documents the
expected layout. The default model is `hey_selene.onnx` (phase 1.5
custom-trained from the openWakeWord pipeline — synthetic Kokoro
positives + Piper-generated hard negatives, 100% precision / 96.7%
recall against held-out synth eval). Swap the active model via
`SettingsRepository.setWakeWordModelAsset()`; the rest of the pipeline
is the same. Without the four files in place,
`MicrophoneForegroundService` logs `Failed at engine_start: …` and
stops itself, the rest of the app is unaffected.

## Threshold

Default 0.420 in probability space. The hey_selene ONNX export bakes
a Sigmoid node in as the final op (matching the openWakeWord /
Home Assistant / wyoming-openwakeword convention), so the lib's
`raw output > threshold` compare operates directly on a probability
— no client-side conversion needed. Settings stores it as
milli-units (`wakeword_threshold_milli`, default `420`) to avoid a
`floatPreferencesKey`. Tune downward after the first field session;
synth eval recall is optimistic vs. real-room conditions.

Exposed as a Slider in the wall-display Settings card (`ui/settings/
SettingsScreen.kt` `WallDisplayCard`). Range 0.15–0.80, step 0.05.
The slider commits to DataStore only on `onValueChangeFinished` so
the engine doesn't restart on every drag tick.
`MicrophoneForegroundService.runControllerLifecycle` observes the
`wakeWordThresholdFlow` via `collectLatest` and rebuilds the
`WakeWordController` on each new value — the threshold is baked into
`WakeWordController.Config` at construction so a fresh controller is
the cleanest way to apply the change. Teardown awaits the controller's
`Event.Stopped` (with a 500 ms timeout cap) before the next rebuild,
to avoid the new engine racing the old one for the mic.

Score-to-volume coupling is steep — openWakeWord confidence varies
with input loudness, so a phrase that scores 0.95 spoken close-mic can
score 0.30–0.45 spoken from across a room. Tune per device geometry:
handheld near-field stays fine at 0.42, a wall-mounted tablet at
viewing distance typically needs 0.25–0.30. The slider lets you
calibrate without a rebuild. See `memory/wakeword_gain_options.md`
for the open question of adding actual mic-gain control (deferred —
the openwakeword lib owns the AudioRecord end-to-end, so this needs
either reflection-based AGC/NoiseSuppressor attachment or a fork).

## Capture endpointing

`WakeCaptureSession` reads 16 kHz mono PCM-16 from
`MediaRecorder.AudioSource.MIC` — same source the wake-word engine
uses, on purpose (see the [audio-handoff gotcha](#galaxy-s24-silent-handoff)).
Per-frame size is 512 samples (32 ms), matching the Silero v1/v5 frame
size for 16 kHz.

| Stage          | Threshold / count                  | Why                                                       |
|----------------|------------------------------------|-----------------------------------------------------------|
| Speech latch   | `MIN_SPEECH_FRAMES = 5` (~150 ms)  | Don't arm silence countdown on a single false-positive frame |
| Silence end    | `SILENCE_FRAMES_TO_END = 22` (~700 ms) | Natural utterance cadence; enough to skip word-internal pauses |
| Hard cap       | `MAX_CAPTURE_MS = 10_000`          | "Never spoke" / "spoke forever" safety net                |
| VAD threshold  | `DEFAULT_SPEECH_THRESHOLD = 0.5f`  | Silero probability cutoff                                 |
| Silent-handoff | `SILENT_PEAK_THRESHOLD = 8` peak   | Below this the audio HAL is producing dead samples        |

The session keeps `(file, durationMs, hadSpeech, totalFrames,
speechFrames)` on success. `hadSpeech=false` (latch never armed) means
the service still emits `Captured` but logs and surfaces a notification
"Captured Xms — no speech detected. Listening again." rather than
opening chat with an empty WAV.

## Notification narration

The persistent notification (channel: `havencore_wakeword`,
`IMPORTANCE_LOW`, ongoing) is the primary on-device diagnostic
surface. `MicrophoneForegroundService.updateNotification(text)`
rebuilds the same `NotificationCompat.Builder` with new body text and
re-posts under the same notification id, gated by `setOnlyAlertOnce` so
flips don't buzz the user.

| Controller event   | Notification body                                                  |
|--------------------|--------------------------------------------------------------------|
| `ListeningStarted` | `Listening for 'hey selene'`                                       |
| `Detected`         | `Detected (score=%.2f). Capturing…`                                |
| `Captured` (hasSpeech) | `Captured %dms. Opening chat…`                                 |
| `Captured` (no speech) | `Captured %dms — no speech detected. Listening again.`         |
| `Failed`           | `Failed at <stage>: <cause.message ?: cause::class.simpleName>`    |
| `Stopped`          | (no update — the service is going away)                            |

A `Failed` event with `stage == "engine_start"` (typically a missing
ONNX asset) also calls `stopSelf()`; the user has to fix the asset and
re-enable from Settings.

## Kiosk activity styling

Activated when the foreground service launches `MainActivity` with
`EXTRA_KIOSK=true`. The kiosk theme + window flags are one-way during
the activity's lifetime: once any wake fires we want fullscreen +
screen-on until the user leaves the activity. The launcher path (cold
start without `EXTRA_KIOSK`) stays with the default windowing.

- `res/values/themes.xml` → `Theme.HavenCoreCompanion.Kiosk` sets
  `android:windowFullscreen`, `android:windowLayoutInDisplayCutoutMode
  shortEdges`, `windowTranslucentStatus`,
  `windowTranslucentNavigation`. Applied dynamically via
  `setTheme(Theme_HavenCoreCompanion_Kiosk)` _before_
  `super.onCreate`.
- `MainActivity.applyKioskWindow(true)` runs imperatively: adds
  `FLAG_KEEP_SCREEN_ON`, hides system bars with the
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` controller (swipe-to-reveal),
  calls `setShowWhenLocked(true)` + `setTurnScreenOn(true)`.

Lock-task mode / true pinning, an always-on doc-mode reset
behavior, and removing the back-button affordance are out of scope
for the phone-side scaffold — they'll be added when the tablet
arrives.

## Diagnostic logging

Tail on the build host:

```bash
adb logcat | grep -iE 'WakeWord|ChatVM|ChatWs'
```

Three tag families cover the full path:

| Tag prefix         | Source                                 |
|--------------------|----------------------------------------|
| `WakeWord:Silero`  | VAD load + degradation                 |
| `WakeWord:Ctrl`    | Engine lifecycle, detections, heartbeat (every 5 s while listening) |
| `WakeWord:Capture` | Capture frame counts, latch state, retries on silent handoff |
| `WakeWord:Svc`     | Service-level event narration, activity launch |
| `ChatVM`           | Wake-hand-off chat-side path: `ingestWakeCapture`, `runSession`, `ws.connect`, status, transcribe, `awaitWsReady`, `sendMessage` |
| `ChatWs`           | WebSocket open / close / failure       |

A healthy capture-to-send trace looks like:

```
WakeWord:Ctrl wake detected hey_selene score=0.77
WakeWord:Svc detected hey_selene score=0.77
WakeWord:Capture capture done frames=73 speech=25 latched=true durMs=2211 file=wake-….wav
WakeWord:Svc captured wake-….wav durMs=2211 speech=true bytes=74796
ChatVM   ingestWakeCapture file=wake-….wav exists=true length=74796
ChatVM   runSession start sessionToResume=… baseUrl=http://…
ChatVM   ws.connect kicked off
ChatVM   ws status -> Connecting
ChatVM   transcribe start file=wake-….wav ct=audio/wav …
ChatWs   ws open url=ws://…/ws/chat
ChatVM   ws status -> Connected
ChatVM   transcribe done ms=1254 ok=true
ChatVM   transcribe text='Hey, what time is it?' len=21
ChatVM   awaitWsReady before=Connected ready=true waitMs=0
ChatVM   sendMessage fromVoice=true sent=true len=21
```

Two `runSession start` lines or a `ws failure: Socket closed`
mid-trace is the duplicate-ChatViewModel symptom (see below) and
means a regression — the hand-off should produce exactly one of
each.

## Gotchas

### Galaxy S24 silent handoff

When the wake engine releases its `AudioRecord` and the capture
session opens a new one immediately, Galaxy S24 (and likely other
Samsung devices) routes the second recorder through a degraded audio
path: `STATE_INITIALIZED` returns OK and `read()` returns positive
frame counts, but every sample is zero. The capture WAV is silent,
the agent transcribes nothing, the user sees nothing happen.

Two mitigations stack:

1. **Match the audio source.** Both the wake engine and the capture
   session use `MediaRecorder.AudioSource.MIC`. The earlier draft
   used `VOICE_COMMUNICATION` for capture (to get AEC/AGC/NS); the
   transition between source types is what triggered the routing
   degradation. Matching them avoids the transition. We lose AEC/AGC/NS
   but at close range with a short utterance it's negligible.
2. **Warm-up + retry in `WakeCaptureSession.openMicWithWarmup`.**
   After `startRecording()`, read `WARMUP_FRAMES_PER_ATTEMPT = 4`
   frames into a scratch buffer and compute peak abs-amplitude. If
   peak `< SILENT_PEAK_THRESHOLD = 8` (well below the noise floor of
   any real mic), close + reopen `AudioRecord` and try again, up to
   `MAX_WARMUP_ATTEMPTS = 3` times with 75 ms between attempts.
   Non-silent warm-up frames are preserved in the WAV so the first
   phoneme isn't clipped.

If all three attempts come back silent, capture returns
`Result.failure(IllegalStateException("silent_audio_after_handoff"))`
and the notification body reads
`Failed at capture: silent_audio_after_handoff`. The controller
returns to `Listening` and the next wake gets a fresh attempt.

`WakeWordController` also waits a short `POST_DETECTION_WARMUP_MS =
50L` after `engine.stop()` before kicking off the capture session,
just enough for the lib's coroutine cancellation to register. The
retry loop is the real mitigation; the 50 ms is a head-start.

### Duplicate ChatViewModel on the wake handoff

(Historical — fixed in `a49bd09`. Documented so the regression is
recognizable.)

The wake-handoff `LaunchedEffect` in `HavenNav` originally keyed on
`(pendingCapture, isKiosk)`. The chat composable's inner effect
consumed the capture by flipping `pendingCapture` back to null, which
re-triggered the outer effect (because `isKiosk` was still true). The
outer effect re-navigated with `popUpTo("history") { inclusive =
false }`, which _destroyed the existing chat entry_ before pushing a
new one — defeating `launchSingleTop`. Two `ChatViewModel`s were
created, both opening their own connections through the singleton
`ChatWsSession.connect` (which internally `close()`s the previous
socket on every call). The captured `vm` reference in the inner
`LaunchedEffect` belonged to the first VM, so `sendMessage` wrote to
the second VM's socket but the user-turn placeholder was appended to
the orphaned first VM's state — the agent's reply landed on a VM
with no placeholder to fill, and the user saw an empty chat with
"Connecting…" up top.

Current shape: the wake-handoff effect keys on `pendingCapture` only,
bails on null, and drops `popUpTo`. One wake → one navigate → one
`ChatViewModel`.

If the duplicate-VM regression returns, the smoking gun is two
`ChatVM runSession start` lines in close succession for one wake
event, often with a `ChatWs ws failure: Socket closed` between them.

### `Status.Idle` reads as "Connecting…"

`ChatViewModel.mapStatus` maps both `ChatWsSession.Status.Idle` and
`Status.Connecting` to `ConnectionUi.Connecting`. The wake hand-off
used to wait for `Status.Connected` _while_ `runSession` was still in
the middle of a sequential `resumeConversation` call — meaning
`ws.connect` had never been called, status was still `Idle`, the UI
showed "Connecting…" indefinitely, and the 8 s `awaitWsReady` timeout
fired silently with "Couldn't connect to server". The fix kicks off
`ws.connect` in parallel with `resumeConversation` so the socket
comes up immediately. If you ever see "Connecting…" stuck on the
kiosk path despite a healthy server, the diagnostic question is
"did `ws.connect kicked off` fire before the user expected it to" —
not "is the server reachable".

### Battery optimization + boot autostart

Doze and adaptive battery will throttle or suspend the mic foreground
service after a few hours idle on stock Android, and much faster on
Samsung / Lenovo OEM skins. The Settings wall-display card now
surfaces an "Ignore battery optimizations" row that reads
`PowerManager.isIgnoringBatteryOptimizations` and, on tap, fires
`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for a one-tap
allow dialog. The status re-checks on `ON_RESUME` so the row updates
when the user returns from the system dialog.

Autostart-after-reboot is handled by `wakeword.BootReceiver`, which
listens for `ACTION_BOOT_COMPLETED` and calls
`WakeWordAutostart.maybeStart(context)` — the same entry point
`HavenCoreApp.onCreate` uses for warm starts. The receiver uses
`goAsync()` so the DataStore read for `wallDisplayEnabled()` can
suspend without ANR. On a Lenovo / MediaTek-skin OEM you may still
need to enable the app's "Auto-start" toggle in the OEM app-manager —
no manifest permission can override that.

## What's intentionally not built

- **Continuous follow-up listening.** The current flow is one-shot
  per wake: capture → transcribe → send → reply. After the agent
  replies the service goes back to wake-listening, but the user has
  to say "hey selene" again to start the next turn. If we want
  conversational follow-ups in kiosk mode (without the wake word),
  the natural pattern is to re-trigger `WakeCaptureSession` directly
  after TTS playback finishes — similar to what the assist overlay's
  silence watcher does inside a single round trip.
- **Lock-task / true pinning** for the docked-tablet scenario.
  Pinning the kiosk chat over the launcher so a user can't
  accidentally back out. Deliberately not wired — the M11 install
  is a soft kiosk (physically out of casual reach), and Device
  Owner provisioning would require a factory reset.
- **VOICE_COMMUNICATION-mode capture** for echo cancellation during
  TTS playback. With the room satellite scenario (small speaker
  next to the mic), the wake-listener will hear its own TTS — we
  may want to mute the engine during `TtsPlayer.state in {Loading,
  Playing}` rather than route through AEC.

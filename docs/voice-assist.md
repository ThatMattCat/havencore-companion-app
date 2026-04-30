# Voice assist — overlay and slot

The default-assistant surface (long-press home / power, lockscreen, or
any `Intent.ACTION_ASSIST` invocation) opens a bottom-sheet overlay
hosted by a `VoiceInteractionSession`. The overlay handles a single
round trip — mic → STT → `/ws/chat` → TTS — and dismisses ~1.5 s after
the reply audio ends. Multi-turn assist is a future surface.

This doc covers the overlay UI, the orchestration, and the OEM gotchas
that the implementation has to compensate for.

## Files

- `voice/HavenAssistService.kt` — the `VoiceInteractionService`. Logs
  only.
- `voice/HavenAssistSessionService.kt` — the
  `VoiceInteractionSessionService` factory.
- `voice/HavenAssistSession.kt` — the per-invocation orchestrator.
  Owns the round-trip state machine, the `MutableStateFlow<AssistUiState>`
  the overlay observes, the mic / STT / WS / TTS calls, and the
  silence-watcher auto-endpoint.
- `voice/AssistUiState.kt` — `Phase` sealed interface
  (`Connecting` → `Listening` → `Transcribing` → `Thinking` → `Replying`
  plus `NoSpeech` / `PermissionMissing` / `Error`) and the surrounding
  data class.
- `voice/AssistOverlay.kt` — Compose surface. Scrim + bottom sheet +
  header (HavenCore + status pill) + animated phase body.
- `voice/AssistVisualizers.kt` — phase-specific hero composables, all
  sized to a 120.dp box so layout doesn't jitter across phase changes.
- `voice/HavenStubRecognitionService.kt` — no-op `RecognitionService`,
  exists purely to satisfy the Samsung Digital Assistant role-picker
  filter (see Samsung gotchas below).
- `voice/DefaultAssistantHelper.kt` — `RoleManager` wrapper +
  unconditional fallback to `Settings.ACTION_VOICE_INPUT_SETTINGS`.
- `voice/AssistLifecycleOwner.kt` — Lifecycle / SavedState /
  ViewModelStore shim so the `ComposeView` inside the session has the
  owners it expects.

## Overlay layout

```
┌──────────────────────────────────────────┐  ← Surface, top-rounded
│            ━━━ drag handle               │
│   HavenCore                  [Listening] │  ← header + status pill
│                                          │
│            ╭──────────╮                  │
│            │   HERO   │                  │  ← 120.dp phase visualizer
│            ╰──────────╯                  │
│                                          │
│   "what time is it"          ← user      │  ← transcript bubble
│   ╭──────────────────────╮               │     (when present)
│   │  It's 3:47 pm.       │               │  ← reply bubble
│   ╰──────────────────────╯               │     (when present)
│                                          │
│            ⊙  Stop                       │  ← phase-specific action
└──────────────────────────────────────────┘
```

Sheet height is `heightIn(min = 240.dp, max = 0.72 * screenHeight)` with
`animateContentSize`, so terse phases (Connecting, NoSpeech) wrap their
content while a long reply pushes the sheet out toward the cap.

The outer Box paints a 40 % black scrim across the whole window
(the session Window is forced to MATCH_PARENT in
`HavenAssistSession.onCreate()` — without that, the Window defaults to
WRAP_CONTENT and the scrim region above the sheet is not hit-testable
so tap-to-dismiss breaks). The inner Surface eats taps so sheet content
does not bubble up to the scrim.

## Phase → visualizer mapping

| Phase              | Hero                              | Body extras                           |
|--------------------|-----------------------------------|---------------------------------------|
| Connecting         | `ConnectingRing` (indeterminate)  | —                                     |
| Listening          | `MicLevelHero` + `MicLevelEqualizer` | 64.dp circular Stop button         |
| Transcribing       | `ConnectingRing`                  | "Hearing you…" caption                |
| Thinking           | `ThinkingDots`                    | transcript bubble + tool-count chip   |
| Replying           | `SpeakingBars` (gated by TTS)     | transcript bubble + reply bubble      |
| NoSpeech           | `MicOff` static disc              | "Didn't catch that" + Dismiss         |
| PermissionMissing  | `Mic` static disc, error tinted   | "Microphone permission required" + Open HavenCore |
| Error              | `ErrorOutline` static disc        | error message + Dismiss               |

`MicLevelHero` and `MicLevelEqualizer` read `MicRecorder.currentAmplitude:
StateFlow<Int>` directly. Normalization clamps `(amp − MIN_PEAK_AMPLITUDE)
/ 6800f` to `[0, 1]` — `MIN_PEAK_AMPLITUDE = 1200` is the same threshold
the silence watcher uses to detect speech, so the hero's halo only grows
above the room-tone floor and the equalizer bars below it stay flat
during ambient noise.

`SpeakingBars` is a programmatic 5-bar animation. The bars only animate
while `TtsPlayer.state` is `Loading` or `Playing`; they rest flat
otherwise. We don't have output PCM amplitude, so the visualization is
synthetic — but it's gated on a real signal (whether audio is actually
playing).

## Phase transitions

`AssistOverlay` wraps the body in an `AnimatedContent` keyed on
`state.phase::class` with `fadeIn(200) + slideInVertically(it/6)
togetherWith fadeOut(150) using SizeTransform(clip = false)`. The sheet
itself uses `animateContentSize(tween(220))` so its height animates
alongside the phase content rather than snapping.

## Auto-endpointing

`HavenAssistSession.launchSilenceWatcher()` polls
`MicRecorder.currentAmplitude` every 100 ms while the recorder is in
`State.Recording`:

- Once a sample crosses `MIN_PEAK_AMPLITUDE`, mark `lastSpeechAt = now`.
- After `silenceTimeoutMs` (settings-controlled) of sub-threshold
  samples since `lastSpeechAt`, stop the recorder.
- `HARD_CAP_MS = 15_000` is a safety net for "never spoke" /
  "spoke continuously" — bounds the worst case.
- The overlay's Stop button still works as a manual override; it just
  calls `app.mic.stop()` and the watcher bails on the recorder's
  state transition.

## Samsung gotchas

### `RecognitionService` stub

Samsung's Digital Assistant role picker filters out a
`VoiceInteractionService` whose package does not also declare a
`RecognitionService`, even when STT is proxied to a server. Pixel and
AOSP do not do this. `HavenStubRecognitionService` is a no-op that
fails every request fast with `ERROR_RECOGNIZER_BUSY`. Its sole
purpose is to satisfy that filter.

### `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` no-op

On Samsung, the role-request intent path resolves but silently does
nothing — the system role picker never appears.
`DefaultAssistantHelper.pickerIntent()` therefore unconditionally
returns `Settings.ACTION_VOICE_INPUT_SETTINGS` so the user lands on
the Voice input settings page where the assistant can actually be
selected.

## Permission edge cases

- `RECORD_AUDIO` cannot be requested from a `VoiceInteractionSession`
  (no Activity to host the permission dialog). When the session
  starts and the permission is not granted, the overlay surfaces
  `Phase.PermissionMissing` with an "Open HavenCore" button that
  finishes the session and brings the main app forward — the user
  grants the permission there and can retry the assist invocation.
- The session reuses the foreground app's `MicRecorder`,
  `TtsPlayer`, OkHttp client, and settings. To avoid disrupting an
  open foreground chat WS, it constructs its own `ChatWsSession`
  but binds it to the same `lastSessionId`, so voice turns from any
  entry point land in the same History row.

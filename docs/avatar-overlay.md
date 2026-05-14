# Live2D avatar overlay (wall-display tablet mode)

A Live2D avatar (placeholder model: **Hiyori**) floats on top of Home
Assistant when "hey selene" fires, runs the full STT → LLM → TTS turn
inline, lip-syncs against a Rhubarb-generated viseme timeline, and
dismisses ~8 s after the last activity so HA is fully visible again.

Implementation status — **lip-sync working; semi-transparency
deferred** (2026-05-14):
- **Lip-sync working end-to-end.** Confirmed on the M11: Rhubarb cues
  flow TTS service → agent → `TtsApi` (X-Visemes header) → 
  `VisemeScheduler.setTimeline` → `AvatarController` → JS
  `setMouthShape` → rig `ParamMouthOpenY`. Per-tick logcat capture
  confirmed the override wins on the rig (`actualOpenY` matched what
  Kotlin pushed). Required one fix during handoff: stash + clear the
  motion manager's `Idle` group definitions on entering Speaking so
  the lib's auto-restart doesn't re-write the mouth keyframes between
  our LOW-priority ticker overrides. Restored on exit. See
  `index.html` `setPhase()` for the implementation.
- **Avatar renders semi-transparent — DEFERRED.** Root-caused on the
  M11 (Lenovo Tab M11 / Zui ROM) to system-level alpha multiplication
  on the entire `TYPE_APPLICATION_OVERLAY` surface; standard
  `WindowManager.LayoutParams` knobs cannot defeat it. Diagnostic
  detail in "Known issues" below.

Opt-in via **Settings → Wall-display mode → Avatar overlay mode**
(default **on** when the wall-display toggle is on; phone installs can
turn it off without affecting any other behavior). Requires the
`SYSTEM_ALERT_WINDOW` system grant — surfaced as a row in the same
card.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│ M11 tablet                                                           │
│                                                                      │
│ ┌── HA Companion app (Android launcher) ──┐  ◀── always-visible      │
│ │ HA dashboard                            │      default screen      │
│ │                                         │                          │
│ │ ┌── HavenCore overlay window ────────┐  │  ◀── TYPE_APPLICATION_   │
│ │ │ ┌── Compose surface ─────────────┐ │  │      OVERLAY,            │
│ │ │ │ WebView (transparent) ──────── │ │  │      FLAG_NOT_TOUCHABLE  │
│ │ │ │  ┌── PIXI v7 canvas ─────────┐ │ │  │      so HA still scrolls │
│ │ │ │  │ pixi-live2d-display +     │ │ │  │                          │
│ │ │ │  │ Cubism Core JS            │ │ │  │                          │
│ │ │ │  │  └ Hiyori model           │ │ │  │                          │
│ │ │ │  └───────────────────────────┘ │ │  │                          │
│ │ │ │ Caption bubble (HTML div)      │ │  │                          │
│ │ │ └───────────────────────────────-┘ │  │                          │
│ │ └────────────────────────────────────┘  │                          │
│ └─────────────────────────────────────────┘                          │
│                                                                      │
│ Background services (HavenCore):                                     │
│   MicrophoneForegroundService  ← wake-word loop (Phase 1.5 model)    │
│   AvatarOverlayService         ← starts per wake (this doc)          │
└──────────────────────────────────────────────────────────────────────┘
```

### Event flow on a wake

```
WakeWordController.Detected
  → MicrophoneForegroundService.launchChatWithCapture(wav)
      ├─ avatarOverlayEnabled() && canDrawOverlays() →
      │   startForegroundService(AvatarOverlayService, EXTRA_CAPTURE_PATH=wav)
      └─ else → legacy MainActivity launch (unchanged phone path)

AvatarOverlayService
  • startForeground (mediaPlayback type)
  • check SYSTEM_ALERT_WINDOW grant; bail if missing
  • mountOverlay(): WindowManager.addView(ComposeView{WebView})
  • bindControllerToJs(): collect controller.state → evaluateJavascript
  • startIdleWatcher(): 500ms poll; dismiss when (idleMs >= settingMs && !ttsBusy)
  • VoiceTurnRunner(captureWav).run() in service scope:
        controller.setPhase(Listening)
        open ChatWsSession; await Connected (5s timeout)
        controller.setPhase(Thinking)
        sttApi.transcribe(wav) → text
        ws.send(text); await Done event
        ttsApi.speak(replyText) → bytes + visemes (from X-Visemes header)
        visemeScheduler.setTimeline(visemes)
        controller.setPhase(Speaking)
        ttsPlayer.play(audioBytes, contentType)
        await tts.state != Loading|Playing
        visemeScheduler.setTimeline(null)
        controller.setPhase(Idle)
  • IdleWatcher fires → stopSelf()
  • onDestroy: WindowManager.removeView; controller.release()
```

## Files

### New (companion app)

| Path | Role |
|---|---|
| `app/src/main/kotlin/.../wakeword/AvatarOverlayService.kt` | Foreground service, overlay window, WebView host, state→JS bridge, idle watcher |
| `app/src/main/kotlin/.../wakeword/AvatarOverlayDebugReceiver.kt` | Exported BroadcastReceiver — adb-triggerable; forwards `DEBUG_SHOW` → service |
| `app/src/main/kotlin/.../voice/avatar/AvatarController.kt` | `StateFlow<AvatarUiState>` + activity-time tracking |
| `app/src/main/kotlin/.../voice/avatar/AvatarPhase.kt` | Phase enum + UiState data class |
| `app/src/main/kotlin/.../voice/avatar/AvatarBridge.kt` | JS → Kotlin `@JavascriptInterface` (`onReady`/`onError`/`onTap`) |
| `app/src/main/kotlin/.../voice/avatar/VoiceTurnRunner.kt` | Headless turn (STT → WS → TTS) sibling of `HavenAssistSession` |
| `app/src/main/kotlin/.../voice/avatar/OverlayPermHelper.kt` | `Settings.canDrawOverlays` + perm-request intent (shape mirrors `BatteryOptHelper`) |
| `app/src/main/kotlin/.../voice/avatar/VisemeScheduler.kt` | Plays a `VisemeTimeline` against `TtsPlayer.currentPositionMs`; emits `MouthShape` |
| `app/src/main/kotlin/.../voice/avatar/VisemeTimeline.kt` | Rhubarb JSON data class + base64 decoder + cue→`MouthShape` table |
| `app/src/main/assets/live2d/index.html` | WebView entry — PIXI + Cubism Core + pixi-live2d-display + `window.AvatarApi` |
| `app/src/main/assets/live2d/runtime/{pixi.min.js,live2dcubismcore.min.js,cubism4.min.js}` | Live2D runtime bundle (~780 KB) |
| `app/src/main/assets/live2d/models/hiyori/*` | Hiyori placeholder model — 9 idle motions, 2 textures, ~4.8 MB |

### Modified (companion app)

| Path | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; register `AvatarOverlayService` (foregroundServiceType=mediaPlayback, exported=false) + `AvatarOverlayDebugReceiver` (exported=true, action filter) |
| `app/src/main/kotlin/.../HavenCoreApp.kt` (`AppContainer`) | Instantiates `VisemeScheduler` singleton |
| `app/src/main/kotlin/.../audio/TtsPlayer.kt` | Exposes `currentPositionMs: StateFlow<Long>`; polls ExoPlayer position at ~50 Hz only while Playing |
| `app/src/main/kotlin/.../net/TtsApi.kt` | Reads `X-Visemes` response header, base64-decodes, parses into `VisemeTimeline` on the `Spoken` result |
| `app/src/main/kotlin/.../wakeword/MicrophoneForegroundService.kt` | `launchChatWithCapture` branches: avatar overlay path (when enabled + permitted) vs. legacy MainActivity path |
| `app/src/main/kotlin/.../data/SettingsRepository.kt` | New keys: `avatar_overlay_enabled` (default true), `avatar_idle_timeout_ms` (default 8000) |
| `app/src/main/kotlin/.../ui/settings/SettingsViewModel.kt` | Exposes flows for new settings + `isOverlayPermGranted` (refreshed in `onResume`) |
| `app/src/main/kotlin/.../ui/settings/SettingsScreen.kt` (`WallDisplayCard`) | 3 new rows: overlay toggle, perm row, idle-timeout slider |
| `app/src/main/res/values/strings.xml` | New strings for the three rows + notification copy |

### Cross-repo (havencore agent host)

| Path | Change |
|---|---|
| `services/text-to-speech/Dockerfile` | Installs Rhubarb 1.14.0 Linux binary into `/opt/rhubarb` + symlink to `/usr/local/bin/rhubarb` |
| `services/text-to-speech/app/main.py` | After Kokoro generates samples, runs `rhubarb -f json -r phonetic` on a temp PCM_16 WAV, base64-encodes the JSON, attaches as `X-Visemes` response header; soft-fail if binary missing or errors |
| `services/agent/selene_agent/api/tts.py` | `/api/tts/speak` proxy forwards the `X-Visemes` upstream header (and adds `Access-Control-Expose-Headers` for browser clients) |

## JS bridge contract

`window.AvatarApi` exposed by `index.html`:

```js
AvatarApi.applyState({
  phase: "idle" | "listening" | "thinking" | "speaking" | "error",
  expression: "neutral" | "alert" | "happy" | "sad" | "confused",
  mouthOpenY: 0..1,         // ParamMouthOpenY (held closed off-speak)
  mouthForm:  -1..1,        // ParamMouthForm
  caption:    string|null,  // shown in HTML bubble at bottom
});

// Individual setters also exposed; applyState() is what Kotlin uses.
AvatarApi.setPhase(...); AvatarApi.setMouthShape(o, f); AvatarApi.setExpression(name);
AvatarApi.setCaption(text); AvatarApi.stopIdle(); AvatarApi.resumeIdle();
AvatarApi._debug.{model, app, phase, mouth, overrideTicks, getParam(id), probe(ms)}
```

`window.AndroidAvatar` exposed by Kotlin (`AvatarBridge`):

```kotlin
@JavascriptInterface fun onReady()          // model finished loading
@JavascriptInterface fun onError(msg: String)
@JavascriptInterface fun onTap()            // wired but not consumed in v1
```

State pushes from Kotlin use **JSON payload** (not hand-built JS strings — a
hand-rolled `setCaption('${text}')` blew up on Selene replies containing
newlines/quotes/punctuation, see commit history). The whole AvatarUiState
is serialized via kotlinx.serialization and the JS side destructures it
in `applyState`.

## Viseme contract (server-side soft dep)

```
POST /api/tts/speak  (companion → agent → text-to-speech)
HTTP/1.1 200 OK
Content-Type: audio/wav
Content-Length: 150044
X-Visemes: eyJtZXRhZGF0YSI6...    ← base64-encoded JSON, optional
Access-Control-Expose-Headers: X-Visemes

<wav bytes>
```

Decoded JSON shape (Rhubarb `--machineReadable` `-f json`):

```json
{
  "metadata": {"duration": 3.12},
  "mouthCues": [
    {"start": 0.00, "end": 0.25, "value": "X"},
    {"start": 0.25, "end": 0.39, "value": "C"},
    ...
  ]
}
```

Cue values follow Preston-Blair 9-shape alphabet: `A B C D E F G H X`
(X = silence). `VisemeScheduler` maps each to `(mouthOpenY, mouthForm)`
floats and lerps over ~40 ms between consecutive cues. Verified during
implementation via `curl -D /tmp/h.txt … && base64 -d | jq` against the
TTS service directly (port 6005, `/v1/audio/speech`).

**Server soft-fail behavior**: if the `rhubarb` binary is missing or
errors, the TTS response omits `X-Visemes` and the client mouth holds
closed. The companion-app side likewise treats absent / malformed
headers as "no timeline" without erroring.

## Asset details — Hiyori placeholder

Pulled from `Live2D/CubismWebSamples` via sparse `git clone`:

```
app/src/main/assets/live2d/models/hiyori/
  Hiyori.model3.json         — 1.7 KB (FileReferences + Groups)
  Hiyori.moc3                — 444 KB (binary rig)
  Hiyori.physics3.json       —  26 KB
  Hiyori.cdi3.json           —   9.6 KB
  Hiyori.pose3.json          —   166 B
  Hiyori.userdata3.json      —   623 B
  Hiyori.2048/
    texture_00.png           — 1.81 MB
    texture_01.png           — 2.50 MB
  motions/
    Hiyori_m{01..10}.motion3.json
```

Total: ~4.8 MB. Live2D `Groups.LipSync.Ids` declares `ParamMouthOpenY`;
the model has no `ParamMouthForm` either declared or in our observation
(JS side guards against `setParameterValueById` throwing — toggles
`mouthFormSupported=false` on first failure). The model has no
`expressions/` directory; `AvatarApi.setExpression` is a soft no-op
slot for the v2 affect hook (left wired for future custom rigs).

Replacement model later: swap files at the same asset path; update
`index.html` if the `.model3.json` filename or parameter IDs differ.

## Debug + manual triggers

**Trigger the overlay directly (no wake-word required):**

```bash
adb shell am broadcast -n ai.havencore.companion/.wakeword.AvatarOverlayDebugReceiver \
    -a ai.havencore.companion.avatar.DEBUG_SHOW
```

The receiver forwards to `AvatarOverlayService` with `ACTION_DEBUG_SHOW`.
The overlay shows with phase=Listening + caption="debug overlay", holds
until the idle timeout (default 8 s) elapses, then dismisses normally.
Use this to iterate on rendering / layout without bothering the user
for each wake-word fire.

Note: a bare `am broadcast -a ai.havencore.companion.avatar.DEBUG_SHOW`
fails with "no receivers" if the app was recently `am force-stop`'d
(stopped-package broadcast filter). Use the explicit component form
above, OR add `--include-stopped-packages`.

**TTS smoke test directly against the service (bypasses the agent):**

```bash
curl -sS -D /tmp/h.txt \
  -X POST http://<agent-host>:6005/v1/audio/speech \
  -H 'Content-Type: application/json' \
  -d '{"input":"Hello, this is a test.","voice":"af_heart","response_format":"wav"}' \
  -o /tmp/say.wav
grep -i '^X-Visemes:' /tmp/h.txt | sed 's/^X-Visemes: //;s/\r$//' \
  | base64 -d | python3 -m json.tool
```

**Via the agent proxy (what the companion app actually hits):**

```bash
curl -sS -D /tmp/h.txt \
  -X POST http://<agent-host>:6002/api/tts/speak \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello.","voice":"af_heart","format":"wav"}' \
  -o /tmp/say.wav
# Same X-Visemes header decode
```

If the agent proxy run doesn't include `X-Visemes`, the agent service
hasn't picked up the `selene_agent/api/tts.py` change yet — `docker
compose restart agent` (or rebuild for clean state).

**WebView console logs:**

`AvatarOverlayService` wires a `WebChromeClient.onConsoleMessage` that
forwards all JS `console.log` / `console.error` calls to logcat under
tag `Avatar:WV`. Useful for diagnosing JS-side init failures.

```bash
adb logcat | grep -E "Avatar:|TtsApi|VisemeSched"
```

(`-s TAG` syntax doesn't accept colons in tag names — use grep.)

## Known issues at handoff

### 1. Avatar appears semi-transparent — DEFERRED

**Status**: root-caused but unfixed. Accepted as-is on the M11 for now;
revisit later if/when the wall-display experience needs polish.

**Symptom**: Hiyori renders and lip-syncs (caption + body motion confirm
the pipeline is live) but at reduced apparent opacity against HA. A
fully-opaque PIXI Graphics drawn on the same canvas appears equally
faded — so this is canvas/window-level alpha, not Live2D-specific.

**Root cause (2026-05-14 diagnostic)**: the entire WebView/overlay
surface is being dimmed by the system, not the WebGL output.
Confirmed via the diagnostic test of setting the canvas CSS bg to
`rgba(0,0,0,1.0)` — even a fully-opaque black `<canvas>` rendered
against HA showed HA partially visible behind it. So the alpha
multiplication is happening at the system compositor / display layer,
applied to the entire `TYPE_APPLICATION_OVERLAY` surface, not at the
WebGL → canvas → DOM stage. Almost certainly a Lenovo Zui ROM
behaviour on the M11 (TB336FU); standard `WindowManager.LayoutParams`
controls cannot defeat it.

**Things tried that did NOT help** (each rebuilt + retested individually
on the M11 then layered, 2026-05-14 — none moved the needle, all
reverted):
- `premultipliedAlpha: true` on the PIXI Application (the WebGL default).
- `PixelFormat.RGBA_8888` on the overlay `LayoutParams` (instead of
  `TRANSLUCENT`).
- `setLayerType(LAYER_TYPE_HARDWARE, null)` on the parent ComposeView
  (in addition to the inner WebView, which already had it).
- `TYPE_PHONE` overlay window — rejected outright on API 35
  (`permission denied for window type 2002`).
- Explicit `params.alpha = 1.0f` + `params.dimAmount = 0.0f`.

**Things that remain plausible if revisited** (none cheap):
- Bypass the overlay window route entirely: render the avatar in a
  fullscreen transparent `Activity` launched on top of HA (
  `theme=Theme.Translucent.NoTitleBar`, `FLAG_NOT_TOUCHABLE`,
  `FLAG_SHOW_WHEN_LOCKED`). Activities don't get the same Zui-side
  dim. Requires re-routing `MicrophoneForegroundService.launchChatWithCapture`
  and re-plumbing the per-turn lifecycle (the overlay-service was
  partly chosen so HA stays the launcher activity — an Activity
  approach has to coexist with that without stealing focus).
- Try the same code on a non-Lenovo / non-Zui tablet to confirm
  scope; if the dim is M11/Zui-specific, the overlay path stays
  correct for general-Android wall-display use cases and only the M11
  needs the Activity workaround.
- Render PIXI to an offscreen canvas, blit per-frame via `toDataURL`
  into an `<img>` — sidesteps the WebGL surface composition path but
  still goes through the same overlay window, so probably won't help
  given the diagnostic above. Was on the list as a fallback; deferred
  in favour of the Activity approach above.

A `screencap` PNG of the device shows Hiyori fully opaque against HA;
the user reports she's still semi-transparent on-screen. That
discrepancy is consistent with the root cause — `screencap` reads
SurfaceFlinger's pre-display composited buffer (no Zui display-side
dim applied), while the live panel composition does apply it.

### 2. Lip-sync triggering — RESOLVED (2026-05-14)

End-to-end pipeline verified on the M11:

```
TtsApi      speak ok: 358844B audio/wav, X-Visemes=2692B b64 → 51 cues
VisemeSched setTimeline: 51 cues, duration=7.47s
[mouth] kotlin pushed openY=0.25 form=0.70 phase=speaking
[mouth] tick=69 setOpenY=0.25 setForm=0.70 actualOpenY=0.250
[mouth] tick=92 setOpenY=0.60 setForm=-0.50 actualOpenY=0.600
…
VisemeSched setTimeline: null (mouth held closed)
Avatar:Svc  idle for 8270ms (timeout=8000ms) — dismissing
```

`actualOpenY` (read back from the rig via
`coreModel.getParameterValueById`) matches the value our LOW-priority
ticker callback wrote — confirming the override wins against the
model's motion update.

**Fix required during handoff verification**: pixi-live2d-display's
motion manager auto-restarts an `Idle` motion when none is playing.
Hiyori's Idle motion has its own `ParamMouthOpenY` keyframes, which
the user observed visibly dominating the avatar's mouth (no
viseme-driven motion). The fix in `index.html#setPhase`:
- On entering Speaking: stash and clear `motionManager.definitions.Idle`
  so the auto-restart has nothing to play, then `stopAllMotions()`.
- On exiting Speaking: restore `definitions.Idle` and start a fresh
  Idle motion.

Per-tick `stopAllMotions()` was tried first and visibly glitched the
body animation (every frame killed motion before it could play out);
clearing the *definitions* at phase entry is surgical, lets one-shot
motions still play (e.g. TapBody on Listening), and lets physics3.json
keep driving hair/breath.

The diagnostic JS-side `[mouth]` log lines shown above were stripped
from the shipping `index.html` after verification. The two remaining
log surfaces are still in place:
- Kotlin `TtsApi`: ``speak ok: <bytes>B <ct>, X-Visemes=<N> cues``
- Kotlin `VisemeSched`: ``setTimeline: <N> cues, duration=<S>s``

### Other smaller items

- Tap-on-avatar is plumbed (`AvatarBridge.onTap`, `AvatarApi` exposes
  `setExpression`, JS handler in `index.html`) but `FLAG_NOT_TOUCHABLE`
  on the overlay window means touches pass to HA — no tap event will
  fire. Out of scope per the original plan.
- Hiyori's `motions/` includes 9 idle variations that auto-cycle.
  During Speaking phase the JS `setPhase("speaking")` calls
  `motionManager.stopAllMotions()` — but pixi-live2d-display auto-
  restarts an Idle motion when none is playing, so body movement
  continues throughout. Our `afterMotionUpdate` mouth override should
  still win against motion-driven mouth keyframes (verified in the
  Chrome harness with `_debug.probe()`).
- Phone-vs-tablet default for `avatar_overlay_enabled` was specced as
  ``smallestScreenWidthDp >= 600`` in the plan; current impl defaults
  to `true` unconditionally. Phones can flip it off in Settings; if
  this is a regression for phone users, add the screen-size gate in
  `SettingsRepository` (one-line change).

## Out of scope (deliberate)

- Lock-task / true kiosk pinning — still soft-kiosk.
- Custom Selene Live2D commission — Hiyori is the placeholder.
- Multi-turn follow-up without re-firing wake — same as the prior
  wall-display behavior.
- Server-emitted affect hint — `setExpression` slot is reserved.
- Tap → typed chat — bridge wired, consumer deliberately not wired.
- Phoneme-level lip-sync — Rhubarb 9-shape is what the rig can
  express anyway.

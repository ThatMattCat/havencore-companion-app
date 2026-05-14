# HavenCore Companion App

Android companion to [HavenCore](https://github.com/ThatMattCat/havencore) —
the self-hosted AI smart-home assistant.

The app delivers:

- **Text chat** over `/ws/chat` with a conversation list and resume.
- **Push-to-talk voice** in chat over `/api/stt/transcribe` and
  `/api/tts/speak`.
- **The Android default-assistant slot** via `VoiceInteractionService`:
  long-press home / power, lockscreen, or any `Intent.ACTION_ASSIST`
  invocation opens a bottom-sheet overlay that runs one round trip
  (mic → STT → `/ws/chat` → TTS) and dismisses ~1.5 s after the reply
  audio ends. The overlay auto-endpoints on silence, with a Stop
  button as a manual override.
- **Always-listening wall-display mode** (off by default) — a
  foreground microphone service that runs an on-device "hey selene"
  wake-word detector (openWakeWord + Silero VAD endpointing). On
  detection it captures the user's request, opens chat in fullscreen
  kiosk mode, transcribes, and sends. Intended for a docked tablet;
  works on a phone for testing.
- **Inbound push notifications** via [UnifiedPush](https://unifiedpush.org/)
  with a self-hosted ntfy distributor. Push payloads carry an optional
  `session_id`; tapping a notification deep-links to that thread in
  History.
- **Device-side actions**: when the agent's LLM calls a device-targeted
  MCP tool, the agent emits a `device_action` event on `/ws/chat` and
  the app fires the matching native flow — scheduling an alarm, taking
  a photo, etc. See [Device actions](#device-actions) below.

Voice turns from any entry point (typed, push-to-talk, assist overlay)
land in the same History row, since they all share the same
`session_id`.

## Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- OkHttp 4.12 + kotlinx-serialization-json 1.7
- Navigation Compose 2.8 — three routes (Settings / History / Chat)
- Jetpack DataStore (Preferences) for persisted settings
- UnifiedPush connector 3.0.x
- minSdk 31 (Android 12) — `AudioManager.setCommunicationDevice()` is
  the only Bluetooth-mic routing path
- compileSdk / targetSdk 35
- AGP 8.7.3, Gradle 8.10.2, JDK 17

## Build

Standard Android: open in **Android Studio Hedgehog or newer** (ships
JDK 17 and SDK), or `./gradlew assembleDebug` with `JAVA_HOME` (JDK 17)
and `ANDROID_HOME` set. Output APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

## Day-to-day dev loop — Wireless ADB from a Linux build host

The primary workflow: build on a Linux server, push to the phone (or
the docked wall-display tablet) over Wireless ADB on the LAN.
`scripts/adb-env.sh` handles environment setup and device discovery;
source it once per shell.

### One-time pairing (Android 11+)

1. Device → Settings → Developer options → **Wireless debugging** → On
2. Tap **Pair device with pairing code** (the modal — _not_ the main
   screen's IP & Port; those are different). Note the pair `host:port`
   and 6-digit code.
3. From the build host:
   ```bash
   adb pair <pair-host:port>   # then enter the 6-digit code
   ```

Pairing persists across reboots; you only repeat it if the device is
wiped or the server's adb keys change. Repeat for each device you want
to target (the phone, the wall-display tablet, etc).

### Each shell session

```bash
source scripts/adb-env.sh                                     # PATHs + mDNS-discover + adb connect
./gradlew installDebug                                        # build + push
adb shell am start -n ai.havencore.companion/.MainActivity    # launch
adb logcat | grep -i 'havencore\|ChatWs\|ChatVM\|MicRec\|TtsPlay\|Voice:VIS\|Voice:Sess\|Push:Recv\|Push:Reg\|Push:Api\|DeviceAction\|CaptureActivity\|WakeWord'   # tail logs
```

The _connect_ port (separate from the pair port) rotates every time
Wireless debugging is toggled or the device reboots. The helper script
auto-discovers it via mDNS — no need to hardcode.

If more than one device is paired, the script lists them by product
model and asks you to pick one in interactive shells, then disconnects
the unselected transports so `installDebug` doesn't bail with "more
than one device/emulator". Non-interactive shells fall back to the
first mDNS match; pin the target explicitly with `PHONE_HOST` if that
matters. The same override unblocks mDNS-blocked networks:

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

On first launch with no saved server URL, the app opens to **Settings**.
Once a URL is saved, subsequent launches open to the **History** list.

1. **Settings** — Server URL (e.g. `http://havencore.local`,
   `http://10.0.0.20`, or whatever the nginx gateway publishes) and an
   editable device name (defaults to `Build.MODEL`). Tap **Test connection**
   to ping `/api/conversations`. Settings also exposes the assist
   silence-timeout, push toggle, theme mode, dynamic-color toggle, and
   the per-tool camera toggles.
2. **History** — list of prior conversations sorted newest-first.
   Pull-to-refresh, refresh icon, and a top-bar gear that opens
   Settings.
   - Tap a row to **resume** that session — the agent re-binds the same
     `session_id` and the prior messages render as immutable history
     above the input.
   - Tap the FAB ("New conversation") to start a fresh chat.
3. **Chat** — text input at the bottom, mic button to its left for
   push-to-talk voice, agent label + last 8 chars of session id at the
   top. An auto-speak toggle in the top-bar persists across launches;
   voice-in turns always speak the reply regardless. The connection
   banner surfaces "Connecting…", "Reconnecting (Ns)…", or "Connection
   failed: … [Retry]" when the WS isn't in `Connected`. While a turn is
   in progress, an assistant card shows "Thinking…" (or "Retrying tool
   call (iteration N)…"), then renders any `tool_call` / `tool_result`
   / `reasoning` / `device_action` events in order, then the final
   text, then a `MetricChips` row showing total / LLM / tool ms (tap
   for the per-tool breakdown). A Settings banner nags users to set
   HavenCore as the default assistant; a one-shot dialog on first
   chat-screen open surfaces the same prompt.

### Default-assistant slot

Once HavenCore is set as the default assistant in **Settings → Apps →
Default apps → Digital assistant**, a long-press of home / power (or
any `Intent.ACTION_ASSIST` invocation, including from the lockscreen)
opens a bottom-sheet overlay that captures one mic utterance,
transcribes it, sends it over the same `/ws/chat` socket, plays the
reply, and dismisses ~1.5 s after the audio ends. Single round trip
per invocation. The session reuses the chat's `session_id`, so an
assist turn shows up at the top of History next to the typed and
voice-in-app turns.

The overlay auto-endpoints on silence: a watcher polls the mic peak
amplitude every 100 ms, and once the level has stayed below the
speech threshold for the configured silence timeout (Settings →
"Voice silence timeout"), the recorder stops automatically. A 15 s
hard cap bounds the worst case for "never spoke" / "spoke
continuously." The Stop button in the overlay is still there as a
manual override.

See `docs/voice-assist.md` for the overlay anatomy, phase →
visualizer mapping, and OEM gotchas (Samsung's role-picker filter
needs a `RecognitionService` stub; the role-request intent is
unreliable on Samsung, so the helper falls back to
`Settings.ACTION_VOICE_INPUT_SETTINGS`).

### Wall-display mode

A second voice surface for the docked-tablet scenario: a foreground
microphone service that continuously listens for "hey selene"
on-device (no audio leaves the device until a detection fires), then
captures the user's request, opens chat in fullscreen kiosk mode, and
sends the transcript on the same `/ws/chat` socket. Toggle in
**Settings → Wall-display mode → Enable wall-display mode**; off by
default so phone users get the unchanged behavior. Drop the four ONNX
files (`embedding_model.onnx`, `melspectrogram.onnx`,
`wakeword/hey_selene.onnx`, `wakeword/silero_vad.onnx`) into
`app/src/main/assets/` before flashing — without them the service
logs `Failed at engine_start` and stops itself.

The persistent notification narrates each phase
(`Listening for 'hey selene'` → `Detected (score=…)` → `Captured Xms`
→ `Failed at <stage>: …`), so the easiest way to debug a missed wake
is the notification shade rather than `adb logcat`.

The wake-word stack uses [openWakeWord](https://github.com/dscripka/openWakeWord)
(`xyz.rementia:openwakeword:0.1.3` Android bindings) with a
custom-trained "hey selene" classifier, and
[snakers4/silero-vad](https://github.com/snakers4/silero-vad) for
post-wake end-of-utterance detection. The classifier ONNX bakes a
Sigmoid as its final op, so the lib's threshold compare operates
directly in probability space (default 0.420).

See [`docs/wake-word.md`](docs/wake-word.md) for the full
architecture, the Galaxy S24 silent-handoff workaround, diagnostic
log tags, and the kiosk theme + activity-launch contract.

### Avatar overlay (wall-display tablet only)

On wall-display installs (default-on under the wall-display master
toggle), the wake handoff no longer pops a fullscreen chat screen.
Instead a Live2D avatar (placeholder model: Hiyori) floats on top
of HA via a `SYSTEM_ALERT_WINDOW` overlay, runs the same STT → LLM →
TTS turn inline, lip-syncs against a server-supplied Rhubarb viseme
timeline, and dismisses ~8 s after the last activity. HA touch input
passes through (FLAG_NOT_TOUCHABLE).

Two open issues at handoff: Hiyori renders semi-transparent against
the overlay (alpha compositing — multiple PIXI + WebView + window
flag fixes attempted), and the lip-sync timeline arrival on a real
wake-fire is unverified end-to-end (the server contract passed curl).
See [`docs/avatar-overlay.md`](docs/avatar-overlay.md) for the full
architecture, JS bridge contract, asset layout, debug triggers, and
the punch list of what's been tried.

### Device actions

Some user requests are best satisfied by an Android Intent or a
device-side capability rather than a server-side tool — setting an
alarm, taking a photo for the LLM to look at. The agent enumerates
its device-targeted tools in a `DEVICE_ACTION_TOOLS` set; whenever
the LLM calls one, the orchestrator emits a `device_action` event on
`/ws/chat` alongside the normal `tool_call` / `tool_result` pair, and
the app fires the corresponding native flow.

Currently wired:

| Action                      | What it does                                            | Native surface                                         |
|-----------------------------|---------------------------------------------------------|--------------------------------------------------------|
| `set_alarm`                 | Schedule an alarm in the user's Clock app               | `AlarmClock.ACTION_SET_ALARM` (silent, `EXTRA_SKIP_UI`)|
| `take_photo`                | Capture a photo and hand it back to the LLM             | `CaptureActivity` → `POST /api/companion/upload`       |
| `identify_object_in_photo`  | Photo + vision-model identification (optional `hint`)   | Same camera flow, server-side vision chain             |
| `read_text_from_image`      | Photo + OCR                                             | Same camera flow, server-side OCR chain                |
| `who_is_in_view`            | Photo + face recognition against the enrolled gallery   | Same camera flow, server-side face-rec chain           |

The chat screen renders a `DeviceActionCard` next to the
corresponding `tool_call` breadcrumb. The assist overlay surfaces a
chip ("1 action") during its Thinking phase and lets the LLM's reply
play out via TTS; for `set_alarm` the alarm is already scheduled by
the time you hear "OK, I've set an alarm for 7am." The four camera
variants share one capture-and-upload primitive: the dispatcher
launches the device camera, the user takes the shot, and the JPEG is
POSTed to the agent's `/api/companion/upload` keyed by the
`tool_call_id` so the agent's awaiting future resolves with the
resulting `image_url`.

**Permissions and toggles.** The alarm action requires
`com.android.alarm.permission.SET_ALARM` (a normal-permission grant on
install). The camera actions require the runtime `CAMERA`
permission. Settings exposes one master camera switch (gating
`take_photo`, the underlying primitive) and three per-tool switches
(`identify_object_in_photo`, `read_text_from_image`,
`who_is_in_view`), all default ON; turning any off causes the
dispatcher to short-circuit with `Disabled` before launching the
camera.

Adding a new device action means: define the MCP tool agent-side, add
it to `DEVICE_ACTION_TOOLS`, and on the app side add a new
`DeviceAction` sealed-class variant + dispatcher branch + display row
in `DeviceActionCard.kt`. Wire format and the camera-capture flow are
documented in [`docs/wire-protocol.md`](docs/wire-protocol.md).

### Push notifications

HavenCore wakes the phone with autonomy briefings ("the dryer is
done", "your 9am starts in 10 minutes", "garage door has been open
for 2 hours") via UnifiedPush. Tapping a notification deep-links to
the originating chat thread — the same session id appears in
History.

The companion app is distributor-agnostic; we recommend
[**ntfy**](https://ntfy.sh/) because it's self-hostable and matches
HavenCore's ethos, but any UnifiedPush distributor (NextPush, FCMUP,
embedded FCM) works without app changes.

#### One-time setup

1. **Install the ntfy Android app** from
   [F-Droid](https://f-droid.org/en/packages/io.heckel.ntfy/), the
   Play Store, or `https://ntfy.sh/app`.
2. **Point ntfy at your server** — open the ntfy app, set Default
   server URL to your self-hosted instance (e.g. `https://ntfy.lan` or
   `http://10.0.0.x:8585`), or leave it pointing at the public
   `https://ntfy.sh` for testing.
3. **Exempt ntfy from battery optimization** — Android Settings →
   Apps → ntfy → Battery → **Unrestricted**. The ntfy app holds the
   long-lived socket to the ntfy server; aggressive OEM background
   limits (especially Samsung One UI) will silently kill it
   otherwise. Optionally do the same for HavenCore as
   belt-and-suspenders.
4. **In HavenCore Settings → Notifications**, flip Enable
   notifications. Approve the `POST_NOTIFICATIONS` permission prompt
   on Android 13+. Status flips from `Awaiting endpoint` to `Ready` in
   ~1 s — the masked endpoint URL is visible underneath.

#### What's wired

The agent fans out push payloads to each registered device's
endpoint; the ntfy server forwards bytes byte-for-byte to the ntfy
app on the phone, which broadcasts to HavenCore's `PushReceiver`.
Severity maps to notification priority: `none` / `info` → default,
`warn` → high + short vibration, `alert` → high + long pulsing
vibration. System Do-Not-Disturb is respected.

The wire format and the `session_id` deep-link contract are
documented in [`docs/wire-protocol.md`](docs/wire-protocol.md).

## Configuration

LAN-only. Cleartext HTTP is permitted globally via
`network_security_config.xml`; remote access goes through Tailscale
or a VPN. There is no auth on the agent's `/api/*` or `/ws/chat`
surfaces — do not expose the agent to the open internet without
fronting it with a reverse proxy that adds authentication.

See [`docs/wire-protocol.md`](docs/wire-protocol.md) for the WS
framing and event-shape gotchas the app builds on,
[`docs/voice-assist.md`](docs/voice-assist.md) for the assist-overlay
architecture, [`docs/wake-word.md`](docs/wake-word.md) for the
wall-display / wake-word architecture, and
[`docs/design-system.md`](docs/design-system.md) for color /
typography / shape / motion tokens and component patterns.

## Roadmap

Capabilities not yet shipped:

- **Multi-turn assist** — the overlay handles a single round trip per
  invocation. A multi-turn redesign is the next significant surface.
- **Continuous follow-up in wall-display mode** — wake-word fires
  once per turn today. Re-triggering capture after TTS playback (so
  the second turn doesn't need another "hey selene") is the natural
  next step.
- **Lock-task / true kiosk pinning** for the docked-tablet scenario.
  The current "kiosk mode" flips fullscreen + show-when-locked +
  screen-on but doesn't pin the activity over the launcher. The M11
  install runs as a soft kiosk (boot-completed autostart and
  battery-optimization exemption are wired; lock-task is not, and
  isn't needed when the tablet is physically out of reach).
- **Todo / shopping list** — pending the agent-side `todo.*` MCP
  tools.
- **Reverse-proxy auth** — pending agent-side support; useful when
  remote access becomes real.

## License

LGPL-2.1 — matches the upstream HavenCore repo.

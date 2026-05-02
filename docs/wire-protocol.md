# Wire protocol — the gotchas

The HavenCore agent's WebSocket and REST surfaces are documented in the
master plan and in agent source under
`/home/matt/code/havencore/services/agent/selene_agent/api/`. This file
captures the small set of facts that are not obvious from skimming the
agent's docstrings — the ones that bit (or would have bitten) the Phase 1
implementation. Keep this short; the agent source is the source of truth.

## WS `/ws/chat`

### Discriminator field

The agent dispatches inbound frames on `data.get("type") == "session"`
(see `chat.py` near line 268). Our `HavenJson` config sets
`encodeDefaults = false`, which means a Kotlin default-valued field is
omitted from the encoded JSON. Without `@EncodeDefault` on
`SessionFrame.type`, the discriminator silently disappears and the agent
treats the frame as a no-op (no session bind). All `SessionFrame`s must
serialize `"type":"session"`.

### Event type strings are lowercase

The orchestrator emits `"thinking"`, `"tool_call"`, `"tool_result"`,
`"reasoning"`, `"metric"`, `"done"`, `"error"`, `"summary_reset"`,
`"session"`. Not the upper-snake-case constants (`THINKING`, etc.) used
inside the orchestrator code. Match the lowercase wire form on
`@SerialName`.

### No `RESPONSE_CHUNK`

The master plan listed `RESPONSE_CHUNK` as part of the event taxonomy.
The agent does not emit it. The full assistant text arrives in a single
`done.content` field. A "Thinking…" spinner covers the latency until
`done` lands. Do not write a streaming text renderer expecting chunks.

### `session_id` is honored only on the first frame

Re-binding to a different session mid-stream is silently ignored
(`chat.py` near line 268: "session_id is honored only on the first
frame"). The reconnect supervisor caches the last-seen `session_id` so a
non-clean reconnect can re-bind the same conversation; an explicit
session change requires opening a new WS.

### `idle_timeout` and `device_name` apply on first frame _and_ mid-stream

Unlike `session_id`, these two fields update the orchestrator each time
they appear. We send them on the opening frame only. The sentinel
`idle_timeout: -1` means "never auto-summarize while idle" — without it,
the agent's idle sweep will summarize and reset a phone session after
the configured window.

### Client → server message frame is `{"message":"..."}`

Plain envelope, no type field. The agent's main path treats anything
without `"type":"session"` as a user message (`chat.py` near line 273:
`user_message = data.get("message", "")`).

### `device_action` event (Phase 5)

The agent can ask the device to perform a native action (e.g. set an
alarm). The orchestrator emits a `device_action` event in addition to
the normal `tool_call` / `tool_result` pair when the LLM invokes a
device-targeted tool — the pair stays as the server-side breadcrumb,
the new event is what the device acts on.

```json
{
  "type": "device_action",
  "action": "set_alarm",
  "args": { "hour": 7, "minute": 0, "label": "Standup" },
  "id": "<tool_call_id>",
  "device_id": "<device name from session>"
}
```

- `action` is the tool name (matches the corresponding `tool_call.tool`).
- `args` schema is per-action. For `set_alarm`: required `hour` (0–23)
  and `minute` (0–59); optional `label` (string) and `days_of_week`
  (list of 1=Sun … 7=Sat for repeating alarms).
- `id` ties the event back to the `tool_call.id` so the UI can
  correlate the action card with the tool-call card. May be null.
- `device_id` is informational; the agent already routes the event to
  the session's device via the per-session pubsub, so no client-side
  filtering is needed.

The companion app's `parseChatFrame` lists `device_action` in
`knownEventTypes`; an old client receiving an unknown action name
(future `set_timer`, etc.) renders an inert "(unsupported)" card and
keeps going. Older clients without `device_action` support ignore the
event entirely via the existing `ParsedFrame.Unknown` path.

The Android side requires `<uses-permission
android:name="com.android.alarm.permission.SET_ALARM"/>` to fire the
intent silently with `EXTRA_SKIP_UI=true`.

### `summary_reset` is out-of-band

The agent's session pool delivers `summary_reset` events between or even
during turns via a per-session notification channel
(`session_pool.py:343`). The reducer must not assume reset events
correlate with the active turn — append a `SummaryResetMarker` to the
turn list and keep going.

## REST

### `POST /api/conversations/{session_id}/resume`

Returns OpenAI-format `messages` (list of `{role, content, tool_calls?,
tool_call_id?}` objects). Tool-using assistant turns split into
`role:"assistant"` (with `tool_calls` array) plus one `role:"tool"` per
result. The `arguments` field on each tool_call is a JSON-encoded
**string**, not a nested object — `ResumeMapper.parseArgs` parses it.

OkHttp rejects a null body on POST, and the agent ignores the body — so
send `"".toRequestBody("application/json".toMediaType())`.

### `GET /api/conversations`

One row per **flush**, not per logical session. Each summary reset
creates a new flush row. We list flushes as-is and trust the user's
session_id tap to resume the right one.

### No manual `/summarize` endpoint

`summary_reset` triggers internally on idle-timeout sweep
(`session_pool.py:340`) or context-size threshold
(`orchestrator.py:241`). There is no `POST .../summarize` despite what
the Phase 1 plan's verification step implied. Test the renderer
organically or by spoofing a frame.

## STT / TTS

### `POST /api/stt/transcribe`

Multipart upload, field name `file`, optional `language` and
`response_format` form fields. Proxies the bytes to the upstream
Whisper service; accepts WAV, MP3, M4A (AAC-in-MP4), FLAC, OGG, and
WebM/Opus. Returns `{"text": "...", "language": "..."}`.

**Whisper hallucinates on silent / very short clips.** A recording with
no real speech does not produce empty `text` — it produces a confident
fake string drawn from common YouTube subtitle credits ("Thank you",
"Thanks for watching", "Subtitles by ..."). The empty-text response is
rarer than the hallucinated one. Client-side gating lives in
`MicRecorder.State.Stopped.hasSpeech()` (duration ≥ `MIN_DURATION_MS`
**and** peak amplitude ≥ `MIN_PEAK_AMPLITUDE` from `getMaxAmplitude()`
polling); both `ChatViewModel.transcribeAndSend` and the assist path
call it before invoking `SttApi.transcribe`. Re-calibrate
`MIN_PEAK_AMPLITUDE` against ambient noise if quiet speech is being
false-gated.

### `POST /api/tts/speak`

JSON body `{ text, voice?, format?, speed?, model? }`; defaults
`voice="af_heart"`, `format="mp3"`, `speed=1.0`, `model="tts-1"`.
Returns the audio blob with the **upstream's** Content-Type — libsndfile
may silently downgrade an unsupported requested format (e.g. mp3) to
WAV, so the response header is the source of truth, not the requested
`format`. Empty/whitespace text rejected with 400.

## Push (Phase 4)

### Registration

`POST /api/push/register` with snake-case JSON:

```json
{
  "device_id": "<uuid>",
  "device_label": "Matt's S24",
  "endpoint": "https://ntfy.example.com/UPxxxxxxxxxxxx",
  "platform": "android"
}
```

Idempotent upsert on `device_id`. The companion app generates the
UUIDv4 once on first push-enable and persists it in DataStore
(`push_device_id`) — a fresh ID requires the user to clear app data or
toggle push off/on through a future explicit reset path. `device_label`
is the existing `device_name` setting.

`DELETE /api/push/register/{device_id}` — 200 on success, 404 treated
as success client-side. Best-effort on disable; if the LAN is down,
the agent row goes stale and the agent's pruner cleans it up
eventually (out of scope for this app — handled in the agent repo).

### Push payload envelope

UnifiedPush distributors deliver a single byte buffer (≤ 4096 bytes)
containing the agent's JSON envelope. The companion app decodes with
`ignoreUnknownKeys = true` so additive agent changes don't require an
app release.

```json
{
  "v": 1,
  "type": "autonomy_brief" | "anomaly" | "reminder" | "act_confirm" | "ad_hoc",
  "title": "Selene",
  "body": "<= 3000 chars",
  "session_id": "<uuid>" | null,
  "severity": "none" | "info" | "warn" | "alert"
}
```

- `v` is the schema version; the receiver tolerates unknown fields but
  may drop unknown `v` values.
- `type` is a surface category for future per-channel splitting; v1
  routes everything through the single `havencore_autonomy` channel.
- `severity` maps to `NotificationCompat` priority/vibration:
  `alert`/`warn` → HIGH + vibration; `none`/`info` → DEFAULT. Respects
  system DND (no bypass in v1).
- `body` over 3000 chars is truncated agent-side to leave room inside
  the 4 KB UnifiedPush envelope cap.
- `session_id` is opaque; the app does not validate before using it as
  a deep-link parameter (the chat screen's cold-resume path 404s
  gracefully if the session is gone).

### Tap-through

Notifications carry a `PendingIntent` (`FLAG_IMMUTABLE` +
`FLAG_UPDATE_CURRENT`) targeting `MainActivity` with
`Intent.FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` and
`session_id` as a string extra (`PushNotifier.EXTRA_SESSION_ID =
"session_id"`). The PendingIntent's request code is
`sessionId.hashCode()` so concurrent pushes for distinct sessions
don't collapse.

`MainActivity` reads the extra in both `onCreate` (cold-launch) and
`onNewIntent` (warm), feeding it into a `MutableStateFlow<String?>`.
`HavenNav`'s `LaunchedEffect` keyed on the value navigates to
`chat?sessionId=<sid>` with `popUpTo("history") { inclusive = false }`
+ `launchSingleTop = true` so a back-press from the deep-linked chat
lands on the home destination, then clears the flow.

A push without `session_id` foregrounds the app to whatever route was
last shown (no nav change).

### Distributor agnosticism

The companion app calls `UnifiedPush.register(ctx, INSTANCE_DEFAULT)`
and `UnifiedPush.unregister(ctx, INSTANCE_DEFAULT)` — the connector
library handles distributor selection (the user's saved choice via
`UnifiedPush.saveDistributor`). The endpoint URL the distributor
returns via `onNewEndpoint` is self-contained — the agent stores it
verbatim and POSTs payloads to it. There is no `NTFY_BASE_URL` env var
on the agent and no ntfy URL field in the companion app; the user
configures their server URL inside the ntfy app.

`MessagingReceiver` callbacks run on the binder thread; `onMessage`
posts the notification synchronously (no network), and the lifecycle
callbacks (`onNewEndpoint` / `onRegistrationFailed` / `onUnregistered`)
use `goAsync()` + `Dispatchers.IO` to stay inside the broadcast's
~10-second window while round-tripping to the agent.

## Auth and transport

CORS is `*` and there is no auth middleware on `/api/*` or the WS. App
is LAN-only and `network_security_config.xml` permits cleartext to any
host. Reverse-proxy auth is a follow-up for when remote access becomes
real.

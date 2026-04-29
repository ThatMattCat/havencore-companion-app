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

## Auth and transport

CORS is `*` and there is no auth middleware on `/api/*` or the WS. App
is LAN-only and `network_security_config.xml` permits cleartext to any
host. Reverse-proxy auth is a follow-up for when remote access becomes
real.

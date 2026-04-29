package ai.havencore.companion.net

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Wraps a single logical chat session over [okhttp3.WebSocket], with a
 * supervisor coroutine that opens the socket, forwards parsed events into a
 * [Flow], and reconnects on non-clean close with a 3 / 6 / 12 / 24 / 30 second
 * backoff.
 *
 * The session_id observed on the first server `session` event is cached so
 * subsequent reconnects re-bind the same conversation. Mid-stream re-binds
 * are silently ignored by the agent, so the client tracks identity locally.
 */
class ChatWsSession(sharedClient: OkHttpClient) {

    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data object Connected : Status
        data class Reconnecting(val attempt: Int, val nextDelayMs: Long) : Status
        data class Closed(val code: Int, val reason: String) : Status
        data class Failed(val cause: Throwable) : Status
    }

    // Per-session client: WS holds the read open indefinitely, and a 20 s
    // application-level ping keeps NAT mappings alive on flaky LAN.
    private val client: OkHttpClient = sharedClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile private var currentSocket: WebSocket? = null
    @Volatile private var rememberedSessionId: String? = null
    private var deviceName: String? = null
    private var idleTimeout: Int? = -1
    private var supervisor: Job? = null

    fun connect(
        scope: CoroutineScope,
        baseUrl: String,
        sessionId: String?,
        deviceName: String?,
        idleTimeout: Int? = -1,
    ): Flow<ParsedFrame> {
        close()
        rememberedSessionId = sessionId
        this.deviceName = deviceName
        this.idleTimeout = idleTimeout
        val frames: Channel<ParsedFrame> = Channel(Channel.UNLIMITED)
        val url = wsUrl(baseUrl)
        if (url == null) {
            _status.value = Status.Failed(IllegalArgumentException("invalid baseUrl: $baseUrl"))
            frames.close()
            return frames.receiveAsFlow()
        }
        supervisor = scope.launch {
            try {
                runReconnectLoop(url, frames)
            } finally {
                frames.close()
            }
        }
        return frames.receiveAsFlow()
    }

    fun send(message: String): Boolean {
        val ws = currentSocket ?: return false
        val frame = HavenJson.encodeToString(MessageFrame.serializer(), MessageFrame(message))
        return ws.send(frame)
    }

    fun rememberSessionId(sid: String) {
        rememberedSessionId = sid
    }

    fun close() {
        supervisor?.cancel()
        supervisor = null
        currentSocket?.close(WS_CLOSE_NORMAL, "client closed")
        currentSocket = null
        _status.value = Status.Idle
    }

    private suspend fun runReconnectLoop(url: String, frames: Channel<ParsedFrame>) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            _status.value = Status.Connecting
            val outcome = try {
                openOnce(url, frames) {
                    attempt = 0
                    _status.value = Status.Connected
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "openOnce threw", t)
                OpenOutcome.Failed(t)
            }
            currentSocket = null

            if (outcome is OpenOutcome.Clean) {
                _status.value = Status.Closed(outcome.code, outcome.reason)
                Log.i(TAG, "closed clean code=${outcome.code} reason=${outcome.reason}")
                return
            }
            attempt += 1
            val delayMs = reconnectDelayMs(attempt)
            _status.value = Status.Reconnecting(attempt, delayMs)
            Log.i(TAG, "reconnect attempt=$attempt in ${delayMs}ms (after $outcome)")
            delay(delayMs)
        }
    }

    private suspend fun openOnce(
        url: String,
        frames: Channel<ParsedFrame>,
        onConnected: () -> Unit,
    ): OpenOutcome = suspendCancellableCoroutine { cont ->
        val req = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            private var resumed = false

            private fun resumeWith(outcome: OpenOutcome) {
                if (resumed) return
                resumed = true
                if (cont.isActive) cont.resume(outcome)
            }

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "ws open url=$url")
                currentSocket = ws
                onConnected()
                val frame = SessionFrame(
                    session_id = rememberedSessionId,
                    idle_timeout = idleTimeout,
                    device_name = deviceName?.takeIf { it.isNotBlank() },
                )
                ws.send(HavenJson.encodeToString(SessionFrame.serializer(), frame))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val parsed = parseChatFrame(text)
                if (parsed is ParsedFrame.Known) {
                    val ev = parsed.event
                    if (ev is ChatEvent.Session) {
                        rememberedSessionId = ev.session_id
                        Log.i(TAG, "bound session_id=${ev.session_id}")
                    }
                } else if (parsed is ParsedFrame.Unknown) {
                    Log.w(TAG, "unknown event type=${parsed.type}")
                } else if (parsed is ParsedFrame.Malformed) {
                    Log.w(TAG, "malformed frame: ${parsed.cause.message}")
                }
                frames.trySend(parsed)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                val outcome = if (code == WS_CLOSE_NORMAL) {
                    OpenOutcome.Clean(code, reason)
                } else {
                    OpenOutcome.Lost(code, reason)
                }
                resumeWith(outcome)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure: ${t.message}")
                resumeWith(OpenOutcome.Failed(t))
            }
        }

        val ws = client.newWebSocket(req, listener)
        cont.invokeOnCancellation {
            ws.cancel()
        }
    }

    private fun reconnectDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 3_000L
        2 -> 6_000L
        3 -> 12_000L
        4 -> 24_000L
        else -> 30_000L
    }

    private fun wsUrl(baseUrl: String): String? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        // http -> ws, https -> wss; preserves any other scheme verbatim.
        val scheme = trimmed.replaceFirst(Regex("^http"), "ws")
        return "$scheme/ws/chat"
    }

    private sealed interface OpenOutcome {
        data class Clean(val code: Int, val reason: String) : OpenOutcome
        data class Lost(val code: Int, val reason: String) : OpenOutcome
        data class Failed(val cause: Throwable) : OpenOutcome
    }

    private companion object {
        const val TAG = "ChatWs"
        const val WS_CLOSE_NORMAL = 1000
    }
}

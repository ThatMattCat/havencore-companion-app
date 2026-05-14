package ai.havencore.companion.voice.avatar

import ai.havencore.companion.AppContainer
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.data.DeviceAction
import ai.havencore.companion.net.ChatEvent
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ParsedFrame
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Runs a single wake-triggered voice turn from a prerecorded WAV (the
 * capture handed off by [ai.havencore.companion.wakeword.MicrophoneForegroundService])
 * with no MainActivity / VoiceInteractionSession involvement. Drives
 * [AvatarController] phase transitions and feeds [VisemeScheduler] when
 * TTS bytes arrive with an X-Visemes header.
 *
 * Sibling of [ai.havencore.companion.voice.HavenAssistSession] — same
 * STT → WS → TTS shape, just sourced from a static file and surfacing
 * progress via the avatar overlay rather than an Activity.
 *
 * Per-turn fresh [ChatWsSession]: avoids stealing the shared foreground
 * chat socket while still binding to the same lastSessionId, so voice
 * turns from any entry point land in the same conversation row.
 */
class VoiceTurnRunner(
    private val container: AppContainer,
    private val controller: AvatarController,
    private val visemeScheduler: VisemeScheduler,
) {

    /**
     * Runs the full turn. Suspends until TTS playback finishes, the agent
     * errors, or the network calls time out. The caller (the
     * AvatarOverlayService) starts this from its own scope; cancellation
     * propagates and tears down the per-turn WS.
     */
    suspend fun run(captureFile: File): Unit = coroutineScope {
        val cfg = container.settings.configFlow.first()
        if (cfg.baseUrl.isBlank()) {
            controller.setError("Configure server URL in HavenCore Settings.")
            return@coroutineScope
        }
        val sid = container.settings.lastSessionId()

        val ws = ChatWsSession(container.http)
        val replyContent = CompletableDeferred<String?>()
        try {
            controller.setPhase(AvatarPhase.Listening)
            val frames = ws.connect(
                scope = this,
                baseUrl = cfg.baseUrl,
                sessionId = sid,
                deviceName = cfg.deviceName,
                idleTimeout = -1,
            )
            launch {
                frames.collect { frame -> reduce(frame, replyContent, this@coroutineScope) }
            }
            withTimeoutOrNull(WS_CONNECT_TIMEOUT_MS) {
                ws.status.filterIsInstance<ChatWsSession.Status.Connected>().first()
            } ?: run {
                controller.setError("Couldn't reach server.")
                return@coroutineScope
            }

            // STT against the captured WAV.
            controller.setPhase(AvatarPhase.Thinking)
            val transcript = container.sttApi
                .transcribe(cfg.baseUrl, captureFile)
                .getOrElse { t ->
                    Log.w(TAG, "STT failed", t)
                    controller.setError("Couldn't transcribe — ${t.message ?: "unknown"}")
                    return@coroutineScope
                }
            if (transcript.isBlank()) {
                Log.i(TAG, "transcribe returned blank — finishing")
                controller.setPhase(AvatarPhase.Idle)
                return@coroutineScope
            }
            controller.setCaption(transcript)

            if (!ws.send(transcript)) {
                controller.setError("Lost server connection.")
                return@coroutineScope
            }

            val reply = withTimeoutOrNull(REPLY_TIMEOUT_MS) { replyContent.await() }
            if (reply.isNullOrBlank()) {
                if (reply == null) {
                    Log.w(TAG, "no Done frame within ${REPLY_TIMEOUT_MS}ms")
                    controller.setError("Server didn't reply in time.")
                }
                return@coroutineScope
            }

            // TTS round-trip + lip-sync handoff. Set the timeline *before*
            // play() so the first viseme tick lands on cue.
            val spoken = container.ttsApi.speak(cfg.baseUrl, reply).getOrNull()
            if (spoken == null) {
                Log.w(TAG, "TTS failed; finishing without playback")
                controller.setPhase(AvatarPhase.Idle)
                return@coroutineScope
            }
            visemeScheduler.setTimeline(spoken.visemes)
            controller.setPhase(AvatarPhase.Speaking)
            controller.setCaption(reply.take(180))
            container.ttsPlayer.play(spoken.bytes, spoken.contentType)

            // Wait for playback to leave Loading/Playing.
            container.ttsPlayer.state
                .filter { it !is TtsPlayer.State.Loading && it !is TtsPlayer.State.Playing }
                .first()
            visemeScheduler.setTimeline(null)
            controller.setPhase(AvatarPhase.Idle)
        } finally {
            runCatching { ws.close() }
        }
    }

    private suspend fun reduce(
        frame: ParsedFrame,
        replyContent: CompletableDeferred<String?>,
        turnScope: kotlinx.coroutines.CoroutineScope,
    ) {
        when (frame) {
            is ParsedFrame.Known -> when (val ev = frame.event) {
                is ChatEvent.Session -> {
                    container.settings.setLastSessionId(ev.session_id)
                }
                is ChatEvent.Done -> {
                    if (!replyContent.isCompleted) replyContent.complete(ev.content)
                }
                is ChatEvent.Err -> {
                    Log.w(TAG, "agent err: ${ev.error}")
                    if (!replyContent.isCompleted) replyContent.complete(null)
                    controller.setError(ev.error.take(200))
                }
                is ChatEvent.DeviceAction -> {
                    val parsed = DeviceAction.fromEvent(ev)
                    if (parsed != null) {
                        // Fire on the turn scope so it gets cancelled if
                        // the user dismisses the overlay mid-action.
                        // Matches HavenAssistSession's pattern.
                        turnScope.launch(Dispatchers.IO) {
                            container.deviceActionDispatcher.dispatch(parsed)
                        }
                    } else {
                        Log.w(TAG, "device_action with unsupported action=${ev.action}")
                    }
                }
                is ChatEvent.ToolCall,
                is ChatEvent.ToolResult,
                is ChatEvent.Reasoning,
                is ChatEvent.Metric,
                is ChatEvent.SummaryReset,
                is ChatEvent.Thinking -> {
                    // No phase change — Thinking is already set before send.
                }
            }
            is ParsedFrame.Unknown -> Log.w(TAG, "unknown event type=${frame.type}")
            is ParsedFrame.Malformed -> Log.w(TAG, "malformed frame: ${frame.cause.message}")
        }
    }

    private companion object {
        const val TAG = "Avatar:Turn"
        const val WS_CONNECT_TIMEOUT_MS = 5_000L
        const val REPLY_TIMEOUT_MS = 60_000L
    }
}

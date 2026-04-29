package ai.havencore.companion.voice

import ai.havencore.companion.HavenCoreApp
import ai.havencore.companion.MainActivity
import ai.havencore.companion.audio.MicRecorder
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.net.ChatEvent
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ParsedFrame
import ai.havencore.companion.ui.theme.HavenCoreTheme
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-invocation overlay session orchestrating the full round-trip:
 * RECORD_AUDIO gate → settings → WS connect (fresh ChatWsSession sharing
 * the OkHttp client, bound to lastSessionId) → mic capture under
 * Phase.Listening (user-stopped via the overlay's Stop button) →
 * MicRecorder.State.Stopped.hasSpeech() gate → STT →
 * `ws.send(transcript)` → reduce inbound frames into Phase.Thinking /
 * tool count chip / Phase.Replying → TtsApi.speak → TtsPlayer.play →
 * 1.5 s grace after audio ends → finish().
 *
 * Reuses the foreground app's [HavenCoreApp.container] (shared
 * MicRecorder, TtsPlayer, OkHttp client) but constructs its own
 * [ChatWsSession] so an active foreground chat WS is not disturbed.
 * Both sockets bind to the same lastSessionId so voice turns from any
 * entry point land in the same History row.
 */
class HavenAssistSession(context: Context) : VoiceInteractionSession(context) {

    private val app get() = (context.applicationContext as HavenCoreApp).container
    private val state = MutableStateFlow(AssistUiState())
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ws by lazy { ChatWsSession(app.http) }

    private lateinit var lifecycleOwner: AssistLifecycleOwner
    private lateinit var cfg: ServerConfig

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "session onCreate")
        lifecycleOwner = AssistLifecycleOwner().also { it.onCreate() }
        // The session Dialog's Window defaults to WRAP_CONTENT, which would
        // shrink the overlay to just the sheet's measured size. Force the
        // Window to fill the screen so the scrim region above the sheet is
        // hit-testable and tap-to-dismiss works. The scrim itself is drawn
        // by Compose. Keep FLAG_SHOW_WHEN_LOCKED for lockscreen invocations.
        window.window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun onCreateContentView(): View {
        Log.i(TAG, "onCreateContentView")
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                HavenCoreTheme {
                    AssistOverlay(
                        stateFlow = state,
                        onDismiss = { finish() },
                        onOpenApp = { openHavenCoreAndFinish() },
                        onStopMic = { stopMicFromOverlay() },
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "onShow flags=$showFlags")
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
        startInvocation()
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "onHide")
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
    }

    override fun onDestroy() {
        Log.i(TAG, "session onDestroy")
        sessionScope.cancel()
        runCatching { ws.close() }
        runCatching { app.mic.cancel() }
        runCatching { app.ttsPlayer.stop() }
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun startInvocation() {
        sessionScope.launch {
            // 1. RECORD_AUDIO gate. Runtime permissions cannot be requested
            //    from a VoiceInteractionSession, so we surface a deep-link
            //    to the chat screen and auto-dismiss.
            if (!hasRecordAudio()) {
                Log.w(TAG, "RECORD_AUDIO not granted — surfacing PermissionMissing")
                state.update { it.copy(phase = Phase.PermissionMissing) }
                delay(2_500)
                finish()
                return@launch
            }

            // 2. Read settings — baseUrl + deviceName + lastSessionId.
            cfg = app.settings.configFlow.first()
            val sid = app.settings.lastSessionId()
            if (cfg.baseUrl.isBlank()) {
                Log.w(TAG, "baseUrl is blank — bailing")
                state.update {
                    it.copy(
                        phase = Phase.Error,
                        errorMessage = "Open HavenCore and configure the server URL first.",
                    )
                }
                delay(2_500)
                finish()
                return@launch
            }

            // 3. Open the WS, kick off frame consumer immediately, then
            //    block on Connected before starting mic.
            state.update { it.copy(phase = Phase.Connecting) }
            val frames = ws.connect(
                scope = sessionScope,
                baseUrl = cfg.baseUrl,
                sessionId = sid,
                deviceName = cfg.deviceName,
                idleTimeout = -1,
            )
            sessionScope.launch {
                frames.collect { frame -> reduce(frame) }
            }
            ws.status.filterIsInstance<ChatWsSession.Status.Connected>().first()

            // 4. Reset the shared MicRecorder so a stale Stopped from a
            //    prior chat-screen capture does not race the collector.
            app.mic.cancel()
            state.update { it.copy(phase = Phase.Listening) }
            app.mic.start().onFailure { t ->
                Log.w(TAG, "mic start failed", t)
                state.update {
                    it.copy(phase = Phase.Error, errorMessage = t.message ?: "Mic error")
                }
                delay(2_500)
                finish()
                return@launch
            }

            // 5. Wait for the user to tap Stop (which calls app.mic.stop()
            //    and produces a Stopped event).
            val stopped = app.mic.state
                .filterIsInstance<MicRecorder.State.Stopped>()
                .first()
            onMicStopped(stopped)
        }
    }

    private fun stopMicFromOverlay() {
        sessionScope.launch {
            app.mic.stop().exceptionOrNull()?.let {
                Log.w(TAG, "mic stop failed", it)
            }
        }
    }

    private suspend fun onMicStopped(stopped: MicRecorder.State.Stopped) {
        if (!stopped.hasSpeech()) {
            Log.i(TAG, "no speech detected (durMs=${stopped.durationMs} peak=${stopped.peakAmplitude})")
            state.update { it.copy(phase = Phase.NoSpeech) }
            delay(1_500)
            finish()
            return
        }
        state.update { it.copy(phase = Phase.Transcribing) }
        val transcript = app.sttApi.transcribe(cfg.baseUrl, stopped.file).getOrElse { t ->
            Log.w(TAG, "transcribe failed", t)
            state.update {
                it.copy(phase = Phase.Error, errorMessage = t.message ?: "Transcription failed")
            }
            delay(2_500)
            finish()
            return
        }
        if (transcript.isBlank()) {
            state.update { it.copy(phase = Phase.NoSpeech) }
            delay(1_500)
            finish()
            return
        }
        state.update { it.copy(phase = Phase.Thinking, transcript = transcript) }
        if (!ws.send(transcript)) {
            Log.w(TAG, "ws.send returned false — socket gone")
            state.update { it.copy(phase = Phase.Error, errorMessage = "Not connected") }
            delay(2_500)
            finish()
            return
        }
        // From here, the reducer drives state — Done triggers speakAndFinish.
    }

    private fun reduce(frame: ParsedFrame) {
        when (frame) {
            is ParsedFrame.Known -> when (val ev = frame.event) {
                is ChatEvent.Session -> {
                    // Persist for cross-invocation continuity (matches
                    // ChatViewModel's path).
                    sessionScope.launch { app.settings.setLastSessionId(ev.session_id) }
                }
                is ChatEvent.ToolCall -> {
                    state.update { it.copy(toolCount = it.toolCount + 1) }
                }
                is ChatEvent.Done -> {
                    state.update { it.copy(phase = Phase.Replying, reply = ev.content) }
                    sessionScope.launch { speakAndFinish(ev.content) }
                }
                is ChatEvent.Err -> {
                    state.update {
                        it.copy(phase = Phase.Error, errorMessage = ev.error)
                    }
                    sessionScope.launch {
                        delay(2_500)
                        finish()
                    }
                }
                is ChatEvent.Thinking,
                is ChatEvent.ToolResult,
                is ChatEvent.Reasoning,
                is ChatEvent.Metric,
                is ChatEvent.SummaryReset -> {
                    // Slim overlay: thinking spinner is already showing,
                    // tool-result expanders / reasoning / metrics / summary
                    // resets all live in the chat screen, not here.
                }
            }
            is ParsedFrame.Unknown -> Log.w(TAG, "unknown event type=${frame.type}")
            is ParsedFrame.Malformed -> Log.w(TAG, "malformed frame: ${frame.cause.message}")
        }
    }

    private suspend fun speakAndFinish(text: String) {
        if (text.isBlank()) {
            delay(500)
            finish()
            return
        }
        val spoken = app.ttsApi.speak(cfg.baseUrl, text).getOrNull()
        if (spoken == null) {
            Log.w(TAG, "TTS failed; finishing without playback")
            delay(500)
            finish()
            return
        }
        app.ttsPlayer.play(spoken.bytes, spoken.contentType)
        // Wait for the player to leave Loading/Playing (i.e. Idle or Error).
        app.ttsPlayer.state
            .filter { it !is TtsPlayer.State.Loading && it !is TtsPlayer.State.Playing }
            .first()
        delay(1_500)
        finish()
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun openHavenCoreAndFinish() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        finish()
    }

    private companion object {
        const val TAG = "Voice:Sess"
    }
}

package ai.havencore.companion.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays a TTS audio blob through Media3 [ExoPlayer]. Uses USAGE_MEDIA +
 * CONTENT_TYPE_SPEECH because ExoPlayer's automatic audio focus handler
 * only accepts USAGE_MEDIA and USAGE_GAME — passing USAGE_ASSISTANT with
 * handleAudioFocus=true throws IllegalArgumentException at construction.
 * The semantic difference (vs USAGE_ASSISTANT) is mostly around car-mode
 * audio bus routing; for a phone companion app, USAGE_MEDIA matches the
 * media stream, which is the standard A2DP target.
 *
 * ExoPlayer is single-threaded — all method calls must come from the
 * Application thread (the looper the player was built on, which is the
 * main thread because [AppContainer] is constructed in
 * `HavenCoreApp.onCreate()`). This wrapper marshals every external entry
 * point onto the main looper.
 */
class TtsPlayer(private val ctx: Context) {

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data object Playing : State
        data class Error(val cause: Throwable) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val player: ExoPlayer = ExoPlayer.Builder(ctx)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (p.playWhenReady) _state.value = State.Playing
                        }
                        Player.STATE_ENDED -> {
                            _state.value = State.Idle
                        }
                        Player.STATE_BUFFERING, Player.STATE_IDLE -> {
                            // Loading/Idle are driven by play() / stop() entry points.
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "ExoPlayer error", error)
                    _state.value = State.Error(error)
                }
            })
        }

    fun play(audioBytes: ByteArray, contentType: String) {
        _state.value = State.Loading
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    pruneCacheDir()
                    val ext = extensionFor(contentType)
                    val outDir = File(ctx.cacheDir, "voice").apply { mkdirs() }
                    val out = File(outDir, "tts-${System.currentTimeMillis()}.$ext")
                    out.writeBytes(audioBytes)
                    out
                }
            } catch (t: Throwable) {
                Log.w(TAG, "tts cache write failed", t)
                _state.value = State.Error(t)
                return@launch
            }
            // Already on Main.immediate; safe to touch the player.
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(file.toUri()))
            player.prepare()
            player.playWhenReady = true
            Log.i(TAG, "play ${file.name} (${audioBytes.size} bytes / $contentType)")
        }
    }

    fun stop() {
        mainHandler.post {
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                player.stop()
            }
            player.clearMediaItems()
            _state.value = State.Idle
        }
    }

    fun release() {
        mainHandler.post {
            runCatching { player.release() }
            scope.cancel()
        }
    }

    private fun extensionFor(contentType: String): String {
        val ct = contentType.lowercase().substringBefore(';').trim()
        return when (ct) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave", "audio/l16" -> "wav"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/aac" -> "aac"
            "audio/flac" -> "flac"
            else -> "bin"
        }
    }

    private fun pruneCacheDir() {
        val dir = File(ctx.cacheDir, "voice")
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
        dir.listFiles()?.forEach { f ->
            // Don't prune .m4a (mic captures) here — MicRecorder owns those.
            if (f.name.startsWith("tts-") && f.lastModified() < cutoff) f.delete()
        }
    }

    private companion object {
        const val TAG = "TtsPlay"
        const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}

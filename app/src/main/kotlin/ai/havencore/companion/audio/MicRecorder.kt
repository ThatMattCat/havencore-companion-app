package ai.havencore.companion.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * AAC-in-MP4 capture via MediaRecorder. Records mono / 16 kHz / 32 kbps from
 * the VOICE_COMMUNICATION source, which enables AEC/AGC/NS where the device
 * supports them.
 *
 * On API 31+ devices with a connected Bluetooth headset, capture is routed to
 * the headset mic via [AudioManager.setCommunicationDevice] when the headset
 * exposes a TYPE_BLUETOOTH_SCO communication device. Routing is best-effort:
 * any failure logs and falls back to the built-in mic.
 *
 * Tracks peak amplitude during capture via [MediaRecorder.getMaxAmplitude] so
 * callers can gate uploads on [State.Stopped.hasSpeech] before hitting STT —
 * Whisper hallucinates plausible subtitle-credit text on silence, so the
 * client filters silent / very short clips before they ever leave the device.
 */
class MicRecorder(private val ctx: Context) {

    sealed interface State {
        data object Idle : State
        data object Recording : State
        data class Stopped(
            val file: File,
            val durationMs: Long,
            val peakAmplitude: Int,
        ) : State {
            fun hasSpeech(): Boolean =
                durationMs >= MIN_DURATION_MS && peakAmplitude >= MIN_PEAK_AMPLITUDE
        }
        data class Error(val cause: Throwable) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val audioManager: AudioManager =
        ctx.getSystemService(AudioManager::class.java)

    private val pollScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    private val peakAmplitude = AtomicInteger(0)

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt: Long = 0L
    private var routedToBt: Boolean = false

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (recorder != null) {
            return@withContext Result.failure(IllegalStateException("recorder is busy"))
        }
        if (!hasRecordAudio()) {
            return@withContext Result.failure(SecurityException("RECORD_AUDIO not granted"))
        }
        runCatching {
            pruneCacheDir()
            val outDir = File(ctx.cacheDir, "voice").apply { mkdirs() }
            val file = File(outDir, "mic-${System.currentTimeMillis()}.m4a")
            currentFile = file

            tryRouteCaptureToBt()

            // The no-arg MediaRecorder constructor is deprecated on API 31+;
            // the Context-taking overload is required.
            val rec = MediaRecorder(ctx).apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startedAt = System.currentTimeMillis()
            startAmplitudePoll(rec)
            _state.value = State.Recording
            Log.i(TAG, "started → ${file.absolutePath}")
            Unit
        }.onFailure { t ->
            Log.w(TAG, "start failed", t)
            stopAmplitudePoll()
            cleanup(deleteFile = true)
            _state.value = State.Error(t)
        }
    }

    suspend fun stop(): Result<File> = withContext(Dispatchers.IO) {
        val rec = recorder ?: return@withContext Result.failure(IllegalStateException("not recording"))
        val file = currentFile ?: return@withContext Result.failure(IllegalStateException("no output file"))
        // Cancel the poller before stopping the recorder — getMaxAmplitude on
        // a stopped MediaRecorder throws IllegalStateException.
        pollJob?.cancelAndJoin()
        pollJob = null
        val peak = peakAmplitude.getAndSet(0)
        runCatching {
            rec.stop()
            rec.release()
            recorder = null
            clearBtRoute()
            val durationMs = System.currentTimeMillis() - startedAt
            currentFile = null
            startedAt = 0L
            _state.value = State.Stopped(file, durationMs, peak)
            Log.i(TAG, "stopped len=${file.length()} bytes durMs=$durationMs peak=$peak")
            file
        }.onFailure { t ->
            Log.w(TAG, "stop failed", t)
            cleanup(deleteFile = true)
            _state.value = State.Error(t)
        }
    }

    fun cancel() {
        pollJob?.cancel()
        pollJob = null
        peakAmplitude.set(0)
        val rec = recorder
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        cleanup(deleteFile = true)
        _state.value = State.Idle
    }

    private fun startAmplitudePoll(rec: MediaRecorder) {
        peakAmplitude.set(0)
        // Documented behavior: the first call after start() returns 0 — discard.
        runCatching { rec.maxAmplitude }
        pollJob = pollScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val cur = runCatching { rec.maxAmplitude }.getOrDefault(0)
                if (cur > peakAmplitude.get()) peakAmplitude.set(cur)
            }
        }
    }

    private fun stopAmplitudePoll() {
        pollJob?.cancel()
        pollJob = null
        peakAmplitude.set(0)
    }

    private fun cleanup(deleteFile: Boolean) {
        recorder = null
        clearBtRoute()
        if (deleteFile) {
            currentFile?.takeIf { it.exists() }?.delete()
        }
        currentFile = null
        startedAt = 0L
    }

    private fun tryRouteCaptureToBt() {
        if (!hasBluetoothConnect()) {
            // Without BLUETOOTH_CONNECT, availableCommunicationDevices is
            // unreadable on API 31+. Skip routing rather than crash.
            return
        }
        runCatching {
            val btMic = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (btMic != null) {
                val ok = audioManager.setCommunicationDevice(btMic)
                routedToBt = ok
                Log.i(TAG, "routing capture to ${btMic.productName} (ok=$ok)")
            }
        }.onFailure { t ->
            Log.w(TAG, "BT capture routing failed; falling back to built-in mic", t)
            routedToBt = false
        }
    }

    private fun clearBtRoute() {
        if (!routedToBt) return
        runCatching { audioManager.clearCommunicationDevice() }
            .onFailure { Log.w(TAG, "clearCommunicationDevice failed", it) }
        routedToBt = false
    }

    private fun pruneCacheDir() {
        val dir = File(ctx.cacheDir, "voice")
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothConnect(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MicRec"
        const val ONE_HOUR_MS = 60L * 60L * 1000L
        const val MIN_DURATION_MS = 600L
        // Calibrated 2026-04-29 against ambient room noise; tune per the
        // verify step if quiet speech is being false-gated.
        const val MIN_PEAK_AMPLITUDE = 800
        const val POLL_INTERVAL_MS = 100L
    }
}

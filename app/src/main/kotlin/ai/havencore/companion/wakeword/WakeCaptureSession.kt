package ai.havencore.companion.wakeword

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Captures one post-wake utterance to a WAV file, ending when Silero VAD
 * reports sustained silence.
 *
 * Owns its own [AudioRecord] — the foreground service must release the
 * wake-word engine (which also holds an AudioRecord) before invoking this.
 * Single-owner mic; no concurrent access.
 *
 * 16 kHz mono PCM-16, [MediaRecorder.AudioSource.MIC] — same source the
 * wake-word engine uses while listening. Galaxy S24 (and likely other
 * Samsung devices) routes AudioRecord(VOICE_COMMUNICATION) through a
 * degraded path when transitioning out of an active MIC-source recording
 * — the resulting WAV is effectively silent. Matching the source avoids
 * the transition entirely; the engine's AudioRecord is released, the
 * capture AudioRecord opens on the same source, and the mic continues to
 * produce normal audio. We lose AEC/AGC/NS vs VOICE_COMMUNICATION, but at
 * close range with a short utterance this is negligible.
 *
 * Endpointing contract:
 *  - Pre-roll: append up to [PRE_ROLL_MS] of audio that arrived before wake
 *    fired (caller supplies via [preRollPcm]) so the start of the wake-word
 *    or the first phoneme of the request isn't clipped.
 *  - Speech latch: only start the silence countdown after Silero has
 *    reported speech for [MIN_SPEECH_FRAMES] frames; protects against
 *    immediate-silence false positives at session start.
 *  - End-of-utterance: after the speech latch, [SILENCE_FRAMES_TO_END]
 *    consecutive sub-threshold frames terminate.
 *  - Hard cap: [MAX_CAPTURE_MS] absolute ceiling.
 */
class WakeCaptureSession(
    private val ctx: Context,
    private val vad: SileroVad,
) {

    data class Result(
        val file: File,
        val durationMs: Long,
        val hadSpeech: Boolean,
    )

    @SuppressLint("MissingPermission")
    suspend fun capture(
        outDir: File,
        preRollPcm: ShortArray? = null,
        speechThreshold: Float = DEFAULT_SPEECH_THRESHOLD,
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        if (!hasRecordAudio()) {
            return@withContext kotlin.Result.failure(
                SecurityException("RECORD_AUDIO not granted"),
            )
        }
        outDir.mkdirs()
        val outFile = File(outDir, "wake-${System.currentTimeMillis()}.wav")
        val writer = PcmWavWriter(outFile, sampleRate = SAMPLE_RATE_HZ, channels = 1)
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufBytes = maxOf(minBuf, FRAME_SAMPLES * 2 * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufBytes,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            writer.close()
            outFile.delete()
            return@withContext kotlin.Result.failure(
                IllegalStateException("AudioRecord failed to initialize"),
            )
        }

        vad.reset()
        val frameShort = ShortArray(FRAME_SAMPLES)
        val frameFloat = FloatArray(FRAME_SAMPLES)
        val started = System.currentTimeMillis()
        var speechSeen = 0
        var silenceRun = 0
        var totalFrames = 0
        var latched = false

        try {
            record.startRecording()
            if (preRollPcm != null && preRollPcm.isNotEmpty()) {
                val cap = (PRE_ROLL_MS * SAMPLE_RATE_HZ / 1000)
                val start = maxOf(0, preRollPcm.size - cap)
                writer.writeFrame(preRollPcm, preRollPcm.size - start)
            }
            while (true) {
                val n = record.read(frameShort, 0, FRAME_SAMPLES)
                if (n <= 0) continue
                writer.writeFrame(frameShort, n)
                totalFrames++
                for (i in 0 until n) frameFloat[i] = frameShort[i] / 32768f
                if (n < FRAME_SAMPLES) {
                    for (i in n until FRAME_SAMPLES) frameFloat[i] = 0f
                }
                val prob = vad.processFrame(frameFloat)
                val elapsed = System.currentTimeMillis() - started
                if (elapsed > MAX_CAPTURE_MS) break
                if (prob >= speechThreshold) {
                    speechSeen++
                    silenceRun = 0
                    if (speechSeen >= MIN_SPEECH_FRAMES) latched = true
                } else if (latched) {
                    silenceRun++
                    if (silenceRun >= SILENCE_FRAMES_TO_END) break
                }
            }
            val durationMs = System.currentTimeMillis() - started
            Log.i(
                TAG,
                "capture done frames=$totalFrames speech=$speechSeen " +
                    "latched=$latched durMs=$durationMs file=${outFile.name}",
            )
            kotlin.Result.success(
                Result(outFile, durationMs, hadSpeech = latched),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "capture failed", t)
            kotlin.Result.failure(t)
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            writer.close()
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "WakeWord:Capture"
        const val SAMPLE_RATE_HZ = SileroVad.SAMPLE_RATE_HZ
        const val FRAME_SAMPLES = SileroVad.FRAME_SAMPLES_16K
        const val FRAME_MS = (FRAME_SAMPLES * 1000) / SAMPLE_RATE_HZ // 32 ms

        // ~700 ms of silence with the speech latch armed = end of utterance.
        const val SILENCE_FRAMES_TO_END = 22
        // ~150 ms of speech before we trust the silence countdown.
        const val MIN_SPEECH_FRAMES = 5
        const val MAX_CAPTURE_MS = 10_000L
        const val PRE_ROLL_MS = 200
        const val DEFAULT_SPEECH_THRESHOLD = 0.5f
    }
}

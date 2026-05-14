package ai.havencore.companion.voice.avatar

import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parsed Rhubarb Lip Sync viseme timeline. The TTS endpoint emits this as a
 * base64-encoded JSON string in the ``X-Visemes`` response header — see
 * ``services/text-to-speech/app/main.py`` for the contract and
 * ``docs/avatar-overlay.md`` for the full pipeline.
 *
 * Cue values use the Preston-Blair 9-shape alphabet: A B C D E F G H X.
 * X is silence (mouth closed). [VisemeScheduler] maps each value to a
 * [MouthShape] with target ParamMouthOpenY / ParamMouthForm values.
 */
@Serializable
data class VisemeTimeline(
    val metadata: Metadata = Metadata(),
    val mouthCues: List<Cue> = emptyList(),
) {
    @Serializable
    data class Metadata(val duration: Double = 0.0)

    @Serializable
    data class Cue(val start: Double, val end: Double, val value: String)

    fun cueAt(positionSec: Double): Cue? {
        // Most utterances have <50 cues; linear scan is faster than building
        // an index and avoids edge cases at gap boundaries.
        for (cue in mouthCues) {
            if (positionSec >= cue.start && positionSec < cue.end) return cue
        }
        return null
    }

    companion object {
        private const val TAG = "VisemeTimeline"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Decode a base64 JSON header (the ``X-Visemes`` value) into a
         * [VisemeTimeline]. Returns null on any decode/parse failure — the
         * caller falls back to closed-mouth playback.
         */
        fun fromBase64(encoded: String): VisemeTimeline? {
            return try {
                val raw = Base64.decode(encoded, Base64.DEFAULT)
                json.decodeFromString(serializer(), String(raw, Charsets.UTF_8))
            } catch (t: Throwable) {
                Log.w(TAG, "viseme timeline decode failed: ${t.message}")
                null
            }
        }
    }
}

/**
 * Live2D mouth-parameter target. [openY] feeds ``ParamMouthOpenY`` (0..1);
 * [form] feeds ``ParamMouthForm`` (-1..1, where negative = pursed/round).
 * The JS bridge clamps to valid ranges, so out-of-bounds values here are
 * silently corrected on the WebView side.
 */
data class MouthShape(val openY: Float, val form: Float) {
    companion object {
        val Closed = MouthShape(0f, 0f)
    }
}

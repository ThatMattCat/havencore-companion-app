package ai.havencore.companion.net

import ai.havencore.companion.voice.avatar.VisemeTimeline
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class SpeakRequest(
    val text: String,
    val voice: String,
    val format: String,
    val speed: Float,
)

data class Spoken(
    val bytes: ByteArray,
    val contentType: String,
    val visemes: VisemeTimeline? = null,
) {
    // Equality on bytes is rarely useful and would copy on every call; override
    // to compare by reference / metadata only.
    override fun equals(other: Any?): Boolean =
        other is Spoken && other.contentType == contentType && other.bytes === bytes
    override fun hashCode(): Int = 31 * contentType.hashCode() + System.identityHashCode(bytes)
}

class TtsApi(sharedClient: OkHttpClient) {

    private val client: OkHttpClient = sharedClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun speak(
        baseUrl: String,
        text: String,
        voice: String = "af_heart",
        format: String = "mp3",
        speed: Float = 1.0f,
    ): Result<Spoken> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        // Agent rejects empty/whitespace text with 400; cheaper to skip.
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("text is empty"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/tts/speak"
        val payload = HavenJson.encodeToString(
            SpeakRequest.serializer(),
            SpeakRequest(text = trimmed, voice = voice, format = format, speed = speed),
        )
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = resp.body?.string()?.take(200).orEmpty()
                    error("HTTP ${resp.code}${if (detail.isNotBlank()) ": $detail" else ""}")
                }
                val bytes = resp.body?.bytes() ?: error("empty TTS response body")
                // libsndfile may downgrade mp3 -> wav silently; the response
                // header is the source of truth for the actual codec.
                val contentType = resp.header("Content-Type") ?: "audio/mpeg"
                // Optional Rhubarb viseme timeline (server-side soft dep).
                val rawHeader = resp.header("X-Visemes")
                val visemes = rawHeader?.let(VisemeTimeline::fromBase64)
                Log.i(
                    "TtsApi",
                    "speak ok: ${bytes.size}B $contentType, X-Visemes=" +
                        (if (rawHeader == null) "absent"
                        else "${rawHeader.length}B b64 → ${visemes?.mouthCues?.size ?: 0} cues"),
                )
                Spoken(bytes = bytes, contentType = contentType, visemes = visemes)
            }
        }
    }
}

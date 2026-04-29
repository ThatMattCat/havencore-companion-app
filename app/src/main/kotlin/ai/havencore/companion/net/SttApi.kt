package ai.havencore.companion.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class TranscribeResponse(
    val text: String = "",
    val language: String? = null,
)

class SttApi(sharedClient: OkHttpClient) {

    // Whisper inference can take several seconds even on LAN; the upstream
    // proxy's own timeout is 120 s. Bump call/write off the shared 10 s read
    // timeout so multipart upload + inference fits.
    private val client: OkHttpClient = sharedClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        baseUrl: String,
        file: File,
        language: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file is empty or missing"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/stt/transcribe"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody("audio/mp4".toMediaType()),
            )
            .also { if (!language.isNullOrBlank()) it.addFormDataPart("language", language) }
            .addFormDataPart("response_format", "json")
            .build()
        val req = Request.Builder().url(url).post(body).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = resp.body?.string()?.take(200).orEmpty()
                    error("HTTP ${resp.code}${if (detail.isNotBlank()) ": $detail" else ""}")
                }
                val payload = resp.body?.string().orEmpty()
                val decoded = HavenJson.decodeFromString(TranscribeResponse.serializer(), payload)
                decoded.text.trim()
            }
        }
    }
}

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
private data class UploadResponse(
    val ok: Boolean = false,
    val image_url: String = "",
    val expires_at: Double = 0.0,
)

data class CompanionUploadAck(
    val imageUrl: String,
    val expiresAt: Double,
)

/**
 * Posts a captured photo to the agent's `/api/companion/upload` endpoint.
 * Mirrors [SttApi.transcribe] in shape: shared OkHttp client with a longer
 * call/read timeout to absorb the multipart upload + server-side handoff.
 */
class CompanionUploadApi(sharedClient: OkHttpClient) {

    private val client: OkHttpClient = sharedClient.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun upload(
        baseUrl: String,
        toolCallId: String,
        deviceId: String?,
        file: File,
        mime: String = "image/jpeg",
    ): Result<CompanionUploadAck> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Photo file is empty or missing"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/companion/upload"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("tool_call_id", toolCallId)
            .also { if (!deviceId.isNullOrBlank()) it.addFormDataPart("device_id", deviceId) }
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody(mime.toMediaType()),
            )
            .build()
        val req = Request.Builder().url(url).post(body).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val detail = resp.body?.string()?.take(200).orEmpty()
                    error("HTTP ${resp.code}${if (detail.isNotBlank()) ": $detail" else ""}")
                }
                val payload = resp.body?.string().orEmpty()
                val decoded = HavenJson.decodeFromString(UploadResponse.serializer(), payload)
                CompanionUploadAck(imageUrl = decoded.image_url, expiresAt = decoded.expires_at)
            }
        }
    }
}

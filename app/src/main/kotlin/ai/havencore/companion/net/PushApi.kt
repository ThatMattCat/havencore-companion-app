package ai.havencore.companion.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PushApi(private val client: OkHttpClient) {

    @Serializable
    private data class RegisterReq(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_label") val deviceLabel: String,
        val endpoint: String,
        val platform: String = "android",
    )

    suspend fun register(
        baseUrl: String,
        deviceId: String,
        deviceLabel: String,
        endpoint: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        runCatching {
            val body = HavenJson.encodeToString(
                RegisterReq.serializer(),
                RegisterReq(deviceId, deviceLabel, endpoint),
            ).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/push/register")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string().orEmpty()
                    error("HTTP ${resp.code}${if (msg.isBlank()) "" else ": $msg"}")
                }
            }
        }
    }

    suspend fun deregister(baseUrl: String, deviceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
            }
            runCatching {
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/push/register/$deviceId")
                    .delete()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@runCatching
                    if (!resp.isSuccessful) {
                        val msg = resp.body?.string().orEmpty()
                        error("HTTP ${resp.code}${if (msg.isBlank()) "" else ": $msg"}")
                    }
                }
            }
        }
}

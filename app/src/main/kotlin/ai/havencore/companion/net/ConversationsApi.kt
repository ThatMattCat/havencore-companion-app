package ai.havencore.companion.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class PingEnvelope(
    val conversations: List<JsonElement> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

class ConversationsApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ping(baseUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/conversations?limit=1&offset=0"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val env = json.decodeFromString(PingEnvelope.serializer(), body)
                env.conversations.size
            }
        }
    }
}

package ai.havencore.companion.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatApi(private val client: OkHttpClient) {

    suspend fun listConversations(
        baseUrl: String,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<ListConversationsResponse> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/conversations?limit=$limit&offset=$offset"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                HavenJson.decodeFromString(ListConversationsResponse.serializer(), body)
            }
        }
    }

    suspend fun resumeConversation(
        baseUrl: String,
        sessionId: String,
    ): Result<ResumeResponse> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }
        if (sessionId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("session id is empty"))
        }
        val url = "${baseUrl.trimEnd('/')}/api/conversations/$sessionId/resume"
        // OkHttp rejects null bodies on POST; the agent ignores the body.
        val body = "".toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code}")
                }
                val responseBody = resp.body?.string().orEmpty()
                HavenJson.decodeFromString(ResumeResponse.serializer(), responseBody)
            }
        }
    }
}

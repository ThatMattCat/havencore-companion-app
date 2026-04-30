package ai.havencore.companion.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PushPayload(
    val v: Int = 1,
    val type: String = "ad_hoc",
    val title: String = "",
    val body: String = "",
    @SerialName("session_id") val sessionId: String? = null,
    val severity: String = "none",
)

private val pushJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun parsePushPayload(bytes: ByteArray): Result<PushPayload> = runCatching {
    val text = bytes.toString(Charsets.UTF_8)
    pushJson.decodeFromString(PushPayload.serializer(), text)
}

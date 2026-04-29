package ai.havencore.companion.voice

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Bound by `system_server` once the user grants HavenCore the
 * `RoleManager.ROLE_ASSISTANT` role. Lifecycle is "while-the-role-is-held"
 * — the per-invocation work happens in [HavenAssistSessionService] /
 * [HavenAssistSession]. Nothing else lives here.
 */
class HavenAssistService : VoiceInteractionService() {

    override fun onReady() {
        Log.i(TAG, "bound — default assistant role active")
    }

    override fun onShutdown() {
        Log.i(TAG, "shutting down")
    }

    private companion object {
        const val TAG = "Voice:VIS"
    }
}

package ai.havencore.companion.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Per-invocation factory. The system binds this when assist is invoked
 * (long-press home / power, lockscreen, `Intent.ACTION_ASSIST`) and asks
 * for a session; we hand back a fresh [HavenAssistSession] each time.
 */
class HavenAssistSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        HavenAssistSession(this)
}

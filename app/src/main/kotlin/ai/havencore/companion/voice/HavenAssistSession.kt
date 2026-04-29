package ai.havencore.companion.voice

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.widget.TextView

/**
 * Placeholder for commit #2 of Phase 3. Surfaces a plain TextView so the
 * end-to-end binding (system → [HavenAssistService] →
 * [HavenAssistSessionService] → this session → on-screen view) can be
 * verified before any real orchestration lands. Subsequent commits replace
 * the body with a Compose overlay backed by the chat substrate.
 */
class HavenAssistSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "session onCreate")
    }

    override fun onCreateContentView(): View {
        Log.i(TAG, "onCreateContentView (placeholder)")
        return TextView(context).apply {
            text = "HavenCore assist (placeholder)"
            textSize = 18f
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setPadding(64, 96, 64, 96)
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "onShow flags=$showFlags")
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "onHide")
    }

    override fun onDestroy() {
        Log.i(TAG, "session onDestroy")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Voice:Sess"
    }
}

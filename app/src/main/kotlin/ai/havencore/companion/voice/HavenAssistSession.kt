package ai.havencore.companion.voice

import ai.havencore.companion.MainActivity
import ai.havencore.companion.ui.theme.HavenCoreTheme
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Per-invocation overlay session. For commit #5 the state is held in a
 * static `MutableStateFlow<AssistUiState>` so the layout can be
 * exercised on the real phone before the orchestration in #6 / #7
 * mutates it. [onCreateContentView] returns a [ComposeView] hosting
 * [AssistOverlay]; the view tree owners come from
 * [AssistLifecycleOwner], pumped manually through the session's own
 * lifecycle callbacks.
 */
class HavenAssistSession(context: Context) : VoiceInteractionSession(context) {

    private val state = MutableStateFlow(AssistUiState())
    private lateinit var lifecycleOwner: AssistLifecycleOwner

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "session onCreate")
        lifecycleOwner = AssistLifecycleOwner().also { it.onCreate() }
        // The session Dialog's Window defaults to WRAP_CONTENT, which would
        // shrink the overlay to just the sheet's measured size. Force the
        // Window to fill the screen so the scrim region above the sheet is
        // hit-testable and tap-to-dismiss works. The scrim itself is drawn
        // by Compose — FLAG_DIM_BEHIND only dims pixels outside the
        // window, which is no longer useful once the window covers
        // everything. Keep FLAG_SHOW_WHEN_LOCKED for lockscreen invocations.
        window.window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun onCreateContentView(): View {
        Log.i(TAG, "onCreateContentView")
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                HavenCoreTheme {
                    AssistOverlay(
                        stateFlow = state,
                        onDismiss = { finish() },
                        onOpenApp = { openHavenCoreAndFinish() },
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "onShow flags=$showFlags")
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "onHide")
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
    }

    override fun onDestroy() {
        Log.i(TAG, "session onDestroy")
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun openHavenCoreAndFinish() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        finish()
    }

    private companion object {
        const val TAG = "Voice:Sess"
    }
}

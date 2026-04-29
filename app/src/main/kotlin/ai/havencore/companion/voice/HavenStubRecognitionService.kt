package ai.havencore.companion.voice

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * No-op [RecognitionService]. We proxy STT to the HavenCore agent via
 * `SttApi`, not via the on-device `SpeechRecognizer` framework, so this
 * service intentionally does nothing useful.
 *
 * It exists because Samsung's Digital Assistant picker silently filters
 * out a `VoiceInteractionService` whose package does not also expose a
 * `RecognitionService` (the role-picker filter looks at manifest shape,
 * not whether the recognizer ever runs). Declaring a stub satisfies the
 * filter; every request fails fast with `ERROR_RECOGNIZER_BUSY` so any
 * caller that does end up here falls back gracefully.
 */
class HavenStubRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.i(TAG, "onStartListening — declining (stub)")
        runCatching { listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
    }

    override fun onStopListening(listener: Callback?) {
        // No-op; we never started.
    }

    override fun onCancel(listener: Callback?) {
        // No-op; we never started.
    }

    private companion object {
        const val TAG = "Voice:Stub"
    }
}

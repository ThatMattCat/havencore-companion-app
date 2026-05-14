package ai.havencore.companion.voice.avatar

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JS → Kotlin bridge exposed to the WebView as `window.AndroidAvatar`. The
 * counterpart `window.AvatarApi` (set in index.html) is the Kotlin → JS
 * surface that the service drives via [android.webkit.WebView.evaluateJavascript].
 *
 * Methods declared here run on the WebView's worker thread, NOT the main
 * thread — push any UI / service-state work through a callback hop.
 */
class AvatarBridge(
    private val onReady: () -> Unit = {},
    private val onError: (String) -> Unit = { /* default: just log */ },
    private val onTap: () -> Unit = {},
) {

    @JavascriptInterface
    fun onReady() {
        Log.i(TAG, "JS: model ready")
        onReady.invoke()
    }

    @JavascriptInterface
    fun onError(msg: String) {
        Log.w(TAG, "JS error: $msg")
        onError.invoke(msg)
    }

    @JavascriptInterface
    fun onTap() {
        Log.i(TAG, "JS: avatar tapped")
        onTap.invoke()
    }

    private companion object {
        const val TAG = "Avatar:Bridge"
    }
}

package ai.havencore.companion.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Transparent, no-history Activity that fires the device's camera intent
 * and reports the resulting JPEG path back to a registered callback. The
 * dispatcher launches this from a non-Activity context, then awaits the
 * callback on a coroutine so the upload can run without holding an
 * Activity instance.
 *
 * The callback registry is keyed by `tool_call_id`. The dispatcher
 * registers BEFORE starting the activity and unregisters once it has
 * consumed the result.
 */
class CaptureActivity : ComponentActivity() {

    private var toolCallId: String? = null
    private var photoFile: File? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        val tcid = toolCallId
        val file = photoFile
        Log.i(
            TAG,
            "TakePicture returned success=$success tool_call_id=$tcid " +
                "file_exists=${file?.exists()} file_bytes=${file?.length() ?: -1}",
        )
        if (tcid != null) {
            val result = if (success && file != null && file.length() > 0) {
                CaptureResult.Captured(file)
            } else {
                CaptureResult.Cancelled
            }
            deliver(tcid, result)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tcid = intent.getStringExtra(EXTRA_TOOL_CALL_ID)
        if (tcid.isNullOrBlank()) {
            Log.w(TAG, "CaptureActivity launched without $EXTRA_TOOL_CALL_ID")
            finish()
            return
        }
        toolCallId = tcid
        Log.i(TAG, "onCreate tool_call_id=$tcid registered_callbacks=${callbacks.keys}")

        val photosDir = File(cacheDir, "photos").apply { mkdirs() }
        val file = File(photosDir, "$tcid.jpg")
        if (file.exists()) file.delete()
        photoFile = file

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        Log.i(TAG, "launching TakePicture uri=$uri target=${file.absolutePath}")
        try {
            takePictureLauncher.launch(uri)
        } catch (t: Throwable) {
            Log.w(TAG, "TakePicture launch failed", t)
            deliver(tcid, CaptureResult.Failed(t.message ?: "no camera app"))
            finish()
        }
    }

    companion object {
        private const val TAG = "CaptureActivity"
        private const val EXTRA_TOOL_CALL_ID = "tool_call_id"

        private val callbacks = ConcurrentHashMap<String, (CaptureResult) -> Unit>()

        fun register(toolCallId: String, cb: (CaptureResult) -> Unit) {
            callbacks[toolCallId] = cb
        }

        fun unregister(toolCallId: String) {
            callbacks.remove(toolCallId)
        }

        private fun deliver(toolCallId: String, result: CaptureResult) {
            val cb = callbacks.remove(toolCallId)
            if (cb == null) {
                Log.w(
                    TAG,
                    "no callback registered for tool_call_id=$toolCallId " +
                        "(known=${callbacks.keys})",
                )
                return
            }
            Log.i(TAG, "delivering capture result tool_call_id=$toolCallId result=$result")
            cb(result)
        }

        fun newIntent(context: Context, toolCallId: String): Intent =
            Intent(context, CaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TOOL_CALL_ID, toolCallId)
    }
}

sealed interface CaptureResult {
    data class Captured(val file: File) : CaptureResult
    data object Cancelled : CaptureResult
    data class Failed(val reason: String) : CaptureResult
}

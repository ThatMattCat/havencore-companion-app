package ai.havencore.companion.device

import ai.havencore.companion.data.DeviceAction
import ai.havencore.companion.data.DeviceActionResult
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.CompanionUploadApi
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

/**
 * Fires Android Intents (and now camera-tool round-trips) on behalf of the
 * agent. Lives in the AppContainer; both ChatViewModel and
 * HavenAssistSession dispatch through the same instance so chat-screen and
 * assist-overlay flows share the same path.
 *
 * `dispatch` is a `suspend fun` because camera tools (`take_photo`) need to
 * wait for the user to capture a photo and for the upload to land at the
 * agent. Fire-and-forget intents like `set_alarm` still return immediately.
 *
 * Intents originate from a non-Activity Context, so every Intent must
 * carry FLAG_ACTIVITY_NEW_TASK.
 */
class DeviceActionDispatcher(
    private val context: Context,
    private val settings: SettingsRepository,
    private val uploadApi: CompanionUploadApi,
) {

    suspend fun dispatch(action: DeviceAction): DeviceActionResult = try {
        when (action) {
            is DeviceAction.SetAlarm -> setAlarm(action)
            is DeviceAction.CameraCapture -> captureAndUpload(action)
        }
    } catch (t: ActivityNotFoundException) {
        Log.w(TAG, "no activity for action=$action", t)
        DeviceActionResult.NoHandler
    } catch (t: Throwable) {
        Log.w(TAG, "device action failed: $action", t)
        DeviceActionResult.Failed(t.message ?: t.javaClass.simpleName)
    }

    private fun setAlarm(action: DeviceAction.SetAlarm): DeviceActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, action.skipUi)
            putExtra(AlarmClock.EXTRA_VIBRATE, action.vibrate)
            action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (action.daysOfWeek.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(action.daysOfWeek))
            }
        }
        context.startActivity(intent)
        Log.i(TAG, "set_alarm fired hour=${action.hour} minute=${action.minute}")
        return DeviceActionResult.Fired
    }

    private suspend fun captureAndUpload(
        action: DeviceAction.CameraCapture,
    ): DeviceActionResult {
        val label = action::class.simpleName ?: "camera"
        Log.i(TAG, "$label dispatched tool_call_id=${action.toolCallId}")

        // Master toggle gates all camera tools; per-tool toggles further
        // narrow which actions are allowed once master is on.
        if (!settings.companionCameraTakePhotoEnabledFlow.first()) {
            Log.i(TAG, "$label skipped: master camera toggle disabled")
            return DeviceActionResult.Disabled
        }
        val perToolEnabled = when (action) {
            is DeviceAction.TakePhoto -> true  // master toggle == take_photo gate
            is DeviceAction.IdentifyObjectInPhoto ->
                settings.companionCameraIdentifyEnabledFlow.first()
            is DeviceAction.ReadTextFromImage ->
                settings.companionCameraReadTextEnabledFlow.first()
        }
        if (!perToolEnabled) {
            Log.i(TAG, "$label skipped: per-tool toggle disabled")
            return DeviceActionResult.Disabled
        }

        val captureResult = launchCapture(action.toolCallId)
        Log.i(TAG, "$label capture result=$captureResult tool_call_id=${action.toolCallId}")
        when (captureResult) {
            is CaptureResult.Cancelled -> return DeviceActionResult.Cancelled
            is CaptureResult.Failed -> return DeviceActionResult.Failed(captureResult.reason)
            is CaptureResult.Captured -> Unit  // continue
        }

        val cfg = settings.configFlow.first()
        val file = (captureResult as CaptureResult.Captured).file
        Log.i(
            TAG,
            "$label uploading file=${file.absolutePath} bytes=${file.length()} " +
                "baseUrl=${cfg.baseUrl} device=${cfg.deviceName}",
        )
        val ack = uploadApi.upload(
            baseUrl = cfg.baseUrl,
            toolCallId = action.toolCallId,
            deviceId = cfg.deviceName.takeIf { it.isNotBlank() },
            file = file,
        )
        // Best-effort cleanup of the cache file regardless of upload outcome.
        runCatching { file.delete() }

        return ack.fold(
            onSuccess = {
                Log.i(
                    TAG,
                    "$label uploaded tool_call_id=${action.toolCallId} image_url=${it.imageUrl}",
                )
                DeviceActionResult.Uploaded
            },
            onFailure = { t ->
                Log.w(TAG, "$label upload failed tool_call_id=${action.toolCallId}", t)
                DeviceActionResult.Failed(t.message ?: "upload failed")
            },
        )
    }

    private suspend fun launchCapture(toolCallId: String): CaptureResult =
        suspendCancellableCoroutine { cont ->
            CaptureActivity.register(toolCallId) { result ->
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { CaptureActivity.unregister(toolCallId) }
            try {
                context.startActivity(CaptureActivity.newIntent(context, toolCallId))
            } catch (t: Throwable) {
                CaptureActivity.unregister(toolCallId)
                if (cont.isActive) {
                    cont.resume(CaptureResult.Failed(t.message ?: "failed to launch camera"))
                }
            }
        }

    private companion object {
        const val TAG = "DeviceAction"
    }
}

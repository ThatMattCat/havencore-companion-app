package ai.havencore.companion.voice

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Thin wrapper around [RoleManager.ROLE_ASSISTANT].
 *
 * The picker intent is unified on [Settings.ACTION_VOICE_INPUT_SETTINGS]
 * for both "set up" and "manage". The `RoleManager.createRequestRoleIntent`
 * one-tap-grant flow looks nicer in the abstract, but Samsung's
 * `PermissionController` fork resolves the intent without actually
 * surfacing a role-grant UI, so the tap is a silent no-op. The Voice
 * Input settings page works universally and is one tap further than the
 * role dialog — acceptable.
 */
object DefaultAssistantHelper {

    fun isHeld(ctx: Context): Boolean {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    fun pickerIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
}

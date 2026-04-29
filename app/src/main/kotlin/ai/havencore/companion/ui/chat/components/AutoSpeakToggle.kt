package ai.havencore.companion.ui.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun AutoSpeakToggle(
    autoSpeak: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        if (autoSpeak) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Auto-speak on; tap to disable",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = "Auto-speak off; tap to enable",
            )
        }
    }
}

package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.VoiceUi
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MicButton(
    voice: VoiceUi,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    when (voice) {
        VoiceUi.Idle -> IconButton(onClick = onTap, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Start voice input",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VoiceUi.Recording -> IconButton(onClick = onTap, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop recording",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        VoiceUi.Transcribing -> IconButton(onClick = {}, enabled = false) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        VoiceUi.Speaking -> IconButton(onClick = onTap, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Stop playback and start voice input",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

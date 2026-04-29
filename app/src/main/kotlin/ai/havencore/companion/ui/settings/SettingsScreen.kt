package ai.havencore.companion.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val config by vm.config.collectAsState()
    val ping by vm.ping.collectAsState()
    val ttsTest by vm.ttsTest.collectAsState()
    val micTest by vm.micTest.collectAsState()
    val playTest by vm.playTest.collectAsState()
    val ctx = LocalContext.current

    val voicePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            vm.testMic()
        }
    }

    var baseUrl by rememberSaveable(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var deviceName by rememberSaveable(config.deviceName) { mutableStateOf(config.deviceName) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://havencore.local") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { vm.save(baseUrl, deviceName) }) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = vm::testConnection,
                    enabled = ping !is PingState.InFlight,
                ) {
                    Text("Test connection")
                }
            }

            HorizontalDivider()

            when (val s = ping) {
                PingState.Untested -> Text(
                    "Untested. Save your server URL, then tap Test connection.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PingState.InFlight -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Pinging …")
                }
                is PingState.Ok -> Text(
                    "Connected. /api/conversations returned ${s.count} conversation(s).",
                    color = MaterialTheme.colorScheme.primary,
                )
                is PingState.Err -> Text(
                    "Failed: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            Text(
                "Phase 2 debug",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = vm::testTts,
                enabled = ttsTest !is TtsTestState.InFlight,
            ) {
                Text("Test TTS")
            }
            OutlinedButton(
                onClick = {
                    val recordOk = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    val btOk = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (recordOk && btOk) {
                        vm.testMic()
                    } else {
                        voicePermLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ),
                        )
                    }
                },
                enabled = micTest !is MicTestState.Recording,
            ) {
                Text("Test mic 3 s")
            }
            OutlinedButton(
                onClick = vm::testPlayTts,
                enabled = playTest !is PlayTestState.Synthesizing &&
                    playTest !is PlayTestState.Playing,
            ) {
                Text("Test play TTS")
            }
            when (val s = ttsTest) {
                TtsTestState.Untested -> {} // no row until first tap
                TtsTestState.InFlight -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Synthesizing …")
                }
                is TtsTestState.Ok -> Text(
                    "received ${s.bytes} bytes (${s.contentType})",
                    color = MaterialTheme.colorScheme.primary,
                )
                is TtsTestState.Err -> Text(
                    "TTS failed: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (val s = micTest) {
                MicTestState.Untested -> {} // no row until first tap
                MicTestState.Recording -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Recording 3 s …")
                }
                is MicTestState.Ok -> Text(
                    "recorded ${s.bytes / 1024} KB at ${s.path.substringAfterLast('/')}",
                    color = MaterialTheme.colorScheme.primary,
                )
                is MicTestState.Err -> Text(
                    "Mic failed: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (val s = playTest) {
                PlayTestState.Untested -> {} // no row until first tap
                PlayTestState.Synthesizing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Synthesizing …")
                }
                PlayTestState.Playing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Playing …")
                }
                PlayTestState.Done -> Text(
                    "Playback finished.",
                    color = MaterialTheme.colorScheme.primary,
                )
                is PlayTestState.Err -> Text(
                    "Play failed: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

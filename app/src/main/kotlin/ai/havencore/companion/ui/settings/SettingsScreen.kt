package ai.havencore.companion.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val config by vm.config.collectAsState()
    val ping by vm.ping.collectAsState()

    var baseUrl by rememberSaveable(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var deviceName by rememberSaveable(config.deviceName) { mutableStateOf(config.deviceName) }

    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.toasts.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HavenCore Companion") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Phase 0 – settings + connectivity probe",
                style = MaterialTheme.typography.titleMedium,
            )

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

            // Phase 1 debug — replaced by the History screen in commit 5.
            OutlinedButton(
                onClick = vm::debugListConversations,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("List conversations (debug)")
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
        }
    }
}

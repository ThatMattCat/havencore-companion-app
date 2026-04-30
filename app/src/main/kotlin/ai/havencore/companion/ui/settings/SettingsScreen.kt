package ai.havencore.companion.ui.settings

import ai.havencore.companion.R
import ai.havencore.companion.push.PushUi
import ai.havencore.companion.voice.DefaultAssistantHelper
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val config by vm.config.collectAsState()
    val ping by vm.ping.collectAsState()
    val isAssistantHeld by vm.isAssistantHeld.collectAsState()
    val pushUi by vm.pushUi.collectAsState()

    val ctx = LocalContext.current

    // Re-check the assistant role + distributor list on every resume so
    // returning from Settings (role picker, ntfy install) flips state
    // without an app restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onResume()
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
            DefaultAssistantBanner(
                held = isAssistantHeld,
                onLaunchPicker = {
                    ctx.startActivity(DefaultAssistantHelper.pickerIntent())
                },
            )

            NotificationsCard(
                state = pushUi,
                onToggle = vm::togglePush,
                onRetry = vm::retryRegistration,
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

@Composable
private fun NotificationsCard(
    state: PushUi,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val ctx = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onToggle(true) }

    var helpExpanded by rememberSaveable { mutableStateOf(false) }

    val toggleEnabled = state !is PushUi.NoDistributor
    val checked = state is PushUi.Ready ||
        state is PushUi.AwaitingEndpoint ||
        state is PushUi.Failed

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.push_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.push_card_body),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = checked,
                    enabled = toggleEnabled,
                    onCheckedChange = { wantOn ->
                        when {
                            !wantOn -> onToggle(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED ->
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggle(true)
                        }
                    },
                )
                Text(
                    text = "Enable notifications",
                    modifier = Modifier.weight(1f),
                )
            }

            when (state) {
                PushUi.Disabled -> Text(
                    text = "Status: off",
                    style = MaterialTheme.typography.bodySmall,
                )
                PushUi.NoDistributor -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Status: no distributor app installed",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://f-droid.org/en/packages/io.heckel.ntfy/"),
                            ),
                        )
                    }) {
                        Text("Install ntfy")
                    }
                }
                is PushUi.AwaitingEndpoint -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Status: contacting distributor…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is PushUi.Ready -> Text(
                    text = "Status: ready · ${distributorLabel(state.distributorPkg)} / ${maskEndpoint(state.endpoint)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is PushUi.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Status: registration failed — ${state.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            TextButton(
                onClick = { helpExpanded = !helpExpanded },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.push_setup_help_title) +
                        if (helpExpanded) "  ▴" else "  ▾",
                )
            }
            if (helpExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.push_setup_help_step_1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.push_setup_help_step_2),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.push_setup_help_step_3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.push_setup_help_step_4),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.push_setup_help_step_5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun distributorLabel(pkg: String): String =
    pkg.substringAfterLast('.').ifBlank { pkg }

private fun maskEndpoint(url: String): String {
    val tail = url.substringAfterLast('/').ifBlank { url }
    return if (tail.length <= 6) "…$tail" else "…${tail.takeLast(6)}"
}

@Composable
private fun DefaultAssistantBanner(
    held: Boolean,
    onLaunchPicker: () -> Unit,
) {
    val colors = if (held) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
    Card(
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (held) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (held) {
                        "HavenCore is your default assistant"
                    } else {
                        "Set HavenCore as your default assistant"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!held) {
                    Text(
                        text = "Long-press home or power to talk hands-free.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onLaunchPicker) {
                Text(if (held) "Manage" else "Set up")
            }
        }
    }
}

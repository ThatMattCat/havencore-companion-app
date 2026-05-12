package ai.havencore.companion.ui.settings

import ai.havencore.companion.R
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.data.ThemeMode
import ai.havencore.companion.push.PushUi
import ai.havencore.companion.ui.components.AccentDisc
import ai.havencore.companion.ui.components.AnimatedSwap
import ai.havencore.companion.ui.components.Banner
import ai.havencore.companion.ui.components.BannerSeverity
import ai.havencore.companion.ui.theme.HavenTokens
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
    val silenceTimeoutMs by vm.silenceTimeoutMs.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val cameraTakePhotoEnabled by vm.companionCameraTakePhotoEnabled.collectAsState()
    val cameraIdentifyEnabled by vm.companionCameraIdentifyEnabled.collectAsState()
    val cameraReadTextEnabled by vm.companionCameraReadTextEnabled.collectAsState()
    val cameraWhoIsInViewEnabled by vm.companionCameraWhoIsInViewEnabled.collectAsState()
    val wallDisplayEnabled by vm.wallDisplayEnabled.collectAsState()

    val ctx = LocalContext.current

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
                .padding(
                    horizontal = HavenTokens.Spacing.lg,
                    vertical = HavenTokens.Spacing.md,
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
        ) {
            DefaultAssistantCard(
                held = isAssistantHeld,
                onLaunchPicker = {
                    ctx.startActivity(DefaultAssistantHelper.pickerIntent())
                },
            )

            ConnectionCard(
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it },
                deviceName = deviceName,
                onDeviceNameChange = { deviceName = it },
                ping = ping,
                onSave = { vm.save(baseUrl, deviceName) },
                onTest = vm::testConnection,
            )

            AppearanceCard(
                themeMode = themeMode,
                onThemeModeChange = vm::setThemeMode,
                dynamicColor = dynamicColor,
                onDynamicColorChange = vm::setDynamicColor,
            )

            VoiceCard(
                silenceTimeoutMs = silenceTimeoutMs,
                onChange = vm::setSilenceTimeoutMs,
            )

            CompanionCameraCard(
                takePhotoEnabled = cameraTakePhotoEnabled,
                onTakePhotoEnabledChange = vm::setCompanionCameraTakePhotoEnabled,
                identifyEnabled = cameraIdentifyEnabled,
                onIdentifyEnabledChange = vm::setCompanionCameraIdentifyEnabled,
                readTextEnabled = cameraReadTextEnabled,
                onReadTextEnabledChange = vm::setCompanionCameraReadTextEnabled,
                whoIsInViewEnabled = cameraWhoIsInViewEnabled,
                onWhoIsInViewEnabledChange = vm::setCompanionCameraWhoIsInViewEnabled,
            )

            WallDisplayCard(
                enabled = wallDisplayEnabled,
                onEnabledChange = vm::setWallDisplayEnabled,
            )

            NotificationsCard(
                state = pushUi,
                onToggle = vm::togglePush,
                onRetry = vm::retryRegistration,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(HavenTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
            ) {
                AccentDisc(
                    icon = icon,
                    discColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ConnectionCard(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    ping: PingState,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    SettingsCard(
        icon = Icons.Default.Cloud,
        title = "Connection",
        description = "Where the HavenCore agent is reachable on your network.",
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("http://havencore.local") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = deviceName,
            onValueChange = onDeviceNameChange,
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSave) { Text("Save") }
            OutlinedButton(
                onClick = onTest,
                enabled = ping !is PingState.InFlight,
            ) {
                Text("Test connection")
            }
        }
        AnimatedSwap(
            targetState = ping,
            contentKey = { it::class },
            label = "ping",
        ) { state ->
            when (state) {
                PingState.Untested -> Text(
                    text = "Save your URL, then tap Test connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PingState.InFlight -> Banner(
                    severity = BannerSeverity.Progress,
                    text = "Pinging…",
                )
                is PingState.Ok -> Banner(
                    severity = BannerSeverity.Info,
                    text = "Connected · ${state.count} conversation(s) on the server.",
                )
                is PingState.Err -> Banner(
                    severity = BannerSeverity.Error,
                    text = "Failed: ${state.message}",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    SettingsCard(
        icon = Icons.Default.Palette,
        title = "Appearance",
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val options = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == themeMode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(mode.label())
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HavenTokens.Spacing.xs),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Match my wallpaper",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Use Android's dynamic color instead of the HavenCore palette.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "System"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

@Composable
private fun VoiceCard(
    silenceTimeoutMs: Long,
    onChange: (Long) -> Unit,
) {
    val minMs = SettingsRepository.MIN_SILENCE_TIMEOUT_MS
    val maxMs = SettingsRepository.MAX_SILENCE_TIMEOUT_MS
    val stepMs = SettingsRepository.SILENCE_TIMEOUT_STEP_MS
    val steps = ((maxMs - minMs) / stepMs).toInt() - 1
    val seconds = silenceTimeoutMs / 1000f

    SettingsCard(
        icon = Icons.Default.Mic,
        title = "Voice",
        description = "How long the assistant overlay waits in silence before it stops listening and processes what you said.",
    ) {
        Text(
            text = "Auto-stop after %.1f s".format(seconds),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = seconds,
            onValueChange = { v ->
                val ms = (Math.round(v * 1000f / stepMs) * stepMs).toLong()
                    .coerceIn(minMs, maxMs)
                onChange(ms)
            },
            valueRange = (minMs / 1000f)..(maxMs / 1000f),
            steps = steps,
        )
    }
}

@Composable
private fun CompanionCameraCard(
    takePhotoEnabled: Boolean,
    onTakePhotoEnabledChange: (Boolean) -> Unit,
    identifyEnabled: Boolean,
    onIdentifyEnabledChange: (Boolean) -> Unit,
    readTextEnabled: Boolean,
    onReadTextEnabledChange: (Boolean) -> Unit,
    whoIsInViewEnabled: Boolean,
    onWhoIsInViewEnabledChange: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onTakePhotoEnabledChange(true) }

    SettingsCard(
        icon = Icons.Default.CameraAlt,
        title = "Camera tools",
        description = "Let the assistant ask your phone to take a photo and " +
            "send it back. Camera launches only when the agent calls a " +
            "matching tool — never on its own.",
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
        ) {
            Switch(
                checked = takePhotoEnabled,
                onCheckedChange = { wantOn ->
                    when {
                        !wantOn -> onTakePhotoEnabledChange(false)
                        ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.CAMERA,
                        ) != PackageManager.PERMISSION_GRANTED ->
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        else -> onTakePhotoEnabledChange(true)
                    }
                },
            )
            Text(
                text = "Camera tools (master)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        CameraToolToggleRow(
            label = "Identify what's in the photo",
            description = "Lets the assistant call identify_object_in_photo.",
            checked = identifyEnabled,
            enabled = takePhotoEnabled,
            onCheckedChange = onIdentifyEnabledChange,
        )
        CameraToolToggleRow(
            label = "Read text from the photo",
            description = "Lets the assistant call read_text_from_image (OCR).",
            checked = readTextEnabled,
            enabled = takePhotoEnabled,
            onCheckedChange = onReadTextEnabledChange,
        )
        CameraToolToggleRow(
            label = "Recognize who's in the photo",
            description = "Lets the assistant call who_is_in_view against " +
                "your enrolled face gallery.",
            checked = whoIsInViewEnabled,
            enabled = takePhotoEnabled,
            onCheckedChange = onWhoIsInViewEnabledChange,
        )
    }
}

@Composable
private fun CameraToolToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
    ) {
        Switch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WallDisplayCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingsCard(
        icon = Icons.Default.Tv,
        title = stringResource(R.string.wall_display_card_title),
        description = stringResource(R.string.wall_display_card_body),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wall_display_toggle),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text(
            text = stringResource(R.string.wall_display_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    SettingsCard(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.push_card_title),
        description = stringResource(R.string.push_card_body),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedSwap(
            targetState = state,
            contentKey = { it::class },
            label = "push-status",
        ) { s ->
            when (s) {
                PushUi.Disabled -> Text(
                    text = "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PushUi.NoDistributor -> Banner(
                    severity = BannerSeverity.Warning,
                    text = "No distributor app installed.",
                    actionLabel = "Install ntfy",
                    onAction = {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://f-droid.org/en/packages/io.heckel.ntfy/"),
                            ),
                        )
                    },
                )
                is PushUi.AwaitingEndpoint -> Banner(
                    severity = BannerSeverity.Progress,
                    text = "Contacting distributor…",
                )
                is PushUi.Ready -> Banner(
                    severity = BannerSeverity.Info,
                    text = "Ready · ${distributorLabel(s.distributorPkg)} / ${maskEndpoint(s.endpoint)}",
                )
                is PushUi.Failed -> Banner(
                    severity = BannerSeverity.Error,
                    text = "Registration failed — ${s.reason}",
                    actionLabel = "Retry",
                    onAction = onRetry,
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.xs)) {
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

private fun distributorLabel(pkg: String): String =
    pkg.substringAfterLast('.').ifBlank { pkg }

private fun maskEndpoint(url: String): String {
    val tail = url.substringAfterLast('/').ifBlank { url }
    return if (tail.length <= 6) "…$tail" else "…${tail.takeLast(6)}"
}

@Composable
private fun DefaultAssistantCard(
    held: Boolean,
    onLaunchPicker: () -> Unit,
) {
    val (container, content) = if (held) {
        MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HavenTokens.Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
        ) {
            AccentDisc(
                icon = if (held) Icons.Default.RecordVoiceOver else Icons.Default.GraphicEq,
                discColor = content.copy(alpha = 0.16f),
                iconColor = content,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.xs),
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

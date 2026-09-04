package com.glyphix.app.ui.PrimaryScreens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Android
import compose.icons.fontawesomeicons.brands.Bluetooth
import compose.icons.fontawesomeicons.solid.*
import compose.icons.fontawesomeicons.solid.NetworkWired
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.logic.LatencyWizard
import com.glyphix.app.service.AudioCaptureService
import com.glyphix.app.spotify.SpotifyPlaybackState
import com.glyphix.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AudioScreen(
    isRunning: Boolean,
    sessionDuration: Long = 0L,
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String? = null,
    fftData: () -> FloatArray = { floatArrayOf() },
    captureSource: AudioCaptureService.CaptureSource = AudioCaptureService.CaptureSource.INTERNAL,
    onCaptureSourceChanged: (AudioCaptureService.CaptureSource) -> Unit = {},
    networkPacketsReceived: Int = 0,
    bluetoothDeviceName: String = "",
    bluetoothDeviceAddress: String = "",
    pcPacketsSent: Int = 0,
    desktopSyncDirection: String = "PHONE_TO_PC",
    pcCompanionIp: String = "",
    isPcStreamingActive: Boolean = false,
    onTogglePcStream: (Boolean) -> Unit = {},
    onPcIpChanged: (String) -> Unit = {},
    onDiscoverPc: () -> Unit = {},
    onSyncDirectionChanged: (String) -> Unit = {},
    latencyWizardState: LatencyWizard.State = LatencyWizard.State.Idle,
    onRunLatencyWizard: () -> Unit = {},
    onResetLatencyWizard: () -> Unit = {},
    bananaMode: Boolean = false,
    penisMode: Boolean = false,
    spotifyPlaybackState: SpotifyPlaybackState? = null,
    onSpotifyTogglePlay: () -> Unit = {},
    onSpotifyNext: () -> Unit = {},
    onSpotifyPrevious: () -> Unit = {},
    onSpotifySeek: (Long) -> Unit = {},
    onSpotifyToggleShuffle: () -> Unit = {},
    onSpotifyToggleRepeat: () -> Unit = {},
    onOpenSpotifyTab: () -> Unit = {},
    onToggleVisualizer: () -> Unit = {},
    viewModel: MainViewModel? = null,
    selectedDevice: Int = 0,
    vizStateProvider: () -> FloatArray = { floatArrayOf() },
    presets: List<AudioCaptureService.PresetInfo> = emptyList(),
    selectedPreset: String = "",
    onPresetSelected: (String) -> Unit = {},
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAutoDeviceToggle(true)
        }
    }

    var pendingCaptureSource by remember { mutableStateOf<AudioCaptureService.CaptureSource?>(null) }
    
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            pendingCaptureSource?.let { onCaptureSourceChanged(it) }
        }
        pendingCaptureSource = null
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingCaptureSource?.let {
            if (isGranted) {
                onCaptureSourceChanged(it)
            }
            pendingCaptureSource = null
        }
    }

    val wizardPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onRunLatencyWizard()
        }
    }

    val handleAutoToggle: (Boolean) -> Unit = { setEnabled ->
        if (setEnabled) {
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                PackageManager.PERMISSION_GRANTED
            }
            if (status == PackageManager.PERMISSION_GRANTED) {
                onAutoDeviceToggle(true)
            } else {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            onAutoDeviceToggle(false)
        }
    }

    StaggeredEntranceColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = LocalAppSpacing.current.edge),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Inbuilt Spotify Control Panel (Displayed above everything when Spotify is active input)
        if (captureSource == AudioCaptureService.CaptureSource.SPOTIFY) {
            AnimatedItem {
                GlyphixSpotifyControlPanel(
                    playbackState = spotifyPlaybackState,
                    onTogglePlay = onSpotifyTogglePlay,
                    onNext = onSpotifyNext,
                    onPrevious = onSpotifyPrevious,
                    onSeek = onSpotifySeek,
                    onToggleShuffle = onSpotifyToggleShuffle,
                    onToggleRepeat = onSpotifyToggleRepeat,
                    onOpenSpotifyTab = onOpenSpotifyTab
                )
            }
        }

        // Desktop Companion Status Panel (Displayed when Desktop Companion is active or PC streaming is enabled)
        var companionCardDismissed by remember { mutableStateOf(false) }

        LaunchedEffect(captureSource) {
            if (captureSource == AudioCaptureService.CaptureSource.NETWORK || captureSource == AudioCaptureService.CaptureSource.BLUETOOTH) {
                companionCardDismissed = false
            }
        }

        val showCompanionCard = isRunning && !companionCardDismissed && (
            captureSource == AudioCaptureService.CaptureSource.NETWORK ||
            captureSource == AudioCaptureService.CaptureSource.BLUETOOTH ||
            isPcStreamingActive
        )

        if (showCompanionCard) {
            AnimatedItem {
                DesktopCompanionStatusCard(
                    captureSource = captureSource,
                    isRunning = isRunning,
                    networkPacketsReceived = networkPacketsReceived,
                    bluetoothDeviceName = bluetoothDeviceName,
                    bluetoothDeviceAddress = bluetoothDeviceAddress,
                    desktopSyncDirection = desktopSyncDirection,
                    pcPacketsSent = pcPacketsSent,
                    pcCompanionIp = pcCompanionIp,
                    isPcStreamingActive = isPcStreamingActive,
                    onTogglePcStream = onTogglePcStream,
                    onPcIpChanged = onPcIpChanged,
                    onDiscoverPc = onDiscoverPc,
                    onSyncDirectionChanged = onSyncDirectionChanged,
                    onDismiss = { companionCardDismissed = true }
                )
            }
        }

        if (!isRunning) {
            AnimatedItem {
                MockupCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(mockupAccentColor().copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = mockupAccentColor(),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Audio Visualizer Ready",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = mockupTextColor()
                            )
                            Text(
                                text = "Tap the Play button below to select capture source and start sync.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = mockupSubtextColor()
                            )
                        }
                    }
                }
            }
        }

        val compositionKey = remember(bananaMode, penisMode) { "$bananaMode-$penisMode" }
        key(compositionKey) {
            AnimatedVisibility(visible = isRunning) {
                AnimatedItem {
                    FFTSpectrumCard(fftData = fftData, bananaMode = bananaMode, penisMode = penisMode)
                }
            }
        }

        if (isRunning) {
            val previewHeight = when (selectedDevice) {
                com.glyphix.app.model.DeviceProfile.DEVICE_NP2 -> 530.dp
                com.glyphix.app.model.DeviceProfile.DEVICE_NP1,
                com.glyphix.app.model.DeviceProfile.DEVICE_NP3,
                com.glyphix.app.model.DeviceProfile.DEVICE_NP4A,
                com.glyphix.app.model.DeviceProfile.DEVICE_NP4B,
                com.glyphix.app.model.DeviceProfile.DEVICE_NP4APRO -> 560.dp
                else -> 400.dp
            }
            AnimatedItem {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphPreview(
                        vizStateProvider = vizStateProvider,
                        device = selectedDevice,
                        modifier = Modifier
                            .width(380.dp)
                            .height(previewHeight)
                    )
                }
            }

            val seconds = (sessionDuration / 1000) % 60
            val minutes = (sessionDuration / (1000 * 60)) % 60
            val hours = (sessionDuration / (1000 * 60 * 60))
            val timeStr = if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
            val descriptionText = stringResource(R.string.audio_description_running) + "\n\nActive Time: $timeStr"

            AnimatedItem {
                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.5f
                    )
                ) {
                    BodyText(
                        text = descriptionText,
                        size = 14.sp
                    )
                }
            }
        }

        val effectivePresets = if (presets.isNotEmpty()) presets else (viewModel?.presetInfos?.collectAsStateWithLifecycle()?.value ?: emptyList())
        val effectiveSelectedPreset = if (selectedPreset.isNotEmpty()) selectedPreset else (viewModel?.selectedPreset?.collectAsStateWithLifecycle()?.value ?: "")
        val configVersion = viewModel?.configVersion?.collectAsStateWithLifecycle()?.value ?: ""
        val favorites = viewModel?.favoritePresets?.collectAsStateWithLifecycle()?.value ?: emptySet()

        val selectedInfo = remember(effectiveSelectedPreset, effectivePresets) {
            effectivePresets.firstOrNull { it.key == effectiveSelectedPreset } ?: effectivePresets.firstOrNull()
        }

        var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

        if (showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Delete Preset?") },
                text = { Text("Are you sure you want to delete the local preset '${showDeleteConfirm}'?") },
                confirmButton = {
                    TextButton(onClick = { 
                        showDeleteConfirm?.let { viewModel?.deleteCustomPreset(it) }
                        showDeleteConfirm = null
                    }) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        AnimatedItem {
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardHeader(
                        title = stringResource(R.string.visualizer_presets)
                    )
                }

                val sortedPresets = remember(effectivePresets, favorites) {
                    effectivePresets.sortedByDescending { favorites.contains(it.key) }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sortedPresets.isNotEmpty()) {
                        ExpressiveSplitButton(
                            items = sortedPresets,
                            selectedItem = sortedPresets.firstOrNull { it.key == effectiveSelectedPreset }
                                ?: sortedPresets.first(),
                            onItemSelection = { preset -> 
                                onPresetSelected(preset.key)
                                viewModel?.setSelectedPreset(preset.key)
                            },
                            labelProvider = { preset -> preset.key },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Crossfade(
                                targetState = selectedInfo?.description,
                                label = "desc_fade",
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                modifier = Modifier.weight(1f)
                            ) { description ->
                                Text(
                                    text = description ?: stringResource(R.string.glyph_no_config),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (selectedInfo?.description?.startsWith("Custom:") == true) {
                                IconButton(
                                    onClick = { showDeleteConfirm = selectedInfo.key },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        FontAwesomeIcons.Solid.Trash,
                                        contentDescription = "Delete Local Preset",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (configVersion.contains(".simple")) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Update Required",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "Download full config to see presets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(86.dp)) //no one will notice
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CaptureSourceCard(
    selectedSource: AudioCaptureService.CaptureSource,
    networkPacketsReceived: Int = 0,
    bluetoothDeviceName: String = "",
    bluetoothDeviceAddress: String = "",
    isRunning: Boolean = false,
    onToggleVisualizer: () -> Unit = {},
    onSourceSelected: (AudioCaptureService.CaptureSource) -> Unit
) {
    MockupCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = "Audio Input Source")
        Spacer(modifier = Modifier.height(10.dp))
        val mainSources = listOf(
            Triple(
                AudioCaptureService.CaptureSource.INTERNAL,
                stringResource(R.string.capture_media_projection),
                FontAwesomeIcons.Solid.Tv
            ),
            Triple(
                AudioCaptureService.CaptureSource.MIC,
                stringResource(R.string.capture_microphone),
                FontAwesomeIcons.Solid.Microphone
            ),
            Triple(
                AudioCaptureService.CaptureSource.VIZUALIZER,
                stringResource(R.string.capture_vizualizer),
                FontAwesomeIcons.Brands.Android
            ),
            Triple(
                AudioCaptureService.CaptureSource.SPOTIFY,
                "Spotify Player",
                Icons.Default.MusicNote
            ),
            Triple(
                AudioCaptureService.CaptureSource.NETWORK,
                "Desktop Companion (UDP)",
                FontAwesomeIcons.Solid.NetworkWired
            ),
            Triple(
                AudioCaptureService.CaptureSource.BLUETOOTH,
                "Desktop Companion (BT)",
                FontAwesomeIcons.Brands.Bluetooth
            )
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mainSources.forEach { (source, label, icon) ->
                val isSelected = selectedSource == source
                val isInternal = source == AudioCaptureService.CaptureSource.INTERNAL
                val isEnabled = !isInternal || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

                OptionTile(
                    label = if (isInternal && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "$label (API 29+)"
                    else label,
                    icon = icon,
                    isSelected = isSelected,
                    enabled = isEnabled,
                    onClick = { onSourceSelected(source) },
                    modifier = Modifier.height(64.dp),
                    maxLines = 2
                )
            }
        }

        if (selectedSource == AudioCaptureService.CaptureSource.NETWORK) {
            val ip = AudioCaptureService.getLocalIpAddress()
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(mockupAccentColor().copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, mockupAccentColor().copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0xFF4CAF50) else Color(0xFFFFA000))
                    )
                    Text(
                        text = if (isRunning) "LISTENER ACTIVE (PORT 12347)" else "READY (TAP START TO LISTEN)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                }

                Text(
                    text = "Stream 16-bit PCM (48kHz Mono) to:",
                    style = MaterialTheme.typography.bodySmall,
                    color = mockupSubtextColor()
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "$ip:12347",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = mockupTextColor(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (networkPacketsReceived > 0) {
                    Text(
                        text = "Packets Received: $networkPacketsReceived",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = mockupAccentColor()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onToggleVisualizer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else mockupAccentColor()
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(
                        text = if (isRunning) "Stop Listening" else "Start Listening",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedSource == AudioCaptureService.CaptureSource.BLUETOOTH) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(mockupAccentColor().copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, mockupAccentColor().copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0xFF2196F3) else Color(0xFFFFA000))
                    )
                    Text(
                        text = if (isRunning) "BLUETOOTH LISTENER ACTIVE" else "BLUETOOTH READY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isRunning) Color(0xFF2196F3) else Color(0xFFFFA000)
                    )
                }

                if (bluetoothDeviceName.isNotEmpty()) {
                    Text(
                        text = "Device: $bluetoothDeviceName",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = mockupTextColor()
                    )
                    if (bluetoothDeviceAddress.isNotEmpty()) {
                        Text(
                            text = "MAC: $bluetoothDeviceAddress",
                            style = MaterialTheme.typography.labelSmall,
                            color = mockupSubtextColor()
                        )
                    }
                }

                Text(
                    text = "Pair your PC and run companion with --bt (UUID: ...7e45)",
                    style = MaterialTheme.typography.bodySmall,
                    color = mockupSubtextColor(),
                    textAlign = TextAlign.Center
                )

                if (networkPacketsReceived > 0) {
                    Text(
                        text = "Data link established • Packets: $networkPacketsReceived",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = mockupAccentColor()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onToggleVisualizer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else mockupAccentColor()
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(
                        text = if (isRunning) "Stop Bluetooth" else "Start Bluetooth",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedSource == AudioCaptureService.CaptureSource.VIZUALIZER) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.audio_warning_vizualizer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LatencyCard(
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    wizardState: LatencyWizard.State,
    onRunWizard: () -> Unit,
    onResetWizard: () -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String?
) {
    val haptics = LocalHapticFeedback.current
    var draggingIndex by remember { mutableIntStateOf(-1) }

    val visualOrder = remember(latencyPresets) {
        latencyPresets.mapIndexed { i, v -> i to v }
            .sortedBy { it.second }
            .map { it.first }
    }

    var isFirstOrderChange by remember { mutableStateOf(true) }
    LaunchedEffect(visualOrder) {
        if (isFirstOrderChange) {
            isFirstOrderChange = false
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val activeIndex = if (draggingIndex != -1) draggingIndex else latencyPresets.indexOf(latencyMs)

    val updateLatency = { newValue: Int ->
        val clampedValue = newValue.coerceIn(0, 500)
        if (draggingIndex == -1) draggingIndex = latencyPresets.indexOf(latencyMs)

        onLatencyChanged(clampedValue)

        if (draggingIndex != -1) {
            val currentList = latencyPresets.toMutableList()
            val isColliding = currentList.mapIndexed { i, v -> i to v }
                .any { (i, v) -> i != draggingIndex && v == clampedValue }

            if (!isColliding) {
                currentList[draggingIndex] = clampedValue
                onLatencyPresetsChanged(currentList)
            }
        }
    }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(
                R.string.latency_compensation
            ), trailingContent = {
                Text(
                    text = "${latencyMs}ms",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            })
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    MaterialTheme.shapes.large
                )
                .padding(4.dp)
        ) {
            val spacing = 4.dp
            val itemWidth = (maxWidth - (spacing * (latencyPresets.size - 1))) / latencyPresets.size

            latencyPresets.forEachIndexed { index, preset ->
                val isSelected = index == activeIndex
                val visualIndex = visualOrder.indexOf(index)
                val targetOffset = (itemWidth + spacing) * visualIndex

                val animatedX by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                    label = "swap"
                )

                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .offset(x = animatedX)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingIndex = index
                            onLatencyChanged(preset)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${preset}ms",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveSlider(
            value = latencyMs.toFloat(),
            onValueChange = { updateLatency(it.toInt()) },
            valueRange = 0f..500f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(-10, -1, 1, 10).forEach { amount ->
                FineTuneButton(
                    amount = amount,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        updateLatency(latencyMs + amount)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Latency Wizard Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Latency Wizard",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (wizardState) {
                            is LatencyWizard.State.Idle -> "Sync lights using your microphone"
                            is LatencyWizard.State.Preparing -> "Preparing..."
                            is LatencyWizard.State.Recording -> "Recording pulse... Please stay quiet."
                            is LatencyWizard.State.Analyzing -> "Analyzing..."
                            is LatencyWizard.State.Success -> "Success! Detected ${wizardState.latencyMs}ms delay."
                            is LatencyWizard.State.Error -> "Error: ${wizardState.message}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = {
                        if (wizardState is LatencyWizard.State.Success || wizardState is LatencyWizard.State.Error) {
                            onResetWizard()
                        } else if (wizardState is LatencyWizard.State.Idle) {
                            onRunWizard()
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = if (wizardState is LatencyWizard.State.Idle)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    enabled = wizardState !is LatencyWizard.State.Preparing && 
                              wizardState !is LatencyWizard.State.Recording &&
                              wizardState !is LatencyWizard.State.Analyzing
                ) {
                    Text(
                        text = when (wizardState) {
                            is LatencyWizard.State.Idle -> "Start"
                            is LatencyWizard.State.Success, is LatencyWizard.State.Error -> "Reset"
                            else -> "Wait"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (wizardState is LatencyWizard.State.Idle)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CardHeader(title = "Auto-Memorize Device")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (autoDeviceEnabled)
                            stringResource(
                                R.string.saving_latency_for,
                                connectedDeviceName
                                    ?: stringResource(R.string.internal_speaker)
                            )
                        else stringResource(R.string.manual_mode_global_latency),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = autoDeviceEnabled,
                    onCheckedChange = onAutoDeviceToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun FFTSpectrumCard(fftData: () -> FloatArray, bananaMode: Boolean = false, penisMode: Boolean = false) {
    val haptics = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var touchX by remember { mutableStateOf<Float?>(null) }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    isExpanded = !isExpanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    FontAwesomeIcons.Solid.Trophy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.live_spectrum),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (isExpanded) FontAwesomeIcons.Solid.ChevronUp else FontAwesomeIcons.Solid.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { touchX = it.x },
                                onDrag = { change, _ ->
                                    change.consume()
                                    touchX = change.position.x
                                },
                                onDragEnd = { touchX = null },
                                onDragCancel = { touchX = null }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    touchX = it.x
                                    tryAwaitRelease()
                                    touchX = null
                                }
                            )
                        }
                ) {
                    val barPath = remember { Path() }
                    val fillPath = remember { Path() }
                    val primaryColor = MaterialTheme.colorScheme.primary
                    
                    val gradient = remember(primaryColor) {
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.6f),
                                primaryColor.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = 400f // Approximate, will be scaled
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val data = fftData()
                        if (data.isEmpty()) return@Canvas

                        val w = size.width
                        val h = size.height
                        
                        barPath.reset()

                        val points = data.size - 1
                        var first = true
                        
                        // Optimize: skip some points for performance if needed, 
                        // but 512 points should be fine if we don't allocate paths.
                        for (i in 5..points) {
                            val fraction = i.toFloat() / points
                            val mag = data[i]

                            // Nonlinear scaling for better visuals
                            val scaledMag = (mag * 1.2f).coerceIn(0f, 1.2f)
                            val y = h - (scaledMag * (h - 40f)) - 20f
                            val x = fraction * w

                            if (first) {
                                barPath.moveTo(x, y)
                                first = false
                            } else {
                                barPath.lineTo(x, y)
                            }
                        }

                        fillPath.reset()
                        fillPath.addPath(barPath)
                        fillPath.lineTo(w, h)
                        fillPath.lineTo(0f, h)
                        fillPath.close()

                        drawPath(path = fillPath, brush = gradient)

                        // Main line
                        drawPath(
                            path = barPath,
                            color = primaryColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        touchX?.let { tx ->
                            val x = tx.coerceIn(0f, w)
                            drawLine(
                                color = primaryColor.copy(alpha = 0.5f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
                            )
                        }
                    }

                    if (bananaMode) {
                        Icon(
                            painter = painterResource(R.drawable.banana),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(48.dp)
                        )
                    } else if (penisMode) {
                        Icon(
                            painter = painterResource(R.drawable.penis),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(48.dp)
                        )
                    }

                    touchX?.let { tx ->
                        val fraction = (tx / constraints.maxWidth.toFloat()).coerceIn(0f, 1f)
                        val logMin = log10(20f)
                        val logMax = log10(20000f)
                        val logFreq = logMin + fraction * (logMax - logMin)
                        val freq = 10f.pow(logFreq)

                        val text = if (freq >= 1000) String.format(
                            Locale.US,
                            "%.1fkHz",
                            freq / 1000f
                        ) else String.format(Locale.US, "%dHz", freq.toInt())
                        val txDp = with(LocalDensity.current) { tx.toDp() }

                        Surface(
                            modifier = Modifier
                                .offset(
                                    x = (txDp - 30.dp).coerceIn(4.dp, maxWidth - 64.dp),
                                    y = 12.dp
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Frequency labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val freqLabels = listOf("30Hz", "100Hz", "1kHz", "10kHz", "20kHz")
                    freqLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.FineTuneButton(
    amount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isAnimating = true
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    delay(100.milliseconds)
                    isAnimating = false
                }
            }
        }
    }

    val animatedWeight by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "weight"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isAnimating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier
            .weight(animatedWeight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (amount > 0) "+$amount" else "$amount",
                style = MaterialTheme.typography.labelMedium,
                color = if (isAnimating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/**
 * Inbuilt Spotify Control Panel for Glyphix (Audio) page
 */
@Composable
fun GlyphixSpotifyControlPanel(
    playbackState: SpotifyPlaybackState?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenSpotifyTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState?.item
    val isPlaying = playbackState?.is_playing == true
    val currentMs = playbackState?.progress_ms ?: 0L
    val durationMs = track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val progressFraction = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    MockupCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar: Spotify Logo Badge + Device + Open Tab Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Spotify Player",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = Color(0xFF1DB954)
                    )
                }

                Surface(
                    onClick = onOpenSpotifyTab,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = mockupTextColor()
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = mockupSubtextColor(),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            // Track details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val coverUrl = track?.imageUrl
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track?.name ?: "No Track Playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artistNames ?: "Open Spotify to choose music",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = mockupSubtextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = if (isSeeking) seekFraction else progressFraction,
                    onValueChange = {
                        isSeeking = true
                        seekFraction = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekFraction * durationMs).toLong()
                        onSeek(targetMs)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1DB954),
                        activeTrackColor = Color(0xFF1DB954),
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dispCurrentMs = if (isSeeking) (seekFraction * durationMs).toLong() else currentMs
                    Text(
                        text = formatDuration(dispCurrentMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = mockupSubtextColor()
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = mockupSubtextColor()
                    )
                }
            }

            // Controls Row: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                val isShuffle = playbackState?.shuffle_state == true
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF1DB954) else mockupSubtextColor(),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Previous
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = mockupTextColor(),
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Play / Pause
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = Color(0xFF1DB954),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Next
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = mockupTextColor(),
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Repeat
                val repeatState = playbackState?.repeat_state ?: "off"
                val isRepeatActive = repeatState != "off"
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatState == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) Color(0xFF1DB954) else mockupSubtextColor(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopCompanionStatusCard(
    captureSource: AudioCaptureService.CaptureSource,
    isRunning: Boolean,
    networkPacketsReceived: Int,
    bluetoothDeviceName: String,
    bluetoothDeviceAddress: String,
    desktopSyncDirection: String = "PHONE_TO_PC",
    pcPacketsSent: Int = 0,
    pcCompanionIp: String = "",
    isPcStreamingActive: Boolean = false,
    onTogglePcStream: (Boolean) -> Unit = {},
    onPcIpChanged: (String) -> Unit = {},
    onDiscoverPc: () -> Unit = {},
    onSyncDirectionChanged: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val ip = remember { AudioCaptureService.getLocalIpAddress() }
    var copied by remember { mutableStateOf(false) }

    MockupCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Icon + Title + Status Pill + Dismiss Close Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(mockupAccentColor().copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (desktopSyncDirection == "PHONE_TO_PC")
                                Icons.Outlined.Devices
                            else if (captureSource == AudioCaptureService.CaptureSource.NETWORK)
                                Icons.Outlined.Wifi
                            else
                                Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = mockupAccentColor(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (desktopSyncDirection == "PHONE_TO_PC")
                                "Desktop Sync (OpenRGB)"
                            else if (captureSource == AudioCaptureService.CaptureSource.NETWORK)
                                "Desktop Companion (UDP)"
                            else
                                "Desktop Companion (BT)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = mockupTextColor()
                        )
                        Text(
                            text = if (desktopSyncDirection == "PHONE_TO_PC")
                                "Stream phone audio to PC hardware"
                            else
                                "Stream PC audio to phone glyphs",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = mockupSubtextColor()
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge
                    val (statusText, statusColor) = if (desktopSyncDirection == "PHONE_TO_PC") {
                        when {
                            isPcStreamingActive && isRunning -> "STREAMING" to Color(0xFF4CAF50)
                            isPcStreamingActive && !isRunning -> "WAITING" to Color(0xFFFFA000)
                            else -> "OFF" to mockupSubtextColor()
                        }
                    } else {
                        val isConnected = networkPacketsReceived > 0
                        when {
                            !isRunning -> "OFFLINE" to mockupSubtextColor()
                            isConnected -> "STREAMING" to Color(0xFF4CAF50)
                            else -> "LISTENING" to Color(0xFFFFA000)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = statusColor
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = mockupSubtextColor(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Direction Selector Segmented Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isPhoneToPc = desktopSyncDirection == "PHONE_TO_PC"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSyncDirectionChanged("PHONE_TO_PC")
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPhoneToPc) mockupAccentColor() else Color.Transparent
                ) {
                    Text(
                        text = "Phone → PC (OpenRGB)",
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isPhoneToPc) Color.White else mockupTextColor()
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSyncDirectionChanged("PC_TO_PHONE")
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isPhoneToPc) mockupAccentColor() else Color.Transparent
                ) {
                    Text(
                        text = "PC → Phone (Glyphs)",
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (!isPhoneToPc) Color.White else mockupTextColor()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

            if (desktopSyncDirection == "PHONE_TO_PC") {
                // PHONE TO PC MODE UI
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Target PC IP Field + Discover Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pcCompanionIp,
                            onValueChange = onPcIpChanged,
                            label = { Text("PC Companion IP", fontSize = 11.sp) },
                            placeholder = { Text("192.168.1.X", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mockupAccentColor(),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedLabelColor = mockupAccentColor()
                            )
                        )

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDiscoverPc()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mockupAccentColor().copy(alpha = 0.15f),
                                contentColor = mockupAccentColor()
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "DISCOVER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Streaming Toggle Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync to PC (OpenRGB)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = mockupTextColor()
                                )
                                Text(
                                    text = if (isPcStreamingActive)
                                        "Streaming active • $pcPacketsSent packets sent"
                                    else
                                        "Streams phone music to PC OpenRGB via UDP",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = if (isPcStreamingActive) Color(0xFF4CAF50) else mockupSubtextColor()
                                    )
                                )
                            }

                            Switch(
                                checked = isPcStreamingActive,
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTogglePcStream(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = mockupAccentColor()
                                )
                            )
                        }
                    }

                    if (isPcStreamingActive && !isRunning) {
                        Text(
                            text = "Audio capture is paused. Tap Start Visualizer below to begin sending audio to PC.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFFFFA000)
                            )
                        )
                    } else if (!isPcStreamingActive) {
                        Text(
                            text = "Tip: Make sure the Desktop Companion on PC is running and in 'Phone → PC' mode.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = mockupSubtextColor()
                        )
                    }
                }
            } else {
                // PC TO PHONE MODE UI
                if (captureSource == AudioCaptureService.CaptureSource.NETWORK) {
                    // Connection Info Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "PHONE IP & PORT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = mockupSubtextColor()
                                )
                                Text(
                                    text = "$ip:12347",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = mockupTextColor()
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboardManager.setText(AnnotatedString(ip))
                                    copied = true
                                }
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy IP",
                                    tint = if (copied) Color(0xFF4CAF50) else mockupAccentColor(),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (networkPacketsReceived > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Receiving audio stream ($networkPacketsReceived packets)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF4CAF50)
                            )
                        }
                    } else {
                        Text(
                            text = "Enter $ip in the Desktop Companion on your PC (in 'PC → Phone' mode), or click DISCOVER to connect automatically.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = mockupSubtextColor()
                        )
                    }
                } else {
                    // Bluetooth Info
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (bluetoothDeviceName.isNotEmpty()) {
                            Text(
                                text = "Connected Device: $bluetoothDeviceName",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = mockupTextColor()
                            )
                            if (bluetoothDeviceAddress.isNotEmpty()) {
                                Text(
                                    text = "MAC: $bluetoothDeviceAddress",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mockupSubtextColor()
                                )
                            }
                        } else {
                            Text(
                                text = "Pair your Nothing Phone with your PC in Windows Bluetooth settings, then run the companion with Bluetooth mode.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = mockupSubtextColor()
                            )
                        }

                        if (networkPacketsReceived > 0) {
                            Text(
                                text = "Receiving audio stream ($networkPacketsReceived packets)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

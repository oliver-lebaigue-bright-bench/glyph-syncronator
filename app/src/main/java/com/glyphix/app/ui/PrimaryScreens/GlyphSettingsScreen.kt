package com.glyphix.app.ui.PrimaryScreens

import androidx.activity.compose.BackHandler
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.R
import com.glyphix.app.service.AudioCaptureService
import com.glyphix.app.logic.AutoPresetEngine
import com.glyphix.app.ui.*
import kotlin.math.pow


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlyphsScreen(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
    maxBrightness: Int,
    onMaxBrightnessChanged: (Int) -> Unit,
    presets: List<AudioCaptureService.PresetInfo>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    isRunning: Boolean,
    selectedDevice: Int,
    viewModel: MainViewModel,
    vizStateProvider: () -> FloatArray = { floatArrayOf() },
    padding: PaddingValues = PaddingValues(),
    onDismiss: (() -> Unit)? = null,
) {
    if (onDismiss != null) {
        BackHandler { onDismiss() }
    }
    val mainScrollState = rememberScrollState()
    val context = LocalContext.current
    val hapticsLocal = androidx.compose.ui.platform.LocalHapticFeedback.current

    val configStatus by viewModel.configUpdateStatus.collectAsStateWithLifecycle()
    val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
    val remoteVersion by viewModel.remoteConfigVersion.collectAsStateWithLifecycle()

    val selectedInfo = remember(selectedPreset, presets) {
        presets.firstOrNull { it.key == selectedPreset } ?: presets.firstOrNull()
    }

    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Preset?") },
            text = { Text("Are you sure you want to delete the local preset '${showDeleteConfirm}'?") },
            confirmButton = {
                TextButton(onClick = { 
                    showDeleteConfirm?.let { viewModel.deleteCustomPreset(it) }
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

    StaggeredEntranceColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(mainScrollState)
            .padding(horizontal = LocalAppSpacing.current.edge),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (onDismiss != null) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            AnimatedItem {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphixBackButton(onClick = onDismiss)
                    Spacer(modifier = Modifier.width(16.dp))
                    ScreenTitle(text = "Glyphs", modifier = Modifier.padding(bottom = 0.dp))
                }
            }
        }

        // Header with external toggle for glyph visualization
        val DEFAULT_BR = 4095
        val lastNonZero = remember { mutableIntStateOf(if (maxBrightness > 0) maxBrightness else DEFAULT_BR) }
        androidx.compose.runtime.LaunchedEffect(maxBrightness) {
            if (maxBrightness > 0) lastNonZero.intValue = maxBrightness
        }

        val glyphEnabled = maxBrightness > 0
        AnimatedItem {
            AnimatedToggleCard(
                title = "Glyph visualisation",
                checked = glyphEnabled,
                onCheckedChange = { switchEnabled ->
                    hapticsLocal.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (switchEnabled) {
                        onMaxBrightnessChanged(lastNonZero.intValue)
                    } else {
                        onMaxBrightnessChanged(0)
                    }
                },
                disabledTopSpacerFraction = 0.4f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = glyphEnabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 420),
                    initialOffsetY = { fullHeight -> fullHeight / 3 }
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 280),
                    targetOffsetY = { fullHeight -> fullHeight / 5 }
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                AnimatedItem {
                    BrightnessCard(
                        maxBrightness = maxBrightness,
                        enabled = glyphEnabled,
                        lastNonZero = lastNonZero.intValue,
                        onLastNonZeroChanged = { v -> lastNonZero.intValue = v },
                        onMaxBrightnessChanged = onMaxBrightnessChanged
                    )
                }

                // ── Presets & Auto Preset Area ──────────────────────────────
                val isAutoPresetEnabled by viewModel.isAutoPresetEnabled.collectAsStateWithLifecycle()
                val autoPresetState by viewModel.autoPresetState.collectAsStateWithLifecycle()
                val fftState by viewModel.fftState.collectAsStateWithLifecycle()

                // ── Card 1: Auto Preset Selector ──────────────────────────────
                AnimatedItem {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Auto Preset Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = FontAwesomeIcons.Solid.Magic,
                                    contentDescription = null,
                                    tint = if (isAutoPresetEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Auto Preset Selector",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Matches presets by tempo & vibe",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAutoPresetEnabled,
                                onCheckedChange = { viewModel.setAutoPresetEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Auto Preset Active Dashboard (BPM Box + Preset Selected Box + Live Stream Graph)
                        AnimatedVisibility(
                            visible = isAutoPresetEnabled,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(),
                            exit = shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Small Box with BPM on top and BPM number just below it
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.width(76.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "BPM",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (autoPresetState.bpm > 0) "${autoPresetState.bpm}" else "--",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (autoPresetState.bpm > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Preset Selected Box
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(58.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = FontAwesomeIcons.Solid.Music,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Column(verticalArrangement = Arrangement.Center) {
                                                Text(
                                                    text = "Preset Selected",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = autoPresetState.activePresetKey.ifEmpty { selectedPreset },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // Live Stream Graph in Auto Preset Mode
                                LiveStreamGraphCard(
                                    fftData = { fftState },
                                    isRunning = isRunning,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // ── Card 2: Visualizer Presets (Shows Live Stream Graph in Manual Preset Mode) ──
                AnimatedVisibility(
                    visible = !isAutoPresetEnabled,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut()
                ) {
                    AnimatedItem {
                        ExpressiveCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CardHeader(
                                title = stringResource(
                                    R.string.visualizer_presets
                                )
                            )

                            val favorites by viewModel.favoritePresets.collectAsStateWithLifecycle()
                            val sortedPresets = remember(presets, favorites) {
                                presets.sortedByDescending { favorites.contains(it.key) }
                            }

                            // Live Stream Graph in Manual Preset Mode
                            LiveStreamGraphCard(
                                fftData = { fftState },
                                isRunning = isRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 1. Grouped Expressive Row for your presets
                                if (sortedPresets.isNotEmpty()) {
                                    ExpressiveSplitButton(
                                        items = sortedPresets,
                                        // If no preset matches, safely fall back to the first item in the list
                                        selectedItem = sortedPresets.firstOrNull { it.key == selectedPreset }
                                            ?: sortedPresets.first(),
                                        onItemSelection = { preset -> onPresetSelected(preset.key) },
                                        labelProvider = { preset -> preset.key },
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                                onClick = { viewModel.showEditor(selectedInfo.key) },
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    FontAwesomeIcons.Solid.Edit,
                                                    contentDescription = "Edit Custom Preset",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { showDeleteConfirm = selectedInfo.key },
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    FontAwesomeIcons.Solid.Trash,
                                                    contentDescription = "Delete Local Preset",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        FilledTonalButton(
                                            onClick = { viewModel.showEditor() },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(FontAwesomeIcons.Solid.Plus, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Custom Preset Maker", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                } else {
                                    // Show loading or Update message if presets are empty
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
                }

                if (isRunning) {
                    val previewHeight = when (selectedDevice) {
                        com.glyphix.app.model.DeviceProfile.DEVICE_NP2 -> 530.dp
                        else -> 560.dp
                    }
                    AnimatedItem {
                        GlyphPreview(
                            vizStateProvider = vizStateProvider,
                            device = selectedDevice,
                            modifier = Modifier
                                .width(380.dp)
                                .height(previewHeight)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }

                AnimatedItem {
                    ExpressiveCard( //gamma
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CardHeader(
                            title = stringResource(
                                R.string.gamma_control
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GammaPreviewCard(gammaValue = gammaValue)
                            BodyText(
                                text = stringResource(R.string.gamma_description),
                                modifier = Modifier.weight(1f),
                                size = 14.sp,
                                lineHeight = 22.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GammaSlider(gammaValue = gammaValue, onGammaChanged = onGammaChanged)
                    }
                }
                // ── Zones Config download ──────────────────────────────────────────────
                LaunchedEffect(Unit) {
                    viewModel.checkRemoteConfigVersion()
                }

                LaunchedEffect(configStatus) {
                    when (val status = configStatus) {
                        is MainViewModel.ConfigUpdateStatus.Success -> {
                            Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetConfigUpdateStatus()
                        }
                        is MainViewModel.ConfigUpdateStatus.Error -> {
                            Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                            viewModel.resetConfigUpdateStatus()
                        }
                        else -> {}
                    }
                }

                AnimatedItem {
                    ExpressiveCard {
                        CardHeader(title = "Visualizer Configuration")

                        BodyText(
                            text = "The zones.config file defines how frequencies map to Glyph LEDs. Updating from GitHub ensures support for new devices and presets.",
                            size = 13.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Version: $configVersion",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (remoteVersion != null && remoteVersion != "Unknown") {
                                        val isUpdateAvailable = remoteVersion != configVersion
                                        Text(
                                            text = if (isUpdateAvailable) "Latest: $remoteVersion" else "Up to date",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUpdateAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                if (remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "UPDATE AVAILABLE",
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val filePickerLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.GetContent()
                        ) { uri ->
                            uri?.let { viewModel.importZonesConfig(uri) }
                        }

                        val isUpdateAvailable =
                            remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion

                        ExpressiveSplitButton(
                            primaryText = if (isUpdateAvailable) "Update Now" else "Check GitHub",
                            primaryIcon = if (configStatus is MainViewModel.ConfigUpdateStatus.Updating) FontAwesomeIcons.Solid.Sync else FontAwesomeIcons.Solid.CloudDownloadAlt,
                            onPrimaryClick = { viewModel.updateZonesConfig() },
                            secondaryText = "Local",
                            secondaryIcon = FontAwesomeIcons.Solid.FolderOpen,
                            onSecondaryClick = { filePickerLauncher.launch("*/*") },
                            enabled = configStatus is MainViewModel.ConfigUpdateStatus.Idle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}


@Composable
fun BrightnessCard(
    maxBrightness: Int,
    enabled: Boolean,
    lastNonZero: Int,
    onLastNonZeroChanged: (Int) -> Unit,
    onMaxBrightnessChanged: (Int) -> Unit,
) {
    androidx.compose.ui.platform.LocalHapticFeedback.current

    val MIN_BRIGHTNESS = 50
    val MAX_BRIGHTNESS = 4500

    // Quadratic mapping: slider position (0..1) -> value = min + (max-min) * pos^2
    fun linearToPos(linear: Int): Float {
        val clamped = linear.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        val ratio = (clamped - MIN_BRIGHTNESS).toFloat() / (MAX_BRIGHTNESS - MIN_BRIGHTNESS).toFloat()
        return kotlin.math.sqrt(ratio.coerceIn(0f, 1f))
    }

    fun posToLinear(pos: Float): Int {
        val p = pos.coerceIn(0f, 1f)
        val valf = MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * (p * p)
        return kotlin.math.round(valf).toInt()
    }

    val posValue = remember(maxBrightness, lastNonZero) { linearToPos(if (maxBrightness > 0) maxBrightness else lastNonZero) }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        CardHeader(
            title = "Brightness:",
            trailingContent = {
                Text(
                    text = "${if (maxBrightness > 0) maxBrightness else lastNonZero}/${MAX_BRIGHTNESS}" + (if (maxBrightness == 4095) " (default)" else ""),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            })

        ExpressiveSlider(
            value = posValue,
            onValueChange = { newPos ->
                val newLinearValue = posToLinear(newPos)
                onLastNonZeroChanged(newLinearValue)
                if (enabled) onMaxBrightnessChanged(newLinearValue)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun GammaSlider(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
) {
    CardHeader(
        title = stringResource(
            R.string.light_gamma
        ), trailingContent = {
            Text(
                text = String.format("%.1f", gammaValue),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        })

        ExpressiveSlider(
            value = gammaValue,
            onValueChange = onGammaChanged,
            valueRange = 0.4f..4.5f,
            modifier = Modifier.fillMaxWidth(),
        )
}

@Composable
fun GammaPreviewCard(gammaValue: Float) {
    val animatedGamma by animateFloatAsState(
        targetValue  = gammaValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "gamma_curve",
    )

    val curvePath = remember { Path() }

    val gridColor = MaterialTheme.colorScheme.outline
    val accent    = MaterialTheme.colorScheme.primary

    Card(
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.size(130.dp, 130.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val pad       = 8f
            val right  = size.width - pad
            val bottom = size.height - pad
            val w = right - pad
            val h = bottom - pad

            drawLine(gridColor, Offset(pad, bottom), Offset(right, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(gridColor, Offset(pad, bottom), Offset(pad, pad),    strokeWidth = 4f, cap = StrokeCap.Round)

            val hStep = h / 4f
            val vStep = w / 4f
            repeat(3) { i ->
                drawLine(gridColor, Offset(pad,         bottom - hStep * (i + 1)), Offset(right, bottom - hStep * (i + 1)), strokeWidth = 1f)
                drawLine(gridColor, Offset(pad + vStep * (i + 1), bottom),         Offset(pad + vStep * (i + 1),
                    pad
                ),     strokeWidth = 1f)
            }

            curvePath.reset()
            curvePath.moveTo(pad, bottom)
            val steps = 50
            for (step in 1..steps) {
                val x = step / steps.toFloat()
                val y = x.pow(animatedGamma)
                curvePath.lineTo(pad + x * w, bottom - y * h)
            }
            drawPath(curvePath, accent, style = Stroke(width = 8f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun LiveStreamGraphCard(
    fftData: () -> FloatArray,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        FontAwesomeIcons.Solid.ChartArea,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Live Stream Graph",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isRunning) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Real-time FFT Spectrum Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                val barPath = remember { Path() }
                val fillPath = remember { Path() }
                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val data = fftData()
                    if (data.isNotEmpty()) {
                        barPath.reset()
                        val points = data.size - 1
                        var first = true

                        for (i in 5..points) {
                            val fraction = i.toFloat() / points
                            val mag = data[i]
                            val scaledMag = (mag * 1.2f).coerceIn(0f, 1.2f)
                            val y = h - (scaledMag * (h - 24f)) - 12f
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

                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.5f),
                                primaryColor.copy(alpha = 0.03f)
                            ),
                            startY = 0f,
                            endY = h
                        )

                        drawPath(path = fillPath, brush = gradientBrush)

                        // Main curve line
                        drawPath(
                            path = barPath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        // Flat subtle idle line
                        drawLine(
                            color = primaryColor.copy(alpha = 0.3f),
                            start = Offset(0f, h - 12f),
                            end = Offset(w, h - 12f),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}



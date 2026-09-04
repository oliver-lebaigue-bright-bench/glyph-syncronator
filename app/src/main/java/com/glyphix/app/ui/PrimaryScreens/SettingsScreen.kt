package com.glyphix.app.ui.PrimaryScreens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.glyphix.app.logic.LatencyWizard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.BuildConfig
import com.glyphix.app.R
import com.glyphix.app.model.DeviceProfile
import com.glyphix.app.ui.*

/**
 * Settings Screen replicating `Assets/settings.png` while maintaining
 * all original themes (Nothing, Glass, Music, Material You, Monster, etc.)
 * and full configuration features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    viewModel: MainViewModel,
    idleBreathingEnabled: Boolean,
    onIdleBreathingEnabledChanged: (Boolean) -> Unit,
    idlePattern: String,
    onIdlePatternChanged: (String) -> Unit,
    strobeEnabled: Boolean,
    onStrobeEnabledChanged: (Boolean) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
    onGoogleSignIn: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    onOpenProfile: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isGlass = LocalIsGlassTheme.current
    val haptics = LocalHapticFeedback.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val m3eEnabled by viewModel.m3eEnabled.collectAsStateWithLifecycle()
    val uiAmplitudeSyncEnabled by viewModel.uiAmplitudeSyncEnabled.collectAsStateWithLifecycle()
    val bananaMode by viewModel.bananaModeEnabled.collectAsStateWithLifecycle()
    val penisMode by viewModel.penisModeEnabled.collectAsStateWithLifecycle()
    val spectrumGain by viewModel.spectrumGain.collectAsStateWithLifecycle()
    val developerModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()
    val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var isThemeExpanded by remember { mutableStateOf(false) }
    var isFontExpanded by remember { mutableStateOf(false) }
    var isIdlePatternExpanded by remember { mutableStateOf(false) }

    StaggeredEntranceColumn(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Appearance Card (Replicating `settings.png` with animated inline drawers)
        AnimatedItem {
            MockupCard {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = mockupTextColor()
                )
                Spacer(Modifier.height(18.dp))

                // Row 1: Theme Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = mockupTextColor()
                        )
                        Text(
                            text = "App theme & color palette",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = mockupSubtextColor()
                        )
                    }
                    MockupPillButton(
                        text = selectedTheme,
                        onClick = { isThemeExpanded = !isThemeExpanded },
                        isExpanded = isThemeExpanded,
                        showChevron = true
                    )
                }

                AnimatedVisibility(
                    visible = isThemeExpanded,
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
                    val themes = listOf(
                        "Default" to Icons.Outlined.Palette,
                        "Nothing" to Icons.Outlined.DarkMode,
                        "Nothing Red" to Icons.Outlined.LocalFireDepartment,
                        "Glass" to Icons.Outlined.AutoAwesome,
                        "Music" to Icons.Outlined.MusicNote,
                        "Material You" to Icons.Outlined.ColorLens,
                        "Monster Classic" to Icons.Outlined.FlashOn,
                        "Monster Ultra White" to Icons.Outlined.LightMode
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        themes.forEach { (theme, icon) ->
                            OptionTile(
                                label = theme,
                                icon = icon,
                                isSelected = selectedTheme == theme,
                                onClick = {
                                    viewModel.setSelectedTheme(theme)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Row 2: Typography / Font Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Font",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = mockupTextColor()
                        )
                        Text(
                            text = "Nothing typography style",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = mockupSubtextColor()
                        )
                    }
                    MockupPillButton(
                        text = selectedFont,
                        onClick = { isFontExpanded = !isFontExpanded },
                        isExpanded = isFontExpanded,
                        showChevron = true
                    )
                }

                AnimatedVisibility(
                    visible = isFontExpanded,
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
                    val fonts = listOf(
                        "NDot" to Icons.Outlined.TextFields,
                        "NType" to Icons.Outlined.Title,
                        "Default" to Icons.Outlined.FontDownload
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3
                    ) {
                        fonts.forEach { (font, icon) ->
                            OptionTile(
                                label = font,
                                icon = icon,
                                isSelected = selectedFont == font,
                                onClick = {
                                    viewModel.setSelectedFont(font)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Row 3: Dynamic Audio Reactive UI Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dynamic color & motion",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = mockupTextColor()
                        )
                        Text(
                            text = "UI pulses to audio amplitude",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = mockupSubtextColor()
                        )
                    }
                    MockupPillToggle(
                        checked = uiAmplitudeSyncEnabled,
                        onCheckedChange = { viewModel.setUiAmplitudeSyncEnabled(it) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Row 4: Material 3 Expressive Motion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expressive animations",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    MockupPillToggle(
                        checked = m3eEnabled,
                        onCheckedChange = { viewModel.setM3EEnabled(it) }
                    )
                }
            }
        }

        // 2. Visualizer Card (Replicating `settings.png`)
        AnimatedItem {
            MockupCard {
                Text(
                    text = "Visualizer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = mockupTextColor()
                )
                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "sensitivity",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    val sensitivityLabel = when {
                        spectrumGain < 2.5f -> "Subtle"
                        spectrumGain < 5.0f -> "Balanced"
                        else -> "High"
                    }
                    Text(
                        text = sensitivityLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = mockupSubtextColor()
                    )

                    Slider(
                        value = spectrumGain,
                        onValueChange = { viewModel.setSpectrumGain(it) },
                        valueRange = 1.0f..8.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = mockupAccentColor(),
                            activeTrackColor = mockupAccentColor(),
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Strobe effect toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Strobe Mode",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    MockupPillToggle(
                        checked = strobeEnabled,
                        onCheckedChange = { onStrobeEnabledChanged(it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Silence Glyphs toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Disable Glyphs when silent",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    MockupPillToggle(
                        checked = disableGlyphsWhenSilent,
                        onCheckedChange = { onDisableGlyphsWhenSilentChanged(it) }
                    )
                }
            }
        }

        // Latency Compensation Card (Exact original LatencyCard moved to Settings)
        AnimatedItem {
            LatencySettingsCard(viewModel = viewModel)
        }

        // 3. Idle Pulse Card (Replicating `settings.png`)
        AnimatedItem {
            MockupCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Idle Pulse",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = mockupTextColor()
                        )
                        Text(
                            text = "Subtle Glyph Patterns",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = mockupSubtextColor()
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MockupPillButton(
                            text = idlePattern.replaceFirstChar { it.uppercase() },
                            onClick = { isIdlePatternExpanded = !isIdlePatternExpanded },
                            isExpanded = isIdlePatternExpanded,
                            showChevron = true
                        )
                        MockupPillToggle(
                            checked = idleBreathingEnabled,
                            onCheckedChange = { onIdleBreathingEnabledChanged(it) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isIdlePatternExpanded,
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
                    val patterns = listOf(
                        "pulse" to Icons.Outlined.GraphicEq,
                        "wave" to Icons.Outlined.Waves,
                        "circular" to Icons.Outlined.RotateRight,
                        "sweep" to Icons.Outlined.BlurOn
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        patterns.forEach { (pattern, icon) ->
                            OptionTile(
                                label = pattern.replaceFirstChar { it.uppercase() },
                                icon = icon,
                                isSelected = idlePattern == pattern,
                                onClick = {
                                    onIdlePatternChanged(pattern)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Easter Eggs / Fun Modes Card
        AnimatedItem {
            MockupCard {
                Text(
                    text = "Fun Modes",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = mockupTextColor()
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍌 Banana Mode",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    MockupPillToggle(
                        checked = bananaMode,
                        onCheckedChange = { viewModel.setBananaModeEnabled(it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍆 Penis Mode",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    MockupPillToggle(
                        checked = penisMode,
                        onCheckedChange = { viewModel.setPenisModeEnabled(it) }
                    )
                }
            }
        }

        // 5. About Card
        AnimatedItem {
            MockupCard {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = mockupTextColor()
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Version",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = mockupTextColor()
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mockupSubtextColor()
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Discord Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            uriHandler.openUri("https://discord.gg/Cq9Qff7Z7w")
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = mockupAccentColor(),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Join Discord Community",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = mockupTextColor()
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = mockupSubtextColor(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 6. Developer Options Card
        if (developerModeEnabled) {
            AnimatedItem {
                MockupCard {
                    Text(
                        text = "Developer Options",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = mockupTextColor()
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Developer Mode",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                ),
                                color = mockupTextColor()
                            )
                            Text(
                                text = "Advanced spoofing & logs",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = mockupSubtextColor()
                            )
                        }
                        MockupPillToggle(
                            checked = developerModeEnabled,
                            onCheckedChange = { viewModel.setDeveloperModeEnabled(it) }
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Spoof Device",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor()
                    )
                    Text(
                        text = "Force app to use a specific device profile",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = mockupSubtextColor()
                    )

                    Spacer(Modifier.height(12.dp))

                    val devices = listOf(
                        "Phone (1)" to DeviceProfile.DEVICE_NP1,
                        "Phone (2)" to DeviceProfile.DEVICE_NP2,
                        "Phone (2a)" to DeviceProfile.DEVICE_NP2A,
                        "Phone (3)" to DeviceProfile.DEVICE_NP3,
                        "Phone (3a)" to DeviceProfile.DEVICE_NP3A,
                        "Phone (4)" to DeviceProfile.DEVICE_NP4A,
                        "Phone (4 Pro)" to DeviceProfile.DEVICE_NP4APRO
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        devices.forEach { (name, id) ->
                            OptionTile(
                                label = name,
                                icon = Icons.Outlined.Smartphone,
                                isSelected = spoofedDevice == id,
                                onClick = { viewModel.setSpoofedDevice(id) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}

/**
 * Latency Compensation & Wizard Settings Card (Exact original LatencyCard)
 */
@Composable
private fun LatencySettingsCard(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
    val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
    val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
    val latencyWizardState by viewModel.latencyWizardState.collectAsStateWithLifecycle()
    val connectedDeviceName = MainActivity.serviceStatic?.getActiveAudioRouteName()

    val wizardPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.runLatencyWizard()
        }
    }

    LatencyCard(
        latencyMs = latencyMs,
        onLatencyChanged = { viewModel.setLatencyMs(it) },
        latencyPresets = latencyPresets,
        onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
        wizardState = latencyWizardState,
        onRunWizard = {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (status == PackageManager.PERMISSION_GRANTED) {
                viewModel.runLatencyWizard()
            } else {
                wizardPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onResetWizard = { viewModel.resetLatencyWizard() },
        autoDeviceEnabled = autoDeviceEnabled,
        onAutoDeviceToggle = { viewModel.setAutoDeviceMemorize(it) },
        connectedDeviceName = connectedDeviceName
    )
}

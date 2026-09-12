package com.glyphix.app.ui.SecondaryScreens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.logic.AiPresetResult
import com.glyphix.app.model.DeviceProfile
import com.glyphix.app.ui.MainViewModel
import com.glyphix.app.ui.mockupSurfaceColor
import com.glyphix.app.ui.mockupTextColor
import com.glyphix.app.ui.mockupSubtextColor
import java.util.Locale

private val INSPIRATION_VIBES = listOf(
    "Heavy EDM Drop" to "Explosive 808 sub-bass on battery and camera, with high-intensity progressive synth rises on the arc.",
    "Retro Synthwave" to "Warm, analog bass pulse in the center with shimmering, smooth 80s arpeggio waves across the upper segments.",
    "Trap & Drill" to "Aggressive, punchy kick drum gates on the bottom with ultra-fast ticking hi-hat sparkles on top camera.",
    "Acoustic Warmth" to "Organic, breathing frequency response tuned for acoustic guitar body and intimate vocal clarity.",
    "Liquid Drum & Bass" to "High-velocity rhythmic visualizer with fast 174 BPM syncopated basslines and airy atmospheric pads.",
    "Stepped VU Meter" to "Staggered amplitude threshold slicing across segments creating a fluid, professional volume level meter.",
    "Cyberpunk Matrix" to "Industrial synth stabs with sharp noise gates and intense mid-range distortion response.",
    "Lo-Fi Study Beats" to "Subtle, relaxed low-frequency glow with soft dynamic damping for laid-back study beats."
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiPresetGeneratorSheet(
    viewModel: MainViewModel,
    selectedDevice: Int,
    onDismiss: () -> Unit,
    onPresetGenerated: (AiPresetResult) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isGenerating by viewModel.isGeneratingAiPreset.collectAsStateWithLifecycle()
    val generationError by viewModel.aiGenerationError.collectAsStateWithLifecycle()

    var promptText by remember { mutableStateOf("") }
    var generatedResult by remember { mutableStateOf<AiPresetResult?>(null) }

    val effectiveDevice = remember(selectedDevice) {
        if (selectedDevice == DeviceProfile.DEVICE_UNKNOWN) DeviceProfile.DEVICE_NP2 else selectedDevice
    }
    val ledCount = remember(effectiveDevice) { DeviceProfile.getLedCount(effectiveDevice) }
    val deviceName = remember(effectiveDevice) { DeviceProfile.deviceName(effectiveDevice) }

    fun triggerGeneration(userPrompt: String) {
        val prompt = userPrompt.trim().ifEmpty { INSPIRATION_VIBES.random().second }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        generatedResult = null
        viewModel.generatePresetWithAi(prompt, effectiveDevice) { result ->
            generatedResult = result
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Preset Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = mockupTextColor()
                    )
                    Text(
                        text = "$deviceName • $ledCount Zones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error Display if any
            if (!generationError.isNullOrBlank()) {
                Text(
                    text = generationError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            val result = generatedResult
            if (result != null) {
                // Generated Result Preview Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = mockupSurfaceColor()),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "PRESET COMPOSED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (result.isOfflineGenerated) "Synthesis Engine" else "OpenRouter AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = mockupTextColor()
                        )

                        Text(
                            text = result.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = mockupSubtextColor(),
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                label = { Text("Decay: ${String.format(Locale.US, "%.2f", result.decayAlpha)}", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.GridOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                label = { Text("${result.zones.size} Zones Mapped", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Action Buttons
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        onPresetGenerated(result)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Apply to Glyph Studio",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        generatedResult = null
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Try Another Prompt", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                // Prompt Input Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Describe Your Concept",
                            style = MaterialTheme.typography.labelMedium,
                            color = mockupSubtextColor(),
                            fontWeight = FontWeight.SemiBold
                        )

                        Row {
                            IconButton(
                                onClick = {
                                    promptText = INSPIRATION_VIBES.random().second
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = "Random Prompt",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (promptText.isNotBlank()) {
                                TextButton(
                                    onClick = { promptText = "" },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Clear", fontSize = 12.sp, color = mockupSubtextColor())
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text(
                                "Describe the lighting behavior, musical genre, or acoustic response...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mockupSubtextColor().copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (promptText.isNotBlank() && !isGenerating) {
                                triggerGeneration(promptText)
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mockupTextColor(),
                            unfocusedTextColor = mockupTextColor(),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Quick Inspiration Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Inspiration Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = mockupSubtextColor(),
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        INSPIRATION_VIBES.forEach { (label, fullDesc) ->
                            val isSelected = promptText == fullDesc
                            InspirationPresetButton(
                                label = label,
                                fullDesc = fullDesc,
                                isSelected = isSelected,
                                onClick = {
                                    promptText = fullDesc
                                }
                            )
                        }
                    }
                }

                // Generating Progress & Shimmer Card
                if (isGenerating) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Composing Glyph Lighting...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "AI is mapping acoustic frequencies for $deviceName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mockupSubtextColor()
                                )
                            }
                        }
                    }
                }

                // Main Generate Action Button
                Button(
                    onClick = {
                        triggerGeneration(promptText)
                    },
                    enabled = !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isGenerating) "Generating..." else "Generate Preset",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}


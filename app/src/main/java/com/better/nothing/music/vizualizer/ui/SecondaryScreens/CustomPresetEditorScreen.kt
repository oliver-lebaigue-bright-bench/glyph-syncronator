package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import com.better.nothing.music.vizualizer.R as AppR
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.model.DeviceProfile
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.ui.ExpressiveRangeSlider
import com.better.nothing.music.vizualizer.ui.invLerpLog
import com.better.nothing.music.vizualizer.ui.lerpLog
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomPresetEditorScreen(
    onDismiss: () -> Unit,
    onSave: (String, List<AudioProcessor.ZoneSpec>, String?) -> Unit,
    onShare: (String, String, List<AudioProcessor.ZoneSpec>) -> Unit,
    selectedDevice: Int,
    fftState: FloatArray = floatArrayOf()
) {
    var presetName by remember { mutableStateOf("My Custom Preset") }
    var authorName by remember { mutableStateOf("Anonymous") }
    var showShareDialog by remember { mutableStateOf(false) }
    val ledCount = remember(selectedDevice) { DeviceProfile.getLedCount(selectedDevice) }
    val haptics = LocalHapticFeedback.current

    val zones = remember(selectedDevice) {
        val list = mutableStateListOf<AudioProcessor.ZoneSpec>()
        val minFreq = 60f
        val maxFreq = 16000f
        for (i in 0 until ledCount) {
            val low = minFreq * (maxFreq / minFreq).toDouble().pow(i.toDouble() / ledCount).toFloat()
            val high = minFreq * (maxFreq / minFreq).toDouble().pow((i + 1).toDouble() / ledCount).toFloat()
            list.add(AudioProcessor.ZoneSpec(low, high, Float.NaN, Float.NaN))
        }
        list
    }

    // Undo/Redo State
    val undoStack = remember { mutableStateListOf<List<AudioProcessor.ZoneSpec>>() }
    val redoStack = remember { mutableStateListOf<List<AudioProcessor.ZoneSpec>>() }

    fun pushUndo() {
        undoStack.add(zones.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(zones.toList())
            val previous = undoStack.removeAt(undoStack.size - 1)
            zones.clear()
            zones.addAll(previous)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(zones.toList())
            val next = redoStack.removeAt(redoStack.size - 1)
            zones.clear()
            zones.addAll(next)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    val selectedIndices = remember { mutableStateListOf<Int>(0) }
    var isMultiSelect by remember { mutableStateOf(false) }
    var isLivePreviewEnabled by remember { mutableStateOf(true) }

    val zoneIntensities = remember(fftState, zones) {
        val intensities = FloatArray(ledCount)
        if (fftState.isEmpty() || !isLivePreviewEnabled) return@remember intensities
        val hzPerBin = 44100f / 2048f
        zones.forEachIndexed { i, zone ->
            val binLo = (zone.lowHz / hzPerBin).toInt().coerceIn(0, fftState.lastIndex)
            val binHi = (zone.highHz / hzPerBin).toInt().coerceIn(binLo, fftState.lastIndex)
            var maxMag = 0f
            for (bin in binLo..binHi) {
                if (fftState[bin] > maxMag) maxMag = fftState[bin]
            }
            // Simple normalization for UI preview
            var intensity = (maxMag * 15f).coerceIn(0f, 1f)
            intensity = intensity * intensity // Shaping
            
            // Apply percent slice
            if (!zone.lowPercent.isNaN() && !zone.highPercent.isNaN()) {
                val low = zone.lowPercent / 100f
                val high = zone.highPercent / 100f
                intensity = when {
                    intensity <= low -> 0f
                    intensity >= high || high == low -> 1f
                    else -> (intensity - low) / (high - low)
                }
            }
            intensities[i] = intensity
        }
        intensities
    }

    fun distributeLogarithmically() {
        if (selectedIndices.isEmpty()) return
        pushUndo()
        val sorted = selectedIndices.sorted()
        val minFreq = 60f
        val maxFreq = 16000f
        val total = sorted.size
        
        sorted.forEachIndexed { i, idx ->
            val low = minFreq * (maxFreq / minFreq).toDouble().pow(i.toDouble() / total).toFloat()
            val high = minFreq * (maxFreq / minFreq).toDouble().pow((i + 1).toDouble() / total).toFloat()
            zones[idx] = AudioProcessor.ZoneSpec(low, high, Float.NaN, Float.NaN)
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun selectAll() {
        selectedIndices.clear()
        for (i in 0 until ledCount) selectedIndices.add(i)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun invertSelection() {
        val current = selectedIndices.toList()
        selectedIndices.clear()
        for (i in 0 until ledCount) {
            if (!current.contains(i)) selectedIndices.add(i)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun applyFrequencyPreset(low: Float, high: Float) {
        if (selectedIndices.isEmpty()) return
        pushUndo()
        selectedIndices.forEach { idx ->
            zones[idx] = AudioProcessor.ZoneSpec(low, high, zones[idx].lowPercent, zones[idx].highPercent)
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun mirrorHorizontally() {
        pushUndo()
        val mapping = when (selectedDevice) {
            DeviceProfile.DEVICE_NP1 -> mapOf(2 to 3, 5 to 4, 3 to 2, 4 to 5)
            DeviceProfile.DEVICE_NP2 -> mapOf(20 to 23, 19 to 22, 23 to 20, 22 to 19)
            DeviceProfile.DEVICE_NP2A -> mapOf(24 to 25, 25 to 24)
            DeviceProfile.DEVICE_NP3A -> mapOf(31 to 35, 32 to 34, 35 to 31, 34 to 32)
            DeviceProfile.DEVICE_NP4A, DeviceProfile.DEVICE_NP4B -> emptyMap()
            DeviceProfile.DEVICE_NP4APRO, DeviceProfile.DEVICE_NP3 -> {
                val w = DeviceProfile.getMatrixWidth(selectedDevice)
                val h = DeviceProfile.getMatrixHeight(selectedDevice)
                val map = mutableMapOf<Int, Int>()
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        val srcIdx = row * w + col
                        val targetCol = (w - 1) - col
                        val targetIdx = row * w + targetCol
                        if (srcIdx != targetIdx) map[srcIdx] = targetIdx
                    }
                }
                map
            }
            else -> emptyMap()
        }
        
        val sourceIndices = selectedIndices.toList()
        sourceIndices.forEach { idx ->
            mapping[idx]?.let { target ->
                zones[target] = zones[idx]
                if (!selectedIndices.contains(target)) selectedIndices.add(target)
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun invertFrequencies() {
        pushUndo()
        selectedIndices.forEach { idx ->
            val z = zones[idx]
            val min = 20f
            val max = 20000f
            val newLow = (max * min) / z.highHz
            val newHigh = (max * min) / z.lowHz
            zones[idx] = AudioProcessor.ZoneSpec(newLow, newHigh, z.lowPercent, z.highPercent)
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun shiftFrequencies(octaves: Float) {
        pushUndo()
        val factor = 2.0f.pow(octaves)
        selectedIndices.forEach { idx ->
            val z = zones[idx]
            val newLow = (z.lowHz * factor).coerceIn(20f, 19000f)
            val newHigh = (z.highHz * factor).coerceIn(newLow + 10f, 20000f)
            zones[idx] = AudioProcessor.ZoneSpec(newLow, newHigh, z.lowPercent, z.highPercent)
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun distributeIntensityProgress() {
        if (selectedIndices.size < 2) return
        pushUndo()
        val sorted = selectedIndices.sorted()
        val total = sorted.size
        val firstZone = zones[sorted.first()]
        
        sorted.forEachIndexed { i, idx ->
            val lowPerc = (i.toFloat() / total) * 100f
            val highPerc = ((i + 1).toFloat() / total) * 100f
            zones[idx] = AudioProcessor.ZoneSpec(firstZone.lowHz, firstZone.highHz, lowPerc, highPerc)
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(AppR.string.visual_preset_editor), style = MaterialTheme.typography.titleMedium)
                        Text(DeviceProfile.deviceName(selectedDevice), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(AppR.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showShareDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Public, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AppR.string.share))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            onSave(presetName, zones.toList(), authorName)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AppR.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (showShareDialog) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text(stringResource(AppR.string.share_to_community)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(AppR.string.share_preset_desc))
                        OutlinedTextField(
                            value = authorName,
                            onValueChange = { authorName = it },
                            label = { Text(stringResource(AppR.string.author_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onShare(presetName, authorName, zones.toList())
                        showShareDialog = false
                    }) {
                        Text(stringResource(AppR.string.share))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(stringResource(AppR.string.cancel))
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text(stringResource(AppR.string.preset_name)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color(0xFF1A1A1A))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EditableGlyphPreview(
                    device = selectedDevice,
                    selectedIndices = selectedIndices.toList(),
                    intensities = zoneIntensities,
                    onIndexSelected = { idx, multi ->
                        if (multi || isMultiSelect) {
                            if (selectedIndices.contains(idx)) selectedIndices.remove(idx)
                            else selectedIndices.add(idx)
                        } else {
                            selectedIndices.clear()
                            selectedIndices.add(idx)
                        }
                        selectedIndex = idx
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Live Preview Toggle
                FilterChip(
                    selected = isLivePreviewEnabled,
                    onClick = { isLivePreviewEnabled = !isLivePreviewEnabled },
                    label = { Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Red.copy(alpha = 0.7f),
                        selectedLabelColor = Color.White
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(AppR.string.segments_selected, selectedIndices.size), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val w = DeviceProfile.getMatrixWidth(selectedDevice)
                    if (w > 0) {
                        TextButton(onClick = {
                            val row = selectedIndices.firstOrNull()?.let { it / w } ?: 0
                            for (c in 0 until w) {
                                val idx = row * w + c
                                if (!selectedIndices.contains(idx)) selectedIndices.add(idx)
                            }
                        }) { Text("Row", fontSize = 12.sp) }
                        TextButton(onClick = {
                            val col = selectedIndices.firstOrNull()?.let { it % w } ?: 0
                            val h = DeviceProfile.getMatrixHeight(selectedDevice)
                            for (r in 0 until h) {
                                val idx = r * w + col
                                if (!selectedIndices.contains(idx)) selectedIndices.add(idx)
                            }
                        }) { Text("Col", fontSize = 12.sp) }
                    }

                    TextButton(onClick = ::selectAll) { Text("All", fontSize = 12.sp) }
                    TextButton(onClick = ::invertSelection) { Text("Inv", fontSize = 12.sp) }
                    FilterChip(
                        selected = isMultiSelect,
                        onClick = { isMultiSelect = !isMultiSelect },
                        label = { Text(stringResource(AppR.string.multi_select), fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary)
                    )
                    IconButton(onClick = { selectedIndices.clear() }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(AppR.string.clear), tint = Color.Gray)
                    }
                }
            }

            // Keep the grid selection for precision
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until ledCount) {
                    val isSelected = selectedIndices.contains(i)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            .clickable { 
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                if (isMultiSelect) {
                                    if (selectedIndices.contains(i)) selectedIndices.remove(i)
                                    else selectedIndices.add(i)
                                } else {
                                    selectedIndices.clear()
                                    selectedIndices.add(i)
                                }
                                selectedIndex = i 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (i + 1).toString(),
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            if (selectedIndices.size > 1) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(AppR.string.bulk_actions), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(AppR.string.bulk_actions_desc), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(
                                onClick = { distributeLogarithmically() },
                                label = { Text(stringResource(AppR.string.auto_distribute)) },
                                icon = { Icon(Icons.Default.ViewModule, null, Modifier.size(18.dp)) }
                            )
                            SuggestionChip(
                                onClick = { distributeIntensityProgress() },
                                label = { Text("Progress") },
                                icon = { Icon(Icons.Default.History, null, Modifier.size(18.dp)) }
                            )
                            SuggestionChip(
                                onClick = { mirrorHorizontally() },
                                label = { Text("Mirror") },
                                icon = { Icon(Icons.Default.Flip, null, Modifier.size(18.dp)) }
                            )
                            SuggestionChip(
                                onClick = { invertFrequencies() },
                                label = { Text("Invert") },
                                icon = { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)) }
                            )
                            SuggestionChip(
                                onClick = { shiftFrequencies(1f) },
                                label = { Text("+1 Octave") },
                                icon = { Icon(Icons.Default.VerticalAlignTop, null, Modifier.size(18.dp)) }
                            )
                            SuggestionChip(
                                onClick = { shiftFrequencies(-1f) },
                                label = { Text("-1 Octave") },
                                icon = { Icon(Icons.Default.VerticalAlignBottom, null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            if (selectedIndices.isNotEmpty()) {
                val firstIdx = selectedIndices.first()
                val zone = zones[firstIdx]
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = if (selectedIndices.size == 1) stringResource(AppR.string.editing_segment, firstIdx + 1) else stringResource(AppR.string.editing_segments_count, selectedIndices.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            SpectrumCanvas(
                                fftState = fftState,
                                lowHz = zone.lowHz,
                                highHz = zone.highHz,
                                lowPerc = if (zone.lowPercent.isNaN()) 0f else zone.lowPercent / 100f,
                                highPerc = if (zone.highPercent.isNaN()) 1f else zone.highPercent / 100f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Frequency Quick Presets
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Sub" to (20f..60f) to Color(0xFF9C27B0),
                                "Bass" to (60f..250f) to Color(0xFF2196F3),
                                "Mids" to (250f..2000f) to Color(0xFF4CAF50),
                                "Vocal" to (500f..3500f) to Color(0xFFFFEB3B),
                                "Highs" to (4000f..12000f) to Color(0xFFFF9800),
                                "Air" to (12000f..20000f) to Color(0xFFF44336)
                            ).forEach { (data, bandColor) ->
                                val (label, range) = data
                                InputChip(
                                    selected = false,
                                    onClick = { applyFrequencyPreset(range.start, range.endInclusive) },
                                    label = { Text(label, fontSize = 11.sp, color = if (label == "Vocal") Color.Black else Color.Unspecified) },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = bandColor.copy(alpha = 0.2f),
                                        labelColor = Color.LightGray
                                    ),
                                    border = InputChipDefaults.inputChipBorder(
                                        borderColor = bandColor.copy(alpha = 0.5f),
                                        enabled = true,
                                        selected = false
                                    )
                                )
                            }
                        }

                        Text(
                            text = stringResource(AppR.string.frequency_range_label, zone.lowHz.toInt(), zone.highHz.toInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        
                        val currentRange = invLerpLog(
                            zone.lowHz,
                            20f,
                            20000f
                        )..invLerpLog(zone.highHz, 20f, 20000f)

                        ExpressiveRangeSlider(
                            value = currentRange,
                            onValueChange = { newRange ->
                                pushUndo()
                                val newLow = lerpLog(newRange.start, 20f, 20000f)
                                val newHigh = lerpLog(newRange.endInclusive, 20f, 20000f)
                                selectedIndices.forEach { idx ->
                                    zones[idx] = AudioProcessor.ZoneSpec(
                                        newLow,
                                        newHigh,
                                        zones[idx].lowPercent,
                                        zones[idx].highPercent
                                    )
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("20Hz", fontSize = 12.sp, color = Color.Gray)
                            Text("20kHz", fontSize = 12.sp, color = Color.Gray)
                        }

                        HorizontalDivider(color = Color.DarkGray)

                        // Percent Slice (Threshold) Sliders
                        Text(
                            text = "Intensity Thresholds",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )

                        val lowPerc = if (zone.lowPercent.isNaN()) 0f else zone.lowPercent / 100f
                        val highPerc = if (zone.highPercent.isNaN()) 1f else zone.highPercent / 100f

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Full" to (0f..1f),
                                "Gate" to (0.15f..1f),
                                "Peak" to (0f..0.5f),
                                "Mid-Only" to (0.3f..0.7f)
                            ).forEach { (label, range) ->
                                AssistChip(
                                    onClick = {
                                        pushUndo()
                                        selectedIndices.forEach { idx ->
                                            zones[idx] = AudioProcessor.ZoneSpec(zones[idx].lowHz, zones[idx].highHz, range.start * 100f, range.endInclusive * 100f)
                                        }
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        ExpressiveRangeSlider(
                            value = lowPerc..highPerc,
                            onValueChange = { newRange ->
                                pushUndo()
                                selectedIndices.forEach { idx ->
                                    zones[idx] = AudioProcessor.ZoneSpec(
                                        zones[idx].lowHz,
                                        zones[idx].highHz,
                                        newRange.start * 100f,
                                        newRange.endInclusive * 100f
                                    )
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Min Intensity (Gate)", fontSize = 12.sp, color = Color.Gray)
                            Text("Max Intensity (Peak)", fontSize = 12.sp, color = Color.Gray)
                        }
                        
                        Text(
                            text = "LEDs only light up when audio volume is between ${ (lowPerc*100).toInt() }% and ${ (highPerc*100).toInt() }% of the peak.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpectrumCanvas(
    fftState: FloatArray,
    lowHz: Float,
    highHz: Float,
    lowPerc: Float,
    highPerc: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val binCount = fftState.size.coerceAtLeast(1)
        
        val minFreq = 20f
        val maxFreq = 20000f
        
        // Freq markers
        listOf(100f, 1000f, 10000f).forEach { marker ->
            val x = invLerpLog(marker, minFreq, maxFreq) * width
            drawLine(Color.DarkGray, Offset(x, 0f), Offset(x, height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        }

        // Intensity threshold lines
        val yLow = height * (1f - lowPerc)
        val yHigh = height * (1f - highPerc)
        drawLine(Color.Red.copy(0.3f), Offset(0f, yLow), Offset(width, yLow))
        drawLine(Color.Red.copy(0.3f), Offset(0f, yHigh), Offset(width, yHigh))

        for (i in 0 until binCount) {
            val hz = i * (44100f / 2048f)
            if (hz < minFreq) continue
            if (hz > maxFreq) break
            
            val mag = fftState[i]
            val x = invLerpLog(hz, minFreq, maxFreq) * width
            val barHeight = (mag * 20f).coerceIn(0f, 1f) * height
            
            val isInRange = hz in lowHz..highHz
            
            drawRect(
                color = if (isInRange) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                topLeft = Offset(x, height - barHeight),
                size = Size(2.dp.toPx(), barHeight)
            )
        }
        
        // Selection range overlay
        val xStart = invLerpLog(lowHz, minFreq, maxFreq) * width
        val xEnd = invLerpLog(highHz, minFreq, maxFreq) * width
        
        drawRect(
            color = Color.Red.copy(alpha = 0.1f),
            topLeft = Offset(xStart, 0f),
            size = Size(xEnd - xStart, height)
        )
    }
}

@Composable
fun EditableGlyphPreview(
    device: Int,
    selectedIndices: List<Int>,
    intensities: FloatArray,
    onIndexSelected: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val parser = remember { PathParser() }
    val paths = remember { getGlyphPaths(parser) }
    
    val androidPaths = remember(paths) {
        paths.mapValues { it.value.asAndroidPath() }
    }
    val regions = remember(androidPaths) {
        androidPaths.mapValues { entry ->
            val region = Region()
            val rectF = RectF()
            entry.value.computeBounds(rectF, true)
            region.setPath(entry.value, Region(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt()))
            region
        }
    }
    
    Canvas(modifier = modifier
        .pointerInput(device) {
            detectTapGestures { offset ->
                val viewBoxW = 182f
                val viewBoxH = if (device == DeviceProfile.DEVICE_NP1 || device == DeviceProfile.DEVICE_NP2 || device == DeviceProfile.DEVICE_NP4A || device == DeviceProfile.DEVICE_NP4B) 382f else 182f
                val scale = min(size.width / viewBoxW, size.height / viewBoxH)
                val dx = (size.width - viewBoxW * scale) / 2
                val dy = (size.height - viewBoxH * scale) / 2
                
                val localX = (offset.x - dx) / scale
                val localY = (offset.y - dy) / scale

                if (device == DeviceProfile.DEVICE_NP4APRO || device == DeviceProfile.DEVICE_NP3) {
                    val isPro = device == DeviceProfile.DEVICE_NP4APRO
                    val matrixW = if (isPro) 13 else 25
                    val matrixH = if (isPro) 13 else 25
                    val pixelSize = if (isPro) 8f else 4.5f
                    val pixelGap = if (isPro) 1.5f else 1f
                    val gridWidth = matrixW * pixelSize + (matrixW - 1) * pixelGap
                    val gridHeight = matrixH * pixelSize + (matrixH - 1) * pixelGap
                    val startX = (182f - gridWidth) / 2
                    val startY = (382f - gridHeight) / 2
                    
                    val col = ((localX - startX) / (pixelSize + pixelGap)).toInt()
                    val row = ((localY - startY) / (pixelSize + pixelGap)).toInt()
                    
                    if (col in 0 until matrixW && row in 0 until matrixH) {
                        onIndexSelected(row * matrixW + col, false)
                    }
                } else {
                    val hit = paths.entries.firstOrNull { entry ->
                        regions[entry.key]?.contains(localX.toInt(), localY.toInt()) == true
                    }

                    if (hit != null) {
                        val idx = when(device) {
                            DeviceProfile.DEVICE_NP1 -> when(hit.key) {
                                "p1_cam" -> 0
                                "p1_slash" -> 1
                                "p1_ring_bl" -> 2
                                "p1_ring_br" -> 3
                                "p1_ring_tr" -> 4
                                "p1_ring_tl" -> 5
                                "p1_dot" -> 6
                                "p1_battery" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 8)).toInt().coerceIn(0, 7)
                                    7 + row
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP2 -> when(hit.key) {
                                "p2_0" -> 0
                                "p2_1" -> 1
                                "p2_2" -> 2
                                "p2_ring" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 16)).toInt().coerceIn(0, 15)
                                    3 + row
                                }
                                "p2_19" -> 19
                                "p2_20" -> 20
                                "p2_21" -> 21
                                "p2_22" -> 22
                                "p2_23" -> 23
                                "p2_24" -> 24
                                "p2_battery" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 8)).toInt().coerceIn(0, 7)
                                    25 + row
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP2A -> when(hit.key) {
                                "p2a_large" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 24)).toInt().coerceIn(0, 23)
                                    row
                                }
                                "p2a_medium" -> 24
                                "p2a_small" -> 25
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP3A -> when(hit.key) {
                                "p3a_large" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 20)).toInt().coerceIn(0, 19)
                                    row
                                }
                                "p3a_medium" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 11)).toInt().coerceIn(0, 10)
                                    20 + row
                                }
                                "p3a_small" -> {
                                    val b = hit.value.getBounds()
                                    val col = ((localX - b.left) / (b.width / 5)).toInt().coerceIn(0, 4)
                                    31 + col
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP4A -> when(hit.key) {
                                "p4a_bar" -> {
                                    val b = hit.value.getBounds()
                                    val col = ((localX - b.left) / (b.width / 6)).toInt().coerceIn(0, 5)
                                    col
                                }
                                "p4a_dot" -> 6
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP4B -> when(hit.key) {
                                "p4b_bar" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 5)).toInt().coerceIn(0, 4)
                                    row
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP4B -> when(hit.key) {
                                "p4b_bar" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 5)).toInt().coerceIn(0, 4)
                                    row
                                }
                                else -> -1
                            }
                            else -> -1
                        }
                        if (idx != -1) onIndexSelected(idx, false)
                    }
                }
            }
        }
        .pointerInput(device) {
            detectDragGestures { change, _ ->
                change.consume()
                val viewBoxW = 182f
                val viewBoxH = if (device == DeviceProfile.DEVICE_NP1 || device == DeviceProfile.DEVICE_NP2 || device == DeviceProfile.DEVICE_NP4A || device == DeviceProfile.DEVICE_NP4B) 382f else 182f
                val scale = min(size.width / viewBoxW, size.height / viewBoxH)
                val dx = (size.width - viewBoxW * scale) / 2
                val dy = (size.height - viewBoxH * scale) / 2
                
                val localX = (change.position.x - dx) / scale
                val localY = (change.position.y - dy) / scale

                if (device == DeviceProfile.DEVICE_NP4APRO || device == DeviceProfile.DEVICE_NP3) {
                    val isPro = device == DeviceProfile.DEVICE_NP4APRO
                    val matrixW = if (isPro) 13 else 25
                    val matrixH = if (isPro) 13 else 25
                    val pixelSize = if (isPro) 8f else 4.5f
                    val pixelGap = if (isPro) 1.5f else 1f
                    val gridWidth = matrixW * pixelSize + (matrixW - 1) * pixelGap
                    val gridHeight = matrixH * pixelSize + (matrixH - 1) * pixelGap
                    val startX = (182f - gridWidth) / 2
                    val startY = (382f - gridHeight) / 2
                    
                    val col = ((localX - startX) / (pixelSize + pixelGap)).toInt()
                    val row = ((localY - startY) / (pixelSize + pixelGap)).toInt()
                    
                    if (col in 0 until matrixW && row in 0 until matrixH) {
                        onIndexSelected(row * matrixW + col, true)
                    }
                } else {
                    val hit = paths.entries.firstOrNull { entry ->
                        regions[entry.key]?.contains(localX.toInt(), localY.toInt()) == true
                    }
                    if (hit != null) {
                        val idx = when(device) {
                            DeviceProfile.DEVICE_NP1 -> when(hit.key) {
                                "p1_cam" -> 0
                                "p1_slash" -> 1
                                "p1_ring_bl" -> 2
                                "p1_ring_br" -> 3
                                "p1_ring_tr" -> 4
                                "p1_ring_tl" -> 5
                                "p1_dot" -> 6
                                "p1_battery" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 8)).toInt().coerceIn(0, 7)
                                    7 + row
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP2 -> when(hit.key) {
                                "p2_0" -> 0
                                "p2_1" -> 1
                                "p2_2" -> 2
                                "p2_ring" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 16)).toInt().coerceIn(0, 15)
                                    3 + row
                                }
                                "p2_19" -> 19
                                "p2_20" -> 20
                                "p2_21" -> 21
                                "p2_22" -> 22
                                "p2_23" -> 23
                                "p2_24" -> 24
                                "p2_battery" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 8)).toInt().coerceIn(0, 7)
                                    25 + row
                                }
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP2A -> when(hit.key) {
                                "p2a_large" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 24)).toInt().coerceIn(0, 23)
                                    row
                                }
                                "p2a_medium" -> 24
                                "p2a_small" -> 25
                                else -> -1
                            }
                            DeviceProfile.DEVICE_NP3A -> when(hit.key) {
                                "p3a_large" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 20)).toInt().coerceIn(0, 19)
                                    row
                                }
                                "p3a_medium" -> {
                                    val b = hit.value.getBounds()
                                    val row = ((localY - b.top) / (b.height / 11)).toInt().coerceIn(0, 10)
                                    20 + row
                                }
                                "p3a_small" -> {
                                    val b = hit.value.getBounds()
                                    val col = ((localX - b.left) / (b.width / 5)).toInt().coerceIn(0, 4)
                                    31 + col
                                }
                                else -> -1
                            }
                            else -> -1
                        }
                        if (idx != -1) onIndexSelected(idx, true)
                    }
                }
            }
        }
    ) {
        val viewBoxW = 182f
        val viewBoxH = if (device == DeviceProfile.DEVICE_NP1 || device == DeviceProfile.DEVICE_NP2 || device == DeviceProfile.DEVICE_NP4A || device == DeviceProfile.DEVICE_NP4B) 382f else 182f
        val scale = min(size.width / viewBoxW, size.height / viewBoxH)
        val dx = (size.width - viewBoxW * scale) / 2
        val dy = (size.height - viewBoxH * scale) / 2

        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawEditorGlyphs(this, device, selectedIndices, intensities, paths)
        }
    }
}

private fun drawEditorGlyphs(scope: DrawScope, device: Int, selectedIndices: List<Int>, intensities: FloatArray, paths: Map<String, Path>) {
    val selectedColor = Color.Red
    val normalColor = Color.White
    val baseAlpha = 0.2f

    fun getColor(idx: Int): Color {
        val isSelected = selectedIndices.contains(idx)
        val intensity = if (idx < intensities.size) intensities[idx] else 0f
        
        return if (intensity > 0.01f) {
            // Visualizing mode: Blend between base color and bright white based on intensity
            // But if selected, keep a red tint
            if (isSelected) lerp(selectedColor, Color.White, intensity)
            else lerp(normalColor.copy(alpha = baseAlpha), Color.White, intensity)
        } else {
            if (isSelected) selectedColor else normalColor
        }
    }
    
    fun getAlpha(idx: Int): Float {
        val isSelected = selectedIndices.contains(idx)
        val intensity = if (idx < intensities.size) intensities[idx] else 0f
        return if (intensity > 0.01f) {
            (baseAlpha + (1f - baseAlpha) * intensity).coerceIn(0f, 1f)
        } else {
            if (isSelected) 1.0f else baseAlpha
        }
    }

    when (device) {
        DeviceProfile.DEVICE_NP1 -> {
            // Camera Plate
            paths["p12_cam_plate"]?.let {
                scope.drawPath(it, Color.White.copy(alpha = 0.05f))
                scope.drawPath(it, Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
            }

            paths["p1_cam"]?.let { scope.drawPath(it, getColor(0), alpha = getAlpha(0)) }
            paths["p1_slash"]?.let { scope.drawPath(it, getColor(1), alpha = getAlpha(1)) }
            paths["p1_ring_bl"]?.let { scope.drawPath(it, getColor(2), alpha = getAlpha(2)) }
            paths["p1_ring_br"]?.let { scope.drawPath(it, getColor(3), alpha = getAlpha(3)) }
            paths["p1_ring_tr"]?.let { scope.drawPath(it, getColor(4), alpha = getAlpha(4)) }
            paths["p1_ring_tl"]?.let { scope.drawPath(it, getColor(5), alpha = getAlpha(5)) }
            paths["p1_dot"]?.let { scope.drawPath(it, getColor(6), alpha = getAlpha(6)) }
            paths["p1_battery"]?.let { drawPathSegmentedVertical(scope, it, (7..14).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }
        }
        DeviceProfile.DEVICE_NP2 -> {
            scope.withTransform({
                translate(3f, -6f)
            }) {
                // Camera Plate
                paths["p2_cam_plate"]?.let {
                    drawPath(it, Color.White.copy(alpha = 0.05f))
                    drawPath(it, Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
                }
            }

            // NP2 Progress glyph (3-18) is a single sequence of 16 segments in its arc
            paths["p2_ring"]?.let { drawPathSegmentedVertical(scope, it, (3..18).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }

            for (i in 19..24) {
                paths["p2_$i"]?.let { scope.drawPath(it, getColor(i), alpha = getAlpha(i)) }
            }
            paths["p2_battery"]?.let { drawPathSegmentedVertical(scope, it, (25..32).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }
        }
        DeviceProfile.DEVICE_NP2A -> {
            scope.withTransform({
                translate(-13.02971f, -40f)
                scale(1.128745f, 1.128745f, pivot = Offset.Zero)
            }) {
                paths["p2a_large"]?.let { drawPathSegmentedVertical(this, it, (0..23).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }
                paths["p2a_medium"]?.let { drawPath(it, getColor(24), alpha = getAlpha(24)) }
                paths["p2a_small"]?.let { drawPath(it, getColor(25), alpha = getAlpha(25)) }
            }
        }
        DeviceProfile.DEVICE_NP3A -> {
            scope.withTransform({
                translate(-2f, 7f)
                scale(1.03f, 1.03f, pivot = Offset.Zero)
            }) {
                // Camera Plate
                paths["p3a_cam_plate"]?.let {
                    drawPath(it, Color.White.copy(alpha = 0.06f))
                }

                paths["p3a_large"]?.let { drawPathSegmentedVertical(this, it, (0..19).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }
                paths["p3a_medium"]?.let { drawPathSegmentedVertical(this, it, (20..30).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha) }
                paths["p3a_small"]?.let { drawPathSegmentedVertical(this, it, (31..35).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha, vertical = false) }
            }
        }
        DeviceProfile.DEVICE_NP4A -> {
            paths["p4a_bar"]?.let { drawPathSegmentedVertical(scope, it, (0..5).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha, vertical = false) }
            paths["p4a_dot"]?.let { scope.drawPath(it, getColor(6), alpha = getAlpha(6)) }
        }
        DeviceProfile.DEVICE_NP4B -> {
            paths["p4b_island"]?.let {
                scope.drawPath(it, Color.White.copy(alpha = 0.05f))
                scope.drawPath(it, Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
            }
            paths["p4b_bar"]?.let { drawPathSegmentedVertical(scope, it, (0..4).toList(), selectedIndices, intensities, selectedColor, normalColor, baseAlpha, vertical = true, specialBaseColors = mapOf(4 to Color.Red)) }
        }
        DeviceProfile.DEVICE_NP4APRO, DeviceProfile.DEVICE_NP3 -> {
            val isPro = device == DeviceProfile.DEVICE_NP4APRO

            if (isPro) {
                paths["p4ap_island"]?.let {
                    scope.drawPath(it, Color.White.copy(alpha = 0.05f))
                    scope.drawPath(it, Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
                }
            }

            val matrixW = if (isPro) 13 else 25
            val matrixH = if (isPro) 13 else 25
            val pixelSize = if (isPro) 8f else 4.5f
            val pixelGap = if (isPro) 1.5f else 1f

            val gridWidth = matrixW * pixelSize + (matrixW - 1) * pixelGap
            val gridHeight = matrixH * pixelSize + (matrixH - 1) * pixelGap

            // Adjust these to move the matrix
            val matrixCenterX = if (isPro) 135f else 91f
            val matrixCenterY = if (isPro) 47f else 191f

            val startX = matrixCenterX - gridWidth / 2f
            val startY = matrixCenterY - gridHeight / 2f

            for (idx in 0 until (matrixW * matrixH)) {
                val row = idx / matrixW
                val col = idx % matrixW
                val px = startX + col * (pixelSize + pixelGap)
                val py = startY + row * (pixelSize + pixelGap)
                
                scope.drawRect(
                    color = getColor(idx),
                    topLeft = Offset(px, py),
                    size = Size(pixelSize, pixelSize),
                    alpha = getAlpha(idx)
                )
            }
        }
    }
}

private fun drawPathSegmentedVertical(
    scope: DrawScope,
    path: Path,
    indices: List<Int>,
    selectedIndices: List<Int>,
    intensities: FloatArray,
    selectedColor: Color,
    normalColor: Color,
    baseAlpha: Float,
    vertical: Boolean = true,
    specialBaseColors: Map<Int, Color> = emptyMap()
) {
    val b = path.getBounds()
    val count = indices.size
    if (count <= 0) return
    val step = if (vertical) b.height / count else b.width / count

    indices.forEachIndexed { i, idx ->
        val isSelected = selectedIndices.contains(idx)
        val intensity = if (idx < intensities.size) intensities[idx] else 0f
        val baseColor = specialBaseColors[idx] ?: normalColor
        
        val color = if (intensity > 0.01f) {
            if (isSelected) lerp(selectedColor, Color.White, intensity)
            else lerp(baseColor.copy(alpha = baseAlpha), Color.White, intensity)
        } else {
            if (isSelected) selectedColor else baseColor
        }
        
        val alpha = if (intensity > 0.01f) {
            (baseAlpha + (1f - baseAlpha) * intensity).coerceIn(0f, 1f)
        } else {
            if (isSelected) 1.0f else baseAlpha
        }

        scope.clipRect(
            left = if (vertical) b.left else b.left + i * step,
            top = if (vertical) b.top + i * step else b.top,
            right = if (vertical) b.right else b.left + (i + 1) * step,
            bottom = if (vertical) b.top + (i + 1) * step else b.bottom
        ) {
            scope.drawPath(path, color, alpha = alpha)
        }
    }
}

private fun drawPathRingSegments(
    scope: DrawScope,
    path: Path,
    indices: List<Int>,
    selectedIndex: Int,
    selectedColor: Color,
    normalColor: Color,
    baseAlpha: Float
) {
    val b = path.getBounds()
    val count = indices.size
    val centerX = b.left + b.width / 2
    val rows = count / 2
    val sliceH = b.height / rows

    indices.forEachIndexed { i, idx ->
        val isSelected = idx == selectedIndex
        val color = if (isSelected) selectedColor else normalColor
        val alpha = if (isSelected) 1.0f else baseAlpha

        val isR = i >= rows
        val row = if (isR) i - rows else i

        scope.clipRect(
            left = if (isR) centerX else b.left,
            top = b.top + row * sliceH,
            right = if (isR) b.right else centerX,
            bottom = b.top + (row + 1) * sliceH
        ) {
            scope.drawPath(path, color, alpha = alpha)
        }
    }
}

private fun getGlyphPaths(parser: PathParser): Map<String, Path> {
    return mutableMapOf<String, Path>().apply {
        // --- Phone (1) ---
        put("p1_cam", parser.parsePathString("M9.704,68.077L9.704,32.177C9.704,20.184 19.241,10.363 31.233,10.01C43.226,9.657 53.314,18.909 54.021,30.885C54.038,31.221 53.917,31.548 53.693,31.798C53.461,32.039 53.142,32.177 52.806,32.177L49.136,32.177C48.498,32.177 47.964,31.677 47.921,31.04C47.335,22.691 40.383,16.109 31.888,16.109C23.014,16.109 15.82,23.303 15.82,32.177L15.82,68.077C15.82,76.951 23.014,84.145 31.888,84.145C40.762,84.145 47.955,76.951 47.955,68.077L47.955,57.696C47.955,57.024 48.498,56.472 49.179,56.472L52.84,56.472C53.512,56.472 54.064,57.015 54.064,57.696L54.064,68.077C54.064,76.003 49.834,83.318 42.976,87.281C36.118,91.244 27.658,91.244 20.8,87.281C13.934,83.318 9.704,75.994 9.704,68.077Z").toPath())
        put("p1_slash", parser.parsePathString("M120.51,63.373C119.812,64.208 119.605,65.354 119.976,66.379C120.346,67.405 121.242,68.154 122.31,68.344C123.379,68.533 124.481,68.137 125.179,67.301L158.891,27.128C159.976,25.836 159.804,23.914 158.512,22.829C157.22,21.743 155.298,21.916 154.213,23.208L120.51,63.373Z").toPath())
        put("p1_ring_bl", parser.parsePathString("M123.153,287.927C112.9,291.329 102.057,293.12 91,293.12C60.17,293.12 31.01,279.2 11.63,255.23C9.6,252.73 8.5,249.61 8.5,246.4L8.5,231.182L14.61,231.182L14.61,246.4C14.61,248.21 15.23,249.97 16.37,251.39C34.6,273.91 62.02,287.01 91,287.01C101.316,287.01 111.435,285.35 121.011,282.196L123.153,287.927Z").toPath())
        put("p1_ring_br", parser.parsePathString("M121.011,282.196C138.337,276.49 153.889,265.893 165.63,251.39C166.77,249.97 167.39,248.21 167.39,246.4L167.39,194.58C167.39,192.9 168.75,191.53 170.44,191.53C172.12,191.53 173.49,192.89 173.49,194.58L173.5,194.58L173.5,246.4C173.5,249.61 172.4,252.73 170.37,255.23C157.941,270.603 141.489,281.842 123.153,287.927L121.011,282.196Z").toPath())
        put("p1_ring_tr", parser.parsePathString("M27.716,118.88L23.393,114.558C41.74,98.337 65.477,89.123 90.35,88.96C120.93,88.76 150,102.3 169.54,125.83C170.24,126.67 170.43,127.82 170.05,128.84C169.68,129.86 168.78,130.61 167.7,130.79C166.63,130.97 165.54,130.56 164.84,129.73C146.47,107.6 119.14,94.88 90.38,95.06C67.131,95.214 44.927,103.784 27.716,118.88Z").toPath())
        put("p1_ring_tl", parser.parsePathString("M8.5,231.182L8.5,135.68C8.5,132.47 9.6,129.35 11.63,126.85C15.231,122.398 19.169,118.292 23.393,114.558L27.716,118.88C23.636,122.459 19.836,126.404 16.37,130.69C15.23,132.1 14.61,133.86 14.61,135.67L14.61,231.182L8.5,231.182Z").toPath())
        put("p1_dot", parser.parsePathString("M90.991,371C92.68,371 94.041,369.63 94.041,367.95L94.041,366.115C94.041,364.427 92.671,363.065 90.991,363.065C89.311,363.065 87.941,364.435 87.941,366.115L87.941,367.95C87.941,369.63 89.311,371 90.991,371Z").toPath())
        put("p1_battery", parser.parsePathString("M90.991,356.73C92.68,356.73 94.041,355.36 94.041,353.68L94.041,311.801C94.041,310.112 92.671,308.751 90.991,308.751C89.311,308.751 87.941,310.121 87.941,311.801L87.941,353.68C87.941,355.368 89.311,356.73 90.991,356.73Z").toPath())
        
        // --- Phone (1) & (2) Camera Plate ---
        val p12CamRadius = 28f
        val p12CamX = 6f
        val p12CamY = 7f
        put("p12_cam_plate", Path().apply {
            addRoundRect(
                RoundRect(
                    left = p12CamX,
                    top = p12CamY,
                    right = p12CamX + 56f,
                    bottom = p12CamY + 86f,
                    cornerRadius = CornerRadius(p12CamRadius)
                )
            )
        })

        // --- Phone (2) Camera Plate (Taller) ---
        put("p2_cam_plate", Path().apply {
            addRoundRect(
                RoundRect(
                    left = p12CamX,
                    top = p12CamY,
                    right = p12CamX + 56f,
                    bottom = p12CamY + 90f,
                    cornerRadius = CornerRadius(p12CamRadius)
                )
            )
        })

        // --- Phone (2) ---
        put("p2_0", parser.parsePathString("M17.883,51.449l-0,-25.117c-0,-9.107 7.233,-16.58 16.353,-16.892c9.119,-0.311 16.836,6.662 17.46,15.751c0.042,0.64 0.578,1.141 1.219,1.141l3.686,0c0.337,0 0.657,-0.139 0.891,-0.381c0.234,-0.241 0.354,-0.569 0.337,-0.906c-0.71,-12.451 -11.195,-22.077 -23.671,-21.73c-12.477,0.354 -22.41,10.549 -22.41,23.017l0,25.117c0,0.674 0.546,1.226 1.229,1.226l3.677,0c0.675,0 1.229,-0.544 1.229,-1.226Z").toPath())
        put("p2_1", parser.parsePathString("M51.975,48.161c-0,-0.674 0.544,-1.226 1.228,-1.226l3.677,-0c0.675,-0 1.229,0.544 1.229,1.226l0,17.817c0,8.657 -4.863,16.589 -12.589,20.511c-7.726,3.931 -17.01,3.197 -24.018,-1.901c-0.277,-0.198 -0.449,-0.501 -0.493,-0.829c-0.043,-0.329 0.052,-0.674 0.268,-0.933l2.336,-2.851c0.407,-0.502 1.134,-0.597 1.661,-0.225c5.166,3.663 11.94,4.139 17.564,1.235c5.624,-2.903 9.154,-8.692 9.154,-15.016l-0,-17.816l-0.017,0.008Z").toPath())
        put("p2_2", parser.parsePathString("M154.368,14.853c1.09,-1.297 3.02,-1.461 4.318,-0.381c1.297,1.08 1.462,3.015 0.38,4.312l-33.362,39.701c-0.519,0.623 -1.271,1.011 -2.085,1.08c-0.814,0.069 -1.618,-0.181 -2.241,-0.708l-1.41,-1.184c-0.251,-0.207 -0.407,-0.51 -0.433,-0.83c-0.026,-0.319 0.069,-0.647 0.286,-0.889l34.539,-41.11l0.008,0.009Z").toPath())
        put("p2_ring", parser.parsePathString("M74.634,89.533c35.857,-5.279 71.801,8.96 94.341,37.376c1.054,1.322 0.829,3.249 -0.491,4.303c-1.32,1.055 -3.245,0.83 -4.298,-0.492c-21.177,-26.707 -54.964,-40.09 -88.654,-35.13c-1.079,0.155 -2.166,-0.268 -2.84,-1.132c-0.673,-0.864 -0.845,-2.013 -0.448,-3.032c0.406,-1.02 1.321,-1.746 2.398,-1.901l-0.008,0.008Z").toPath())
        put("p2_19", parser.parsePathString("M49.732,97.623c0.995,-0.458 2.163,-0.345 3.054,0.293c0.891,0.631 1.375,1.695 1.272,2.783c-0.104,1.089 -0.78,2.039 -1.783,2.497c-13.756,6.264 -25.835,15.699 -35.231,27.527c-0.683,0.855 -1.764,1.288 -2.855,1.124c-1.081,-0.164 -1.998,-0.89 -2.405,-1.901c-0.407,-1.019 -0.234,-2.169 0.45,-3.024c10.001,-12.589 22.85,-22.62 37.489,-29.29l0.009,-0.009Z").toPath())
        put("p2_20", parser.parsePathString("M14.625,188.142c-0,1.694 -1.376,3.059 -3.063,3.059c-1.687,-0 -3.063,-1.375 -3.063,-3.059l0,-41.542c0,-1.097 0.588,-2.108 1.532,-2.652c0.951,-0.544 2.119,-0.544 3.062,0c0.953,0.544 1.532,1.555 1.532,2.652l-0,41.542Z").toPath())
        put("p2_21", parser.parsePathString("M17.044,250.861c21.232,26.707 55.105,40.09 88.883,35.13c1.081,-0.155 2.172,0.269 2.846,1.132c0.684,0.855 0.848,2.013 0.45,3.033c-0.407,1.019 -1.324,1.745 -2.406,1.901c-35.949,5.278 -71.984,-8.96 -94.583,-37.377c-0.684,-0.856 -0.857,-2.013 -0.45,-3.024c0.407,-1.02 1.315,-1.746 2.405,-1.901c1.082,-0.164 2.172,0.267 2.855,1.123l-0,-0.017Z").toPath())
        put("p2_22", parser.parsePathString("M170.123,251.638c0.407,1.02 0.233,2.169 -0.451,3.025c-10.001,12.588 -22.849,22.619 -37.488,29.289c-0.995,0.459 -2.163,0.346 -3.055,-0.293c-0.891,-0.64 -1.375,-1.694 -1.271,-2.783c0.103,-1.088 0.778,-2.038 1.782,-2.497c13.757,-6.264 25.834,-15.699 35.231,-27.527c0.683,-0.855 1.765,-1.288 2.855,-1.124c1.082,0.165 1.999,0.891 2.405,1.901l-0.008,0.009Z").toPath())
        put("p2_23", parser.parsePathString("M169.303,190.397c-1.695,-0 -3.063,1.373 -3.063,3.058l0,31.545c0,1.694 1.376,3.059 3.063,3.059c1.687,-0 3.063,-1.374 3.063,-3.059l-0,-31.545c-0,-1.693 -1.376,-3.058 -3.063,-3.058Z").toPath())
        put("p2_24", parser.parsePathString("M90.191,364.357c-1.691,-0 -3.055,1.373 -3.055,3.058l-0,3.231c-0,1.694 1.372,3.059 3.055,3.059c1.682,-0 3.055,-1.374 3.055,-3.059l-0,-3.231c-0,-1.693 -1.373,-3.058 -3.055,-3.058Z").toPath())
        put("p2_battery", parser.parsePathString("M87.136,315.644l-0,39.873c-0,1.693 1.372,3.059 3.055,3.059c1.682,-0 3.055,-1.375 3.055,-3.059l-0,-39.873c-0,-1.097 -0.587,-2.108 -1.527,-2.653c-0.95,-0.544 -2.115,-0.544 -3.055,0c-0.949,0.545 -1.528,1.556 -1.528,2.653Z").toPath())
        
        // --- Phone (2a) ---
        put("p2a_large", parser.parsePathString("M63.057,55.311c0.524,1.363 -0.156,2.894 -1.52,3.419c-4.942,1.901 -12.013,7.268 -18.387,14.372c-6.355,7.083 -11.63,15.468 -13.376,23.102c-0.326,1.424 -1.744,2.315 -3.169,1.989c-1.424,-0.325 -2.314,-1.744 -1.989,-3.169c2.028,-8.869 7.948,-18.045 14.596,-25.455c6.628,-7.39 14.367,-13.448 20.426,-15.778c1.363,-0.525 2.894,0.156 3.419,1.52Z").toPath())
        put("p2a_medium", parser.parsePathString("M159.648,87.219c1.482,-0 2.68,1.198 2.68,2.68l0,47.64c0,1.483 -1.198,2.681 -2.68,2.681c-1.478,-0 -2.676,-1.198 -2.676,-2.681l0,-47.64c0,-1.482 1.198,-2.68 2.676,-2.68Z").toPath())
        put("p2a_small", parser.parsePathString("M30.754,144.063c1.363,-0.573 2.932,0.066 3.506,1.428c2.167,5.144 7.304,12.329 11.354,15.749c1.129,0.953 1.272,2.642 0.318,3.772c-0.954,1.13 -2.643,1.272 -3.773,0.318c-4.752,-4.013 -10.37,-11.912 -12.833,-17.76c-0.574,-1.363 0.065,-2.933 1.428,-3.507Z").toPath())

        // --- Phone (3a) ---
        put("p3a_large", parser.parsePathString("M162.87,60.41C164.27,60.36 165.56,61.288 165.91,62.693C166.18,63.799 166.42,64.91 166.64,66.024L166.64,66.025C167.04,68.036 167.37,70.056 167.6,72.08L167.6,72.081C167.83,74.113 168.02,76.149 168.08,78.185C168.17,79.963 168.17,81.743 168.13,83.518C168.07,85.554 167.94,87.585 167.72,89.608L167.74,89.609C167.5,91.909 167.15,94.196 166.7,96.466L166.7,96.465C166.31,98.468 165.85,100.458 165.29,102.428L165.29,102.427C164.82,104.134 164.3,105.826 163.71,107.501C163.03,109.425 162.27,111.325 161.45,113.198L161.45,113.202C160.63,115.062 159.73,116.895 158.76,118.697L158.76,118.698C158.21,119.704 157.66,120.702 157.08,121.687C156.22,123.11 154.38,123.574 152.96,122.726C151.54,121.881 151.07,120.033 151.92,118.61C152.32,117.914 152.74,117.21 153.12,116.501C154.03,114.839 154.88,113.15 155.67,111.434L155.67,111.433C156.46,109.712 157.17,107.966 157.81,106.198L157.79,106.197C158.52,104.189 159.16,102.152 159.71,100.096C160.2,98.276 160.62,96.438 160.95,94.588L160.95,94.589C161.25,92.978 161.49,91.357 161.68,89.728L161.68,89.727C161.89,87.86 162.04,85.983 162.12,84.102L162.11,84.102C162.18,81.972 162.18,79.837 162.08,77.702C161.97,75.82 161.8,73.939 161.56,72.062C161.35,70.437 161.09,68.815 160.77,67.198L160.75,67.198C160.72,67.058 160.69,66.919 160.66,66.78C160.66,66.674 160.63,66.568 160.61,66.462C160.44,65.679 160.27,64.896 160.07,64.115C159.67,62.51 160.66,60.882 162.28,60.493C162.47,60.444 162.67,60.418 162.87,60.41Z").toPath())
        put("p3a_medium", parser.parsePathString("M23.37,58.781C23.34,58.831 23.32,58.882 23.31,58.932C23.3,58.952 23.29,58.973 23.29,58.993L23.29,59C23.28,59.027 23.27,59.055 23.26,59.082C22.71,60.606 21.02,61.416 19.49,60.9C17.93,60.375 17.07,58.671 17.6,57.102L17.6,57.061C17.94,56.041 18.31,55.035 18.69,54.034L18.7,54.035C19.06,53.083 19.44,52.139 19.84,51.203C20.17,50.415 20.52,49.634 20.87,48.859L20.87,48.858C21.31,47.931 21.74,47.015 22.22,46.107C22.77,45.031 23.34,43.967 23.94,42.919L23.94,42.92C24.45,42.033 24.98,41.155 25.52,40.289L25.52,40.29C25.97,39.563 26.45,38.845 26.93,38.135L26.93,38.134C27.49,37.284 28.08,36.446 28.68,35.619L28.68,35.618C29.31,34.755 29.96,33.908 30.61,33.072C31.33,32.17 32.07,31.285 32.82,30.416L32.82,30.418C33.38,29.778 33.94,29.146 34.52,28.521C34.54,28.497 34.57,28.472 34.59,28.448C34.68,28.349 34.77,28.249 34.86,28.149C35.45,27.525 36.06,26.909 36.66,26.303L36.66,26.302C37.38,25.581 38.11,24.874 38.87,24.179L38.87,24.177C39.73,23.375 40.62,22.593 41.52,21.83L41.53,21.831C42.31,21.172 43.1,20.529 43.91,19.901C44.6,19.373 45.27,18.856 45.97,18.349C46.8,17.744 47.65,17.154 48.49,16.581L48.49,16.578C49.42,15.965 50.34,15.368 51.29,14.792C51.83,14.468 52.41,14.328 52.99,14.355C53.95,14.4 54.88,14.908 55.41,15.793C56.28,17.209 55.84,19.056 54.42,19.922C54.36,19.963 54.28,20.007 54.21,20.047L54.21,20.045C53.54,20.456 52.89,20.878 52.23,21.312L52.23,21.314C51.43,21.839 50.65,22.38 49.87,22.933L49.87,22.934C48.98,23.578 48.1,24.242 47.23,24.926L47.23,24.925C46.49,25.514 45.75,26.117 45.03,26.732L45.03,26.73C44.44,27.235 43.87,27.748 43.29,28.271L43.29,28.273C42.59,28.911 41.9,29.562 41.23,30.227C40.55,30.892 39.89,31.568 39.25,32.258C38.5,33.059 37.77,33.878 37.04,34.712L37.04,34.709C36.53,35.307 36.03,35.913 35.55,36.528C34.95,37.266 34.36,38.015 33.8,38.778L33.81,38.78C33.25,39.54 32.7,40.311 32.16,41.093C31.55,42.002 30.95,42.926 30.36,43.862C29.88,44.666 29.39,45.479 28.93,46.302L28.93,46.3C28.53,47 28.15,47.708 27.78,48.423C27.34,49.26 26.93,50.105 26.52,50.959L26.52,50.96C26.11,51.816 25.73,52.681 25.36,53.553C24.92,54.562 24.51,55.582 24.13,56.613C23.87,57.33 23.61,58.053 23.36,58.78L23.37,58.781Z").toPath())
        put("p3a_small", parser.parsePathString("M41.5,134.113C42.51,135.434 42.25,137.313 40.92,138.312C39.59,139.311 37.72,139.049 36.72,137.719L35.13,135.628L35.13,135.627L34.61,134.911L31.49,130.778L27.29,125.218L24.19,121.091L22.14,118.364C21.13,117.043 21.39,115.163 22.73,114.164C23.3,113.727 23.99,113.532 24.66,113.562C25.53,113.599 26.36,114.009 26.93,114.757L28.92,117.404L32.08,121.606L32.38,121.991L32.62,122.324L35.74,126.452L35.8,126.531L36.28,127.169L39.86,131.912L41.5,134.113Z").toPath())

        put("p4a_bar", parser.parsePathString("M40.5,300.5L142.5,300.5").toPath())
        put("p4a_dot", parser.parsePathString("M91,330.5A5,5 0 1,1 90.99,330.5Z").toPath())

        // --- Phone (4b) ---
        put("p4b_island", Path().apply {
            addRoundRect(
                RoundRect(
                    left = 10f,
                    top = 10f,
                    right = 172f,
                    bottom = 160f,
                    cornerRadius = CornerRadius(24f)
                )
            )
        })
        put("p4b_bar", parser.parsePathString("M144,50h14v100h-14z").toPath())


        // --- Phone (4a) Pro Camera Bump ---
        val p4apCamRadius = 28f
        put("p4ap_island", Path().apply {
            addRoundRect(
                RoundRect(
                    left = 5.5f,
                    top = 5f,
                    right = 176.5f,
                    bottom = 135f,
                    cornerRadius = CornerRadius(p4apCamRadius)
                )
            )
        })

        // --- Phone (3a) Camera Plate ---
        val p3aCamRadius = 18f
        put("p3a_cam_plate", Path().apply {
            addRoundRect(
                RoundRect(
                    left = 78f,
                    top = 57f,
                    right = 122f,
                    bottom = 93f,
                    cornerRadius = CornerRadius(p3aCamRadius)
                )
            )
        })
    }
}


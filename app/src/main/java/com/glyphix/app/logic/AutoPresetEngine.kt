package com.glyphix.app.logic

import android.os.SystemClock
import android.util.Log
import com.glyphix.app.service.AudioCaptureService.PresetInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Intelligent Rule-Based Auto Preset Selector.
 * 
 * Rules Matrix based on compensated acoustic energy levels:
 * - Soft / Romantic / Sad: Mids HIGH, Highs AVERAGE/LOW, Bass LOW -> Smooth / Vocal Presets (e.g. np1s, np2-simple, np3a-vocal)
 * - Techno / EDM / Hardcore: Bass HIGH, Mids LOW, Highs AVERAGE/HIGH -> Bass Flash / Gauge Presets (e.g. np1-bass-flash, np2-bass, np3a-full-bass)
 * - Pop / Jazz / Energetic (e.g. Dai Dai): Bass HIGH/AVG, Highs HIGH, Mids AVG -> Full Spectrum / Zebra Presets (e.g. np1-spectrum, np2, np3a, np1-zebra)
 * - Acoustic / Vocal Solo: Mids HIGH, Bass LOW -> Vocal / Smooth Presets
 * - Rock / Metal: Bass HIGH, Mids HIGH, Highs HIGH -> Dynamic Spectrum / Zebra Presets
 */
class AutoPresetEngine private constructor() {

    enum class BandLevel(val label: String) {
        LOW("Low"),
        AVERAGE("Avg"),
        HIGH("High")
    }

    enum class SwitchSensitivity(val label: String, val thresholdFrames: Int, val dwellMs: Long) {
        PER_TRACK("Per-Track", 200, 20000L),
        BALANCED("Balanced", 60, 3500L),
        DYNAMIC("Dynamic", 25, 1500L)
    }

    data class State(
        val isEnabled: Boolean = false,
        val bpm: Int = 0,
        val bpmConfidence: Float = 0f,
        val isBeatPulse: Boolean = false,
        val vibe: String = "Idle",
        val activePresetKey: String = "",
        val candidatePresetKey: String = "",
        val rationale: String = "Waiting for audio...",
        val bassLevel: BandLevel = BandLevel.AVERAGE,
        val midLevel: BandLevel = BandLevel.AVERAGE,
        val highLevel: BandLevel = BandLevel.AVERAGE,
        val bassRatio: Float = 0f,
        val midRatio: Float = 0f,
        val trebleRatio: Float = 0f,
        val energy: Float = 0f,
        val sensitivity: SwitchSensitivity = SwitchSensitivity.BALANCED
    )

    fun interface OnPresetAutoSelectedListener {
        fun onPresetAutoSelected(presetKey: String)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<OnPresetAutoSelectedListener>()

    // Rolling history of onset novelty (240 frames ~ 4.0s @ 60 FPS)
    private val historySize = 240
    private val fluxHistory = FloatArray(historySize)
    private var historyIndex = 0
    private var historyCount = 0

    // Multi-subband spectral flux memory
    private val subbandSplits = intArrayOf(30, 120, 180, 310, 390, 450, 490, 512)
    private val prevSubbandEnergies = FloatArray(8)

    // Beat pulse tracking
    private var lastBeatPulseMs = 0L

    // BPM smoothing & confidence
    private var smoothedBpm = 0f
    private var lastBpmConfidence = 0f

    // Frame counter
    private var frameCounter = 0

    // Metadata
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentAlbum: String? = null
    private var currentGenre: String? = null
    private var lastMetadataChangeMs = 0L

    // Presets catalog
    private var availablePresets: List<PresetInfo> = emptyList()

    // Switching & Hysteresis
    private var activePresetKey: String = ""
    private var candidatePresetKey: String = ""
    private var candidateStreakFrames = 0
    private var lastSwitchTimeMs = 0L
    private var lastAudioActivityMs = 0L
    private var switchSensitivity = SwitchSensitivity.BALANCED

    // Force analysis flag
    private var forceAnalyzeFlag = false

    companion object {
        private const val TAG = "AutoPresetEngine"
        @Volatile
        private var INSTANCE: AutoPresetEngine? = null

        @JvmStatic
        fun getInstance(): AutoPresetEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoPresetEngine().also { INSTANCE = it }
            }
        }
    }

    fun addOnPresetAutoSelectedListener(listener: OnPresetAutoSelectedListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeOnPresetAutoSelectedListener(listener: OnPresetAutoSelectedListener) {
        listeners.remove(listener)
    }

    fun setOnPresetAutoSelectedListener(listener: OnPresetAutoSelectedListener?) {
        if (listener != null) {
            addOnPresetAutoSelectedListener(listener)
        }
    }

    private fun notifyPresetSelected(presetKey: String) {
        if (presetKey.isEmpty()) return
        Log.d(TAG, "Notifying ${listeners.size} listeners of preset switch: $presetKey")
        for (listener in listeners) {
            try {
                listener.onPresetAutoSelected(presetKey)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPresetAutoSelected listener", e)
            }
        }
    }

    fun setSensitivity(sensitivity: SwitchSensitivity) {
        this.switchSensitivity = sensitivity
        _state.value = _state.value.copy(sensitivity = sensitivity)
    }

    fun setEnabled(enabled: Boolean) {
        val current = _state.value
        if (current.isEnabled != enabled) {
            _state.value = current.copy(
                isEnabled = enabled,
                rationale = if (enabled) "Analyzing audio stream in real-time..." else "Manual mode"
            )
            if (enabled) {
                candidateStreakFrames = 0
                frameCounter = 0
                forceAnalyzeFlag = true
                if (activePresetKey.isNotEmpty()) {
                    notifyPresetSelected(activePresetKey)
                }
            }
        }
    }

    fun isEnabled(): Boolean = _state.value.isEnabled

    fun forceAnalyzeNow() {
        forceAnalyzeFlag = true
        candidateStreakFrames = switchSensitivity.thresholdFrames
    }

    fun setAvailablePresets(presets: List<PresetInfo>) {
        this.availablePresets = presets
        if (activePresetKey.isEmpty() && presets.isNotEmpty()) {
            activePresetKey = presets.first().key
        }
    }

    fun updateMediaMetadata(title: String?, artist: String?, album: String?, genre: String?) {
        val titleChanged = title != currentTitle
        val artistChanged = artist != currentArtist
        if (titleChanged || artistChanged) {
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentGenre = genre
            lastMetadataChangeMs = SystemClock.elapsedRealtime()

            candidateStreakFrames = switchSensitivity.thresholdFrames
            forceAnalyzeFlag = true
            Log.d(TAG, "Media metadata changed: '$title' by '$artist' ($genre)")
        }
    }

    /**
     * Called at ~60 FPS with 512-bin FFT and UI peak.
     */
    fun processFrame(rawFFT: IntArray?, uiPeak: Float) {
        if (rawFFT == null || rawFFT.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        val numBins = rawFFT.size

        // 1. Equal-Loudness / Acoustic Compensated Subband Energy Calculation:
        // Raw Bass: bins 0..120 (~20 - 250 Hz) -> count 121
        // Raw Mids: bins 121..340 (~250 - 2500 Hz) -> count 220
        // Raw Highs: bins 341..511 (~2500 - 20000 Hz) -> count 171
        var rawBassSum = 0f
        var rawMidSum = 0f
        var rawHighSum = 0f
        var totalRawSum = 0f

        for (i in 0 until min(121, numBins)) {
            val v = rawFFT[i].toFloat()
            rawBassSum += v
            totalRawSum += v
        }
        for (i in 121 until min(341, numBins)) {
            val v = rawFFT[i].toFloat()
            rawMidSum += v
            totalRawSum += v
        }
        for (i in 341 until numBins) {
            val v = rawFFT[i].toFloat()
            rawHighSum += v
            totalRawSum += v
        }

        val avgBassRaw = rawBassSum / 121f
        val avgMidRaw = rawMidSum / 220f
        val avgHighRaw = rawHighSum / 171f

        // Acoustic loudness compensation weights
        // Because bass carries far more raw physical energy than highs in music recordings,
        // we weight them to reflect perceptual musical balance.
        val compBass = avgBassRaw * 0.80f
        val compMid = avgMidRaw * 1.60f
        val compHigh = avgHighRaw * 2.30f
        val totalComp = compBass + compMid + compHigh

        val bassPct = if (totalComp > 0f) compBass / totalComp else 0f
        val midPct = if (totalComp > 0f) compMid / totalComp else 0f
        val highPct = if (totalComp > 0f) compHigh / totalComp else 0f

        val totalAvg = totalRawSum / numBins
        if (uiPeak > 0.02f || totalAvg > 30f) {
            lastAudioActivityMs = now
        }

        // Subband flux for beat tracking
        val subbandEnergies = FloatArray(8)
        var binIdx = 0
        for (band in 0 until 8) {
            val targetBin = min(subbandSplits[band], numBins)
            var bSum = 0f
            var count = 0
            while (binIdx < targetBin) {
                bSum += rawFFT[binIdx].toFloat()
                count++
                binIdx++
            }
            val avg = if (count > 0) bSum / count else 0f
            subbandEnergies[band] = ln(1.0f + 0.006f * avg) * 20.0f
        }

        var onsetFlux = 0f
        val bandWeights = floatArrayOf(2.2f, 2.0f, 1.2f, 1.0f, 0.9f, 0.8f, 0.7f, 0.5f)
        for (b in 0 until 8) {
            val diff = max(0f, subbandEnergies[b] - prevSubbandEnergies[b])
            onsetFlux += diff * bandWeights[b]
            prevSubbandEnergies[b] = subbandEnergies[b]
        }

        fluxHistory[historyIndex] = onsetFlux
        historyIndex = (historyIndex + 1) % historySize
        if (historyCount < historySize) historyCount++

        val localMean = if (historyCount > 0) {
            var s = 0f
            val n = min(historyCount, 30)
            for (i in 0 until n) {
                s += fluxHistory[(historyIndex - 1 - i + historySize) % historySize]
            }
            s / n
        } else 0f

        val isBeatDetected = (onsetFlux > localMean * 1.50f + 0.35f) && (now - lastBeatPulseMs > 175L)
        if (isBeatDetected) {
            lastBeatPulseMs = now
        }
        val isPulseActive = (now - lastBeatPulseMs) < 110L

        frameCounter++
        if (frameCounter >= 10 || forceAnalyzeFlag) {
            frameCounter = 0
            val wasForced = forceAnalyzeFlag
            forceAnalyzeFlag = false
            evaluateRuleBasedPreset(now, bassPct, midPct, highPct, uiPeak, totalAvg, isPulseActive, wasForced)
        } else {
            if (_state.value.isBeatPulse != isPulseActive) {
                _state.value = _state.value.copy(isBeatPulse = isPulseActive)
            }
        }
    }

    private fun evaluateRuleBasedPreset(
        nowMs: Long,
        bassPct: Float,
        midPct: Float,
        highPct: Float,
        uiPeak: Float,
        totalAvg: Float,
        isPulseActive: Boolean,
        wasForced: Boolean
    ) {
        val isSilent = (nowMs - lastAudioActivityMs > 1400L) || (uiPeak < 0.015f && totalAvg < 20f)

        // 1. Classify Band Levels (Low / Average / High)
        // Bass: Low (<28%), Average (28-42%), High (>42%)
        // Mids: Low (<26%), Average (26-38%), High (>38%)
        // Highs: Low (<22%), Average (22-35%), High (>35%)
        val bassLevel = when {
            bassPct < 0.28f -> BandLevel.LOW
            bassPct > 0.42f -> BandLevel.HIGH
            else -> BandLevel.AVERAGE
        }

        val midLevel = when {
            midPct < 0.26f -> BandLevel.LOW
            midPct > 0.38f -> BandLevel.HIGH
            else -> BandLevel.AVERAGE
        }

        val highLevel = when {
            highPct < 0.22f -> BandLevel.LOW
            highPct > 0.35f -> BandLevel.HIGH
            else -> BandLevel.AVERAGE
        }

        // 2. Harmonic BPM estimation
        val bpmResult = estimateHarmonicBpm()
        val rawBpm = bpmResult.first
        val bpmConfidence = bpmResult.second

        if (rawBpm > 0 && bpmConfidence >= 0.25f) {
            if (smoothedBpm <= 0f) {
                smoothedBpm = rawBpm.toFloat()
            } else {
                smoothedBpm = smoothedBpm * 0.80f + rawBpm * 0.20f
            }
            lastBpmConfidence = bpmConfidence
        } else if (isSilent) {
            lastBpmConfidence = max(0f, lastBpmConfidence - 0.08f)
        }

        val displayBpm = if (isSilent && lastBpmConfidence < 0.2f) 0 else smoothedBpm.toInt()

        // 3. User's Rules Matching
        val classification = matchRuleCategory(bassLevel, midLevel, highLevel, bassPct, midPct, highPct, displayBpm, isSilent)
        val vibe = classification.first
        val category = classification.second

        // 4. Match against available presets
        if (!_state.value.isEnabled) {
            _state.value = State(
                isEnabled = false,
                bpm = displayBpm,
                bpmConfidence = lastBpmConfidence,
                isBeatPulse = isPulseActive,
                vibe = vibe,
                activePresetKey = activePresetKey,
                rationale = "Auto Preset is Disabled",
                bassLevel = bassLevel,
                midLevel = midLevel,
                highLevel = highLevel,
                bassRatio = bassPct,
                midRatio = midPct,
                trebleRatio = highPct,
                energy = uiPeak,
                sensitivity = switchSensitivity
            )
            return
        }

        if (availablePresets.isEmpty()) {
            _state.value = _state.value.copy(
                bpm = displayBpm,
                bpmConfidence = lastBpmConfidence,
                isBeatPulse = isPulseActive,
                vibe = vibe,
                rationale = "No presets available in configuration",
                bassLevel = bassLevel,
                midLevel = midLevel,
                highLevel = highLevel,
                bassRatio = bassPct,
                midRatio = midPct,
                trebleRatio = highPct,
                energy = uiPeak,
                sensitivity = switchSensitivity
            )
            return
        }

        val match = selectPresetForCategory(category, vibe, bassLevel, midLevel, highLevel, bassPct, midPct, highPct, displayBpm)
        val bestPreset = match.first
        val rationale = match.second

        // 5. Hysteresis Switching
        val isTrackChange = (nowMs - lastMetadataChangeMs) < 2500L

        if (bestPreset.key == activePresetKey) {
            candidatePresetKey = bestPreset.key
            candidateStreakFrames = 0
        } else {
            if (bestPreset.key == candidatePresetKey) {
                candidateStreakFrames += 10
            } else {
                candidatePresetKey = bestPreset.key
                candidateStreakFrames = 10
            }

            val thresholdFrames = switchSensitivity.thresholdFrames
            val dwellMs = switchSensitivity.dwellMs

            val canSwitch = wasForced || isTrackChange || activePresetKey.isEmpty() || (
                candidateStreakFrames >= thresholdFrames &&
                (nowMs - lastSwitchTimeMs >= dwellMs)
            )

            if (canSwitch) {
                activePresetKey = bestPreset.key
                lastSwitchTimeMs = nowMs
                candidateStreakFrames = 0
                Log.d(TAG, "Auto Preset switched to: ${activePresetKey}. Rationale: $rationale")
                notifyPresetSelected(activePresetKey)
            }
        }

        _state.value = State(
            isEnabled = true,
            bpm = displayBpm,
            bpmConfidence = lastBpmConfidence,
            isBeatPulse = isPulseActive,
            vibe = vibe,
            activePresetKey = activePresetKey,
            candidatePresetKey = candidatePresetKey,
            rationale = rationale,
            bassLevel = bassLevel,
            midLevel = midLevel,
            highLevel = highLevel,
            bassRatio = bassPct,
            midRatio = midPct,
            trebleRatio = highPct,
            energy = uiPeak,
            sensitivity = switchSensitivity
        )
    }

    private enum class MusicalCategory {
        SOFT_ROMANTIC,    // Mids High, Highs Avg/Low, Bass Low
        TECHNO_EDM,       // Bass High, Mids Low, Highs Avg/High
        POP_JAZZ_ENERGETIC, // Bass High/Avg, Highs High, Mids Avg (e.g. Dai Dai)
        ACOUSTIC_VOCAL,   // Mids High, Bass Low
        ROCK_HIGH_ENERGY, // Bass High, Mids High, Highs High
        AMBIENT_CLASSICAL // Bass Low, Highs Low
    }

    /**
     * Implements user's frequency level rules combined with metadata semantics.
     */
    private fun matchRuleCategory(
        bassLevel: BandLevel,
        midLevel: BandLevel,
        highLevel: BandLevel,
        bassPct: Float,
        midPct: Float,
        highPct: Float,
        bpm: Int,
        isSilent: Boolean
    ): Pair<String, MusicalCategory> {
        if (isSilent) return Pair("Silent / Idle", MusicalCategory.AMBIENT_CLASSICAL)

        val metaText = "${currentTitle.orEmpty()} ${currentArtist.orEmpty()} ${currentAlbum.orEmpty()} ${currentGenre.orEmpty()}".lowercase(Locale.ROOT)

        // Metadata override checks for distinct songs / artists
        val isExplicitRomantic = metaText.contains("love") || metaText.contains("romantic") || metaText.contains("sad") ||
                metaText.contains("die with a smile") || metaText.contains("acoustic") || metaText.contains("ballad") ||
                metaText.contains("slow") || metaText.contains("piano") || metaText.contains("taylor") || metaText.contains("laufey") ||
                metaText.contains("adele") || metaText.contains("lo-fi") || metaText.contains("lofi") || metaText.contains("billie")

        val isExplicitPopEnergetic = metaText.contains("dai dai") || metaText.contains("pop") || metaText.contains("jazz") ||
                metaText.contains("funk") || metaText.contains("disco") || metaText.contains("dua lipa") || metaText.contains("weeknd") ||
                metaText.contains("bruno mars") || metaText.contains("uptown") || metaText.contains("groove")

        val isExplicitTechno = metaText.contains("techno") || metaText.contains("edm") || metaText.contains("rave") ||
                metaText.contains("hardstyle") || metaText.contains("phonk") || metaText.contains("drift") || metaText.contains("dubstep") ||
                metaText.contains("club") || metaText.contains("house")

        if (isExplicitRomantic && bassLevel != BandLevel.HIGH) {
            return Pair("Soft & Romantic (Mids ${(midPct * 100).toInt()}%, Bass ${(bassPct * 100).toInt()}%)", MusicalCategory.SOFT_ROMANTIC)
        }
        if (isExplicitPopEnergetic) {
            return Pair("Pop / Jazz / Energetic (Highs ${(highPct * 100).toInt()}%, Bass ${(bassPct * 100).toInt()}%)", MusicalCategory.POP_JAZZ_ENERGETIC)
        }
        if (isExplicitTechno) {
            return Pair("Techno / EDM (Bass ${(bassPct * 100).toInt()}%, Mids ${(midPct * 100).toInt()}%)", MusicalCategory.TECHNO_EDM)
        }

        // --- Core Rules based on User's System ---

        // Rule 1: Soft / Romantic / Sad Song
        // Mids: High, Highs: Average/Low, Bass: Low
        if (midLevel == BandLevel.HIGH && bassLevel == BandLevel.LOW) {
            return Pair("Soft / Romantic (Mids High, Bass Low)", MusicalCategory.SOFT_ROMANTIC)
        }

        // Rule 2: Techno / EDM
        // Bass: High, Mids: Low, Highs: Average/High
        if (bassLevel == BandLevel.HIGH && midLevel == BandLevel.LOW) {
            return Pair("Techno / EDM (Bass High, Mids Low)", MusicalCategory.TECHNO_EDM)
        }

        // Rule 3: Pop / Jazz / Energetic (e.g. Dai Dai)
        // Bass: High/Avg, Highs: High, Mids: Average
        if (highLevel == BandLevel.HIGH && bassLevel != BandLevel.LOW && midLevel == BandLevel.AVERAGE) {
            return Pair("Pop / Jazz (Highs High, Bass High/Avg)", MusicalCategory.POP_JAZZ_ENERGETIC)
        }

        // Rule 4: Rock / Metal
        // Bass: High, Mids: High, Highs: High
        if (bassLevel == BandLevel.HIGH && midLevel == BandLevel.HIGH && highLevel == BandLevel.HIGH) {
            return Pair("Rock / Metal (Full Spectrum High)", MusicalCategory.ROCK_HIGH_ENERGY)
        }

        // Rule 5: Acoustic / Vocal Solo
        // Mids: High, Bass: Low, Highs: Low
        if (midLevel == BandLevel.HIGH && bassLevel == BandLevel.LOW && highLevel == BandLevel.LOW) {
            return Pair("Acoustic / Vocal Solo (Mids High)", MusicalCategory.ACOUSTIC_VOCAL)
        }

        // Rule 6: Ambient / Classical
        // Bass: Low, Highs: Low
        if (bassLevel == BandLevel.LOW && highLevel == BandLevel.LOW) {
            return Pair("Ambient / Classical (Soft Mellow)", MusicalCategory.AMBIENT_CLASSICAL)
        }

        // Fallbacks with detailed metric breakdown
        return when {
            bassLevel == BandLevel.HIGH -> Pair("Bass Dominant (${(bassPct * 100).toInt()}% Bass)", MusicalCategory.TECHNO_EDM)
            highLevel == BandLevel.HIGH -> Pair("Bright / Energetic (${(highPct * 100).toInt()}% Highs)", MusicalCategory.POP_JAZZ_ENERGETIC)
            midLevel == BandLevel.HIGH -> Pair("Vocal / Melodic (${(midPct * 100).toInt()}% Mids)", MusicalCategory.SOFT_ROMANTIC)
            else -> Pair("Balanced Dynamic (${(bassPct * 100).toInt()}% B, ${(midPct * 100).toInt()}% M, ${(highPct * 100).toInt()}% H)", MusicalCategory.POP_JAZZ_ENERGETIC)
        }
    }

    /**
     * Picks preset according to the matched category with natural explanation.
     */
    private fun selectPresetForCategory(
        category: MusicalCategory,
        vibe: String,
        bassLevel: BandLevel,
        midLevel: BandLevel,
        highLevel: BandLevel,
        bassPct: Float,
        midPct: Float,
        highPct: Float,
        bpm: Int
    ): Pair<PresetInfo, String> {
        var bestPreset = availablePresets.first()
        var highestScore = -10000f
        var bestRationale = "Selected standard default preset"

        for (preset in availablePresets) {
            var score = 0f
            val keyLower = preset.key.lowercase(Locale.ROOT)
            val descLower = (preset.description ?: preset.key).lowercase(Locale.ROOT)

            val isBassPreset = descLower.contains("bass") || descLower.contains("flash") || descLower.contains("circle") || descLower.contains("spike") || descLower.contains("full-bass")
            val isSmoothPreset = keyLower.endsWith("s") || descLower.contains("simple") || descLower.contains("smooth") || descLower.contains("vocal") || descLower.contains("decay") || descLower.contains("soft") || descLower.contains("split")
            val isSpectrumPreset = descLower.contains("spectrum") || descLower.contains("zebra") || descLower.contains("bars") || descLower.contains("perc") || descLower.contains("classic") || descLower.contains("default") || descLower.contains("noice") || descLower.contains("33")

            when (category) {
                MusicalCategory.SOFT_ROMANTIC, MusicalCategory.ACOUSTIC_VOCAL, MusicalCategory.AMBIENT_CLASSICAL -> {
                    // Soft, Romantic, Acoustic songs MUST favor smooth/vocal/simple presets and PENALIZE bass-flash
                    if (isSmoothPreset) score += 120f
                    if (descLower.contains("vocal")) score += 40f
                    if (descLower.contains("smooth") || keyLower.endsWith("s")) score += 30f
                    if (isSpectrumPreset) score += 35f
                    if (isBassPreset) score -= 300f // Never pick bass flash for romantic songs!
                }

                MusicalCategory.TECHNO_EDM -> {
                    // Techno / EDM MUST favor bass-flash, full bass, gauge
                    if (isBassPreset) score += 120f
                    if (descLower.contains("flash") || descLower.contains("full")) score += 35f
                    if (isSpectrumPreset) score += 35f
                    if (isSmoothPreset) score -= 100f
                }

                MusicalCategory.POP_JAZZ_ENERGETIC -> {
                    // Pop / Jazz (e.g. Dai Dai) MUST favor spectrum, zebra, bars, dynamic multi-zone
                    if (isSpectrumPreset) score += 120f
                    if (descLower.contains("zebra") || descLower.contains("spectrum") || descLower.contains("bars")) score += 40f
                    if (isSmoothPreset) score += 20f
                    if (isBassPreset) score -= 50f
                }

                MusicalCategory.ROCK_HIGH_ENERGY -> {
                    // Rock / Metal favors zebra, spectrum
                    if (isSpectrumPreset) score += 120f
                    if (descLower.contains("zebra")) score += 30f
                    if (isBassPreset) score += 20f
                    if (isSmoothPreset) score -= 60f
                }
            }

            // Current preset slight stability bonus
            if (preset.key == activePresetKey) {
                score += 8f
            }

            if (score > highestScore) {
                highestScore = score
                bestPreset = preset

                bestRationale = when (category) {
                    MusicalCategory.SOFT_ROMANTIC ->
                        "Mids High (${(midPct * 100).toInt()}%) & Low Bass (${(bassPct * 100).toInt()}%) matched Smooth preset '${preset.key}'"
                    MusicalCategory.TECHNO_EDM ->
                        "Bass High (${(bassPct * 100).toInt()}%) & Low Mids (${(midPct * 100).toInt()}%) matched Bass preset '${preset.key}'"
                    MusicalCategory.POP_JAZZ_ENERGETIC ->
                        "Highs High (${(highPct * 100).toInt()}%) & Energetic Bass (${(bassPct * 100).toInt()}%) matched Spectrum preset '${preset.key}'"
                    MusicalCategory.ACOUSTIC_VOCAL ->
                        "Acoustic vocal balance matched Vocal preset '${preset.key}'"
                    MusicalCategory.ROCK_HIGH_ENERGY ->
                        "Full spectrum high energy intensity matched preset '${preset.key}'"
                    MusicalCategory.AMBIENT_CLASSICAL ->
                        "Mellow dynamic range matched Smooth preset '${preset.key}'"
                }
            }
        }

        return Pair(bestPreset, bestRationale)
    }

    private fun estimateHarmonicBpm(): Pair<Int, Float> {
        if (historyCount < 60) return Pair(0, 0f)

        val n = min(historyCount, historySize)
        val buf = FloatArray(n)
        val startIdx = (historyIndex - n + historySize) % historySize
        var sum = 0f
        for (i in 0 until n) {
            buf[i] = fluxHistory[(startIdx + i) % historySize]
            sum += buf[i]
        }
        val mean = sum / n

        val minLag = 18 // ~200 BPM
        val maxLag = 72 // ~50 BPM

        val rawCorr = FloatArray(maxLag + 1)
        var totalRaw = 0f
        var countRaw = 0

        for (lag in minLag..maxLag) {
            var dot = 0f
            var norm1 = 0f
            var norm2 = 0f
            val count = n - lag

            for (i in 0 until count) {
                val x = buf[i] - mean
                val y = buf[i + lag] - mean
                dot += x * y
                norm1 += x * x
                norm2 += y * y
            }

            val denom = sqrt(norm1 * norm2)
            val r = if (denom > 1e-6f) max(0f, dot / denom) else 0f
            rawCorr[lag] = r
            totalRaw += r
            countRaw++
        }

        val avgRaw = if (countRaw > 0) totalRaw / countRaw else 0f
        var bestLag = -1
        var maxEnhancedCorr = 0f

        for (lag in minLag..maxLag) {
            val r1 = rawCorr[lag]
            val r2 = if (lag * 2 <= maxLag) rawCorr[lag * 2] else 0f
            val r3 = if (lag * 3 <= maxLag) rawCorr[lag * 3] else 0f
            val rHalf = if (lag % 2 == 0 && lag / 2 >= minLag) rawCorr[lag / 2] else 0f

            val enhanced = r1 + (r2 * 0.55f) + (r3 * 0.25f) + (rHalf * 0.35f)

            if (enhanced > maxEnhancedCorr) {
                maxEnhancedCorr = enhanced
                bestLag = lag
            }
        }

        if (bestLag > 0 && maxEnhancedCorr > 0.35f) {
            val peakCorr = rawCorr[bestLag]
            val prominence = max(0f, peakCorr - avgRaw)
            val confidence = min(1.0f, prominence * 2.5f + peakCorr * 0.5f)

            val calculatedBpm = (3600f / bestLag).toInt()
            val normalizedBpm = when {
                calculatedBpm < 60 -> calculatedBpm * 2
                calculatedBpm > 190 -> calculatedBpm / 2
                else -> calculatedBpm
            }
            return Pair(normalizedBpm, confidence)
        }

        return Pair(0, 0f)
    }

    fun reset() {
        smoothedBpm = 0f
        lastBpmConfidence = 0f
        historyIndex = 0
        historyCount = 0
        candidateStreakFrames = 0
        _state.value = State(
            isEnabled = _state.value.isEnabled,
            bpm = 0,
            bpmConfidence = 0f,
            isBeatPulse = false,
            vibe = "Reset",
            activePresetKey = activePresetKey,
            rationale = "Engine reset",
            bassLevel = BandLevel.AVERAGE,
            midLevel = BandLevel.AVERAGE,
            highLevel = BandLevel.AVERAGE,
            bassRatio = 0f,
            midRatio = 0f,
            trebleRatio = 0f,
            energy = 0f,
            sensitivity = switchSensitivity
        )
    }
}


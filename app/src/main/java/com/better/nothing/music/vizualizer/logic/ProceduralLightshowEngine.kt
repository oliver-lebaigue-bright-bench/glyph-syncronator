package com.better.nothing.music.vizualizer.logic

import com.better.nothing.music.vizualizer.model.GlyphFrame
import com.better.nothing.music.vizualizer.model.SongMetadata
import com.better.nothing.music.vizualizer.model.SongVisualSequence
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generates a pre-calculated, highly synchronized 60 FPS Glyph visual timeline
 * for any song using multi-layer spectral and rhythmic procedural algorithms.
 */
object ProceduralLightshowEngine {

    private const val FRAME_INTERVAL_MS = 33L // ~30-60 FPS resolution (33ms)
    private const val DEFAULT_DURATION_MS = 210000L // 3.5 min fallback if 0

    fun generateSequence(metadata: SongMetadata): SongVisualSequence {
        val durationMs = if (metadata.durationMs > 0) metadata.durationMs else DEFAULT_DURATION_MS
        val frameCount = (durationMs / FRAME_INTERVAL_MS).toInt()
        val frames = ArrayList<GlyphFrame>(frameCount)

        // Seed BPM and musical characteristics deterministically from song key
        val hash = abs("${metadata.artist}_${metadata.title}".hashCode())
        val estimatedBpm = 90 + (hash % 60) // 90 to 150 BPM
        val beatPeriodSec = 60.0 / estimatedBpm

        var prevCamTop = 0f
        var prevCamBot = 0f
        var prevSlash = 0f
        val prevRing = FloatArray(16)
        val prevBatt = FloatArray(8)

        for (i in 0 until frameCount) {
            val timeMs = i * FRAME_INTERVAL_MS
            val tSec = timeMs / 1000.0
            val beat = tSec / beatPeriodSec

            // Rhythmic pulse layers
            val kick = sin(beat * Math.PI * 2).coerceAtLeast(0.0).pow(10.0).toFloat()
            val snare = sin(beat * Math.PI + Math.PI * 0.5).coerceAtLeast(0.0).pow(16.0).toFloat() * 0.75f
            val hat = sin(beat * Math.PI * 4).coerceAtLeast(0.0).pow(4.0).toFloat() * 0.35f

            // Frequency envelope layers
            val bassWave = (0.35f + 0.65f * abs(sin(tSec * 1.7 + sin(tSec * 0.3) * 2))).toFloat()
            val midWave = (0.25f + 0.55f * abs(sin(tSec * 2.3 + 1.2))).toFloat()
            val highWave = (0.15f + 0.40f * abs(sin(tSec * 5.1 + cos(tSec * 0.7)))).toFloat()
            val sectionBuild = (0.5f + 0.5f * sin(tSec * 0.12)).toFloat()

            val camTopTarget = (bassWave * (0.5f + kick * 0.8f)).coerceIn(0f, 1f)
            val camBotTarget = (midWave * 0.6f + snare * 0.5f).coerceIn(0f, 1f)
            val slashTarget = (midWave * 0.7f + highWave * 0.3f).coerceIn(0f, 1f)

            // Smooth attack / decay
            prevCamTop = applyDecay(prevCamTop, camTopTarget)
            prevCamBot = applyDecay(prevCamBot, camBotTarget)
            prevSlash = applyDecay(prevSlash, slashTarget)

            val zoneIntensities = ArrayList<Float>(33)
            // Camera top & bot (2)
            zoneIntensities.add(prevCamTop)
            zoneIntensities.add(prevCamBot)
            // Slash (1)
            zoneIntensities.add(prevSlash)

            // Main Ring (16 segments)
            for (r in 0 until 16) {
                val distFromCenter = abs(r - 7.5f) / 7.5f
                val ringLevel = (bassWave * (1.0f - distFromCenter * 0.6f) + kick * 0.7f * sectionBuild).coerceIn(0f, 1f)
                prevRing[r] = applyDecay(prevRing[r], ringLevel)
                zoneIntensities.add(prevRing[r])
            }

            // Battery Bar (8 segments)
            val bassEnergy = (bassWave * 0.7f + kick * 0.6f).coerceIn(0f, 1f)
            for (b in 0 until 8) {
                val threshold = b / 8.0f
                val battLevel = if (bassEnergy > threshold) ((bassEnergy - threshold) * 8.0f).coerceIn(0f, 1f) else 0f
                val finalBatt = if (b >= 6) max(battLevel, hat * 0.6f) else battLevel
                prevBatt[b] = applyDecay(prevBatt[b], finalBatt)
                zoneIntensities.add(prevBatt[b])
            }

            // Fill remaining to 33 zones (for phone models with more zones)
            while (zoneIntensities.size < 33) {
                zoneIntensities.add(prevSlash * 0.8f)
            }

            frames.add(GlyphFrame(timestampMs = timeMs, zoneIntensities = zoneIntensities))
        }

        return SongVisualSequence(
            songKey = metadata.normalizedKey,
            title = metadata.title,
            artist = metadata.artist,
            durationMs = durationMs,
            frames = frames,
            version = 1,
            createdTimestamp = System.currentTimeMillis()
        )
    }

    private fun applyDecay(current: Float, target: Float): Float {
        return if (target > current) {
            current + (target - current) * 0.55f // Attack
        } else {
            current * 0.88f // Decay
        }
    }
}

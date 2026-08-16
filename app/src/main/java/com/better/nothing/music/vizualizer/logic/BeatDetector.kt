package com.better.nothing.music.vizualizer.logic

import android.os.SystemClock
import java.util.Arrays
import kotlin.math.ln
import kotlin.math.max

/**
 * Centeralized beat detection logic to be used by UI, Flashlight, and Haptic engines.
 */
class BeatDetector(
    var sensitivity: Float = 1.0f,
    var cooldownMs: Long = 60L
) {
    private val deltaHistory = FloatArray(61)
    private val sortedHistory = FloatArray(61)
    private var deltaIndex = 0
    private var deltaCount = 0
    private var prevEnergy = 0f
    private var lastTriggerMs = 0L
    private var thresholdMask = 0f

    /**
     * Processes a frame of magnitude data and returns true if a beat is detected.
     */
    fun detect(magnitude: FloatArray, binLo: Int, binHi: Int): Boolean {
        if (magnitude.isEmpty()) return false

        val start = max(0, minOf(binLo, magnitude.lastIndex))
        val end = max(start, minOf(binHi, magnitude.lastIndex))

        var sum = 0f
        for (i in start..end) {
            sum += magnitude[i]
        }

        val energy = ln(1f + sum)
        val delta = energy - prevEnergy
        prevEnergy = energy

        pushDelta(delta)

        val threshold = max(medianDelta() * (2.2f * sensitivity), thresholdMask)
        val now = SystemClock.elapsedRealtime()

        val triggered = delta > threshold && delta > 0.025f && (now - lastTriggerMs) >= cooldownMs
        if (triggered) {
            lastTriggerMs = now
            thresholdMask = delta * 0.8f
        }

        thresholdMask *= 0.85f
        return triggered
    }

    private fun pushDelta(delta: Float) {
        deltaHistory[deltaIndex] = delta.coerceAtLeast(0.0001f)
        deltaIndex = (deltaIndex + 1) % deltaHistory.size
        if (deltaCount < deltaHistory.size) deltaCount++
    }

    private fun medianDelta(): Float {
        if (deltaCount == 0) return 0.01f
        System.arraycopy(deltaHistory, 0, sortedHistory, 0, deltaCount)
        Arrays.sort(sortedHistory, 0, deltaCount)

        return if (deltaCount % 2 == 1) {
            sortedHistory[deltaCount / 2]
        } else {
            val mid = deltaCount / 2
            (sortedHistory[mid - 1] + sortedHistory[mid]) * 0.5f
        }
    }

    fun reset() {
        deltaIndex = 0
        deltaCount = 0
        prevEnergy = 0f
        lastTriggerMs = 0L
        thresholdMask = 0f
        Arrays.fill(deltaHistory, 0f)
    }
}

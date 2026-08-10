package com.better.nothing.music.visualizer.model

data class GlobalStats(
    val totalVisualizedTimeMs: Long = 0L,
    val totalActiveTimeMs: Long = 0L,
    val totalIdleTimeMs: Long = 0L,
    val totalGlyphTimeMs: Long = 0L,
    val totalHapticTimeMs: Long = 0L,
    val totalFlashlightTimeMs: Long = 0L,
    val totalSessions: Long = 0L,
    val totalBeatsDetected: Long = 0L,
    val userCount: Long = 0L
)

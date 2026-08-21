package com.glyphix.app.logic.smartcapture

data class GlyphFrame(
    val timestampMs: Long,
    val intensities: FloatArray
)

data class SongVisualSequence(
    val songKey: String,
    val durationMs: Long,
    val frames: List<GlyphFrame>
)

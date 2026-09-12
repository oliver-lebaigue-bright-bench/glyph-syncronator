package com.glyphix.app.logic.smartcapture

data class GlyphFrame(
    var timestampMs: Long = 0L,
    var intensities: List<Float> = emptyList()
) {
    fun getIntensitiesArray(): FloatArray {
        val arr = FloatArray(intensities.size)
        for (i in intensities.indices) {
            arr[i] = intensities[i]
        }
        return arr
    }
}

data class SongVisualSequence(
    var songKey: String = "",
    var durationMs: Long = 0L,
    var frames: List<GlyphFrame> = emptyList()
)

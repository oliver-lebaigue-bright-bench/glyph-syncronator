package com.better.nothing.music.vizualizer.model

import androidx.annotation.Keep

/**
 * Data class representing song metadata extracted from MediaSession notifications.
 */
@Keep
data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val album: String = ""
) {
    /**
     * Generates a normalized, unique key for cloud/local storage lookup.
     * Example: "The Weeknd - Blinding Lights (Remastered)" -> "the_weeknd_blinding_lights"
     */
    val normalizedKey: String
        get() {
            val raw = "${artist.lowercase().trim()}_${title.lowercase().trim()}"
            val cleaned = raw.replace(Regex("\\(.*?\\)|\\[.*?\\]|- remastered.*|- single.*"), "")
                .replace(Regex("[^a-z0-9_]"), "")
                .replace(Regex("_+"), "_")
                .trim('_')
            return if (cleaned.isNotEmpty()) cleaned else "unknown_track_${Math.abs(raw.hashCode())}"
        }
}

/**
 * Represents a single frame of zone brightness values at a specific timestamp.
 */
@Keep
data class GlyphFrame(
    val timestampMs: Long = 0L,
    val zoneIntensities: List<Float> = emptyList()
)

/**
 * Complete pre-calculated or generated glyph animation timeline for a song.
 */
@Keep
data class SongVisualSequence(
    val songKey: String = "",
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val frames: List<GlyphFrame> = emptyList(),
    val version: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val downloads: Int = 0
)

package com.better.nothing.music.vizualizer.model

import com.better.nothing.music.vizualizer.logic.AudioProcessor
import androidx.annotation.Keep

@Keep
data class CommunityPreset(
    val id: String = "",
    val name: String = "",
    val author: String = "Anonymous",
    val authorId: String = "",
    val phoneModel: String = "",
    val zones: List<ZoneData> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val downloads: Int = 0
)

@Keep
data class ZoneData(
    val lowHz: Float = 0f,
    val highHz: Float = 0f,
    val lowPercent: Float? = null,
    val highPercent: Float? = null
) {
    fun toZoneSpec(): AudioProcessor.ZoneSpec {
        return AudioProcessor.ZoneSpec(
            lowHz, 
            highHz, 
            lowPercent ?: Float.NaN, 
            highPercent ?: Float.NaN
        )
    }

    companion object {
        fun fromZoneSpec(spec: AudioProcessor.ZoneSpec): ZoneData {
            return ZoneData(
                spec.lowHz,
                spec.highHz,
                if (spec.lowPercent.isNaN()) null else spec.lowPercent,
                if (spec.highPercent.isNaN()) null else spec.highPercent
            )
        }
    }
}

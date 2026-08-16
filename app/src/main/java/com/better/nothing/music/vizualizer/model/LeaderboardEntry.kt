package com.better.nothing.music.vizualizer.model

import androidx.annotation.Keep

@Keep
data class LeaderboardEntry(
    val userId: String = "",
    val name: String = "Anonymous",
    val profilePictureUrl: String? = null,
    val totalTimeMs: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

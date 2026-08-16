package com.better.nothing.music.vizualizer.model

import androidx.annotation.Keep

@Keep
data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val style: String = "INFO", // INFO, URGENT, FEATURE
    val link: String? = null,
    val linkText: String? = null
)

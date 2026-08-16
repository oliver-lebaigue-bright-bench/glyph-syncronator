package com.better.nothing.music.vizualizer.logic

import android.content.Context

object ServerConfig {
    const val DEFAULT_POCKETBASE_URL = "https://pb.glyph-syncronator.org"
    const val CONNECT_TIMEOUT_SEC = 3L
    const val READ_TIMEOUT_SEC = 3L

    fun getPocketBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        return prefs.getString("homeserver_url", DEFAULT_POCKETBASE_URL)?.trimEnd('/')
            ?: DEFAULT_POCKETBASE_URL
    }

    fun setPocketBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("homeserver_url", url.trimEnd('/')).apply()
    }
}

package com.better.nothing.music.vizualizer.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsHelper(context: Context) {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
    }

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics.logEvent(name, params)
    }

    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun logTabSelected(tabName: String) {
        val bundle = Bundle().apply {
            putString("tab_name", tabName)
        }
        logEvent("tab_selected", bundle)
    }

    fun logPresetSelected(presetKey: String, isCustom: Boolean) {
        val bundle = Bundle().apply {
            putString("preset_key", if (isCustom) "community_preset" else presetKey)
            putBoolean("is_custom", isCustom)
            putString(FirebaseAnalytics.Param.ITEM_ID, presetKey)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, if (isCustom) "custom_preset" else "default_preset")
        }
        logEvent("preset_selected", bundle)
    }

    fun logPresetFavorited(presetKey: String, isFavorited: Boolean, isCustom: Boolean) {
        val bundle = Bundle().apply {
            putString("preset_key", if (isCustom) "community_preset" else presetKey)
            putBoolean("is_favorited", isFavorited)
            putBoolean("is_custom", isCustom)
            putString(FirebaseAnalytics.Param.ITEM_ID, presetKey)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, if (isCustom) "custom_preset" else "default_preset")
        }
        logEvent("preset_favorited", bundle)
    }

    fun logCommunityPresetDownloaded(presetName: String, author: String) {
        val bundle = Bundle().apply {
            putString("preset_name", "community_preset")
            putString("author", author)
        }
        logEvent("community_preset_downloaded", bundle)
    }

    fun logPresetShared(presetName: String) {
        val bundle = Bundle().apply {
            putString("preset_name", "community_preset")
        }
        logEvent("preset_shared", bundle)
    }
    
    fun logVisualizerStarted(isRunning: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("is_running", isRunning)
        }
        logEvent("visualizer_toggle", bundle)
    }

    fun logCaptureSourceChanged(source: String) {
        val bundle = Bundle().apply {
            putString("source", source)
        }
        logEvent("capture_source_changed", bundle)
    }

    fun logSettingChanged(settingName: String, value: Any) {
        val bundle = Bundle().apply {
            putString("setting_name", settingName)
            when (value) {
                is String -> putString("value_string", value)
                is Boolean -> putBoolean("value_boolean", value)
                is Int -> putInt("value_int", value)
                is Float -> putFloat("value_float", value)
                is Long -> putLong("value_long", value)
                is Double -> putDouble("value_double", value)
                else -> putString("value_string", value.toString())
            }
        }
        logEvent("setting_changed", bundle)
    }

    fun logAppOpen(openCount: Int) {
        val bundle = Bundle().apply {
            putInt("open_count", openCount)
        }
        logEvent("app_open_stats", bundle)
    }

    fun logThemeChanged(themeName: String) {
        val bundle = Bundle().apply {
            putString("theme_name", themeName)
        }
        logEvent("theme_changed", bundle)
    }

    fun logFontChanged(fontName: String) {
        val bundle = Bundle().apply {
            putString("font_name", fontName)
        }
        logEvent("font_changed", bundle)
    }

    fun logAnnouncementViewed(announcementId: String) {
        val bundle = Bundle().apply {
            putString("announcement_id", announcementId)
        }
        logEvent("announcement_viewed", bundle)
    }

    fun logAnnouncementClicked(announcementId: String, action: String) {
        val bundle = Bundle().apply {
            putString("announcement_id", announcementId)
            putString("action", action)
        }
        logEvent("announcement_clicked", bundle)
    }

    fun logProfileUpdate(field: String) {
        val bundle = Bundle().apply {
            putString("field", field)
        }
        logEvent("profile_updated", bundle)
    }

    fun logError(errorCode: String, message: String) {
        val bundle = Bundle().apply {
            putString("error_code", errorCode)
            putString("error_message", message)
        }
        logEvent("app_error", bundle)
    }

    fun logUpdateChecked(currentVersion: String, latestVersion: String, available: Boolean) {
        val bundle = Bundle().apply {
            putString("current_version", currentVersion)
            putString("latest_version", latestVersion)
            putBoolean("available", available)
        }
        logEvent("update_checked", bundle)
    }

    fun logShizukuPermissionResult(granted: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("granted", granted)
        }
        logEvent("shizuku_permission_result", bundle)
    }

    fun logDeviceSpoofed(deviceName: String) {
        val bundle = Bundle().apply {
            putString("spoofed_device", deviceName)
        }
        logEvent("device_spoofed", bundle)
    }

    fun logLatencyChanged(latencyMs: Int, route: String?) {
        val bundle = Bundle().apply {
            putInt("latency_ms", latencyMs)
            putString("route", route)
        }
        logEvent("latency_changed", bundle)
    }

    fun logStatsSynced(visualizedTime: Long, glyphTime: Long, hapticTime: Long, flashlightTime: Long) {
        val bundle = Bundle().apply {
            putLong("total_visualized_time", visualizedTime)
            putLong("total_glyph_time", glyphTime)
            putLong("total_haptic_time", hapticTime)
            putLong("total_flashlight_time", flashlightTime)
        }
        logEvent("stats_synced", bundle)
    }
}

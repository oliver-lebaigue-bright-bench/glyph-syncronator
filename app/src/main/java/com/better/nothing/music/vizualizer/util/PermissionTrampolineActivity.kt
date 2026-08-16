package com.better.nothing.music.vizualizer.util

import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.MainActivity
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionTrampolineActivity : ComponentActivity() {

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, result.data)
                    putExtra(AudioCaptureService.EXTRA_PRESET_KEY, getDefaultPreset())
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analytics = AnalyticsHelper(this)
        analytics.logScreenView("permission_trampoline")
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            analytics.logError("missing_permission", "RECORD_AUDIO missing in trampoline")
            // If essential recording permission is missing, redirect to main activity
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(MainActivity.EXTRA_REQUEST_START, true)
            startActivity(intent)
            finish()
            return
        }

        val appPrefs = getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val sourceStr = appPrefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        val source = try {
            AudioCaptureService.CaptureSource.valueOf(sourceStr!!)
        } catch (e: Exception) {
            AudioCaptureService.CaptureSource.INTERNAL
        }

        if (source == AudioCaptureService.CaptureSource.MIC || source == AudioCaptureService.CaptureSource.VIZUALIZER) {
            val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_PRESET_KEY, getDefaultPreset())
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            finish()
            return
        }

        try {
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            analytics.logError("projection_launch_failed", e.message ?: "Unknown")
            // Fallback to MainActivity if possible
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
    
    private fun getDefaultPreset(): String {
        val appPrefs = getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        return appPrefs.getString("selected_preset", "np1s")?.takeIf { it.isNotBlank() } ?: "np1s"
    }
}

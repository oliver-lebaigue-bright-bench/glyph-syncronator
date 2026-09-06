package com.glyphix.app.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.glyphix.app.service.AudioCaptureService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class AodActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.attributes.screenBrightness = 0.01f // Very dim

        enableEdgeToEdge()
        setContent {
            AodScreen(onDismiss = { finish() })
        }
    }
}

@Composable
fun AodScreen(onDismiss: () -> Unit) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    var magnitudes by remember { mutableStateOf(floatArrayOf()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance().time
            delay(1000.milliseconds)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            AudioCaptureService.sInstance?.let {
                val latest = it.latestMagnitudes
                if (latest != null) {
                    magnitudes = latest.copyOf()
                }
            }
            delay(16.milliseconds)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        // Edge Visualizer Glow
        AndroidView(
            factory = { context ->
                EdgeVisualizerView(context).apply {
                    setThickness(64)
                    setScreenRadius(120f)
                    setBarCounts(18, 36)
                    setColor(android.graphics.Color.argb(180, 255, 255, 255))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(rotationZ = 0.5f, scaleX = 1.02f, scaleY = 1.02f),
            update = { view ->
                val service = AudioCaptureService.sInstance
                if (service != null) {
                    // Balanced sensitivity for AOD
                    view.setSensitivity(service.mLensVisualizerSensitivity * 9.0f)
                    view.updateMagnitudes(magnitudes, 48000)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = timeFormat.format(currentTime),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 86.sp,
                fontFamily = NDot55FontFamily,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = dateFormat.format(currentTime).uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 18.sp,
                fontFamily = NTypeFontFamily,
                letterSpacing = 2.sp
            )
        }

        Text(
            text = "GLYPHIX ACTIVE",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            color = Color.White.copy(alpha = 0.2f),
            fontSize = 12.sp,
            fontFamily = NDotFontFamily,
            letterSpacing = 4.sp
        )
    }
}

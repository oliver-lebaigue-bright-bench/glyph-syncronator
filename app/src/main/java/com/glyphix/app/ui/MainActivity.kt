package com.glyphix.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.projection.MediaProjectionManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.glyphix.app.R
import com.glyphix.app.model.DeviceProfile
import com.glyphix.app.service.AudioCaptureService
import com.glyphix.app.service.GlyphNotificationListener
import com.glyphix.app.ui.PrimaryScreens.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private var service: AudioCaptureService? = null
    private var bound = false
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var hasPendingToken = false
    private var pendingVisualizerStart = false

    private val musicThemeHandler by lazy { MusicThemeHandler(this, viewModel) }

    companion object {
        const val EXTRA_REQUEST_START = "request_start"
        var serviceStatic: AudioCaptureService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as AudioCaptureService.LocalBinder
            service = localBinder.service
            serviceStatic = service
            bound = true

            applyServiceSettings()

            lifecycleScope.launch {
                service?.isRunningFlow()?.collect { running ->
                    viewModel.setRunning(running)
                }
            }

            if (hasPendingToken) {
                deliverProjectionToken(pendingResultCode, pendingData!!)
                hasPendingToken = false
            }

            if (pendingVisualizerStart) {
                service?.startVisualizer()
                pendingVisualizerStart = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceStatic = null
            bound = false
        }
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            deliverProjectionToken(result.resultCode, result.data!!)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            toggleVisualizer()
        } else {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                viewModel.linkWithCredential(credential)
            } catch (e: Exception) {
                Log.e("MainActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchGoogleSignIn() {
        try {
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId.isEmpty()) {
                Toast.makeText(this, "Web Client ID is missing!", Toast.LENGTH_LONG).show()
                return
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to launch Google Sign In", e)
            Toast.makeText(this, "Launcher error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {}

        val intent = Intent(this, AudioCaptureService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (isNotificationServiceEnabled()) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    musicThemeHandler.sessionsChangedListener,
                    ComponentName(this, GlyphNotificationListener::class.java)
                )
                musicThemeHandler.updateActiveMediaController()
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Failed to add sessions listener: ${e.message}")
            }
        }

        // Handle deep link on cold launch
        intent?.data?.let { uri ->
            if (uri.scheme == "glyphix" && uri.host == "spotify-callback") {
                viewModel.spotifyAuthManager.handleAuthCallback(uri) { success ->
                    if (success) {
                        viewModel.selectTab(Tab.Spotify)
                    }
                }
            }
        }

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
            val bananaMode by viewModel.bananaModeEnabled.collectAsStateWithLifecycle()
            val penisMode by viewModel.penisModeEnabled.collectAsStateWithLifecycle()
            val musicThemeColor by viewModel.musicThemeColor.collectAsStateWithLifecycle()
            val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    val rawFloat = FloatArray(512)
                    val decayedFloat = FloatArray(512)
                    while (true) {
                        service?.let { s ->
                            s.currentLightState?.let {
                                viewModel.setVisualizerState(it)
                            }
                            
                            // Use the magnitudes already computed by the service instead of re-calculating
                            val latestMags = s.latestMagnitudes
                            if (latestMags != null && latestMags.size == 512) {
                                // We still need to pass new arrays to StateFlow to trigger recomposition if it uses reference equality
                                // But we can at least avoid the manual loop and division
                                viewModel.setFftState(latestMags, latestMags) // Using same for both to reduce work if raw isn't strictly needed
                            }
                        }
                        delay(16.milliseconds) // Target 60fps for smoother UI
                    }
                } else {
                    viewModel.setFftStateEmpty()
                    viewModel.setVisualizerState(floatArrayOf())
                }
            }

            GlyphixTheme(
                themeName = selectedTheme,
                fontName = selectedFont,
                bananaMode = bananaMode,
                penisMode = penisMode,
                musicPrimaryColor = musicThemeColor,
            ) {
                val isMonsterTheme = selectedTheme.startsWith("Monster")

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    GlyphixBackground()

                    if (isMonsterTheme && !bananaMode && !penisMode) {
                        val clawFilter = if (selectedTheme == "Monster Ultra White") {
                            androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF888888))
                        } else {
                            null // Для Monster Classic рендерим оригинальные цвета картинки без фильтра
                        }

                        Image(
                            painter = painterResource(R.drawable.ic_monster_claw),
                            contentDescription = null,
                            colorFilter = clawFilter,
                            modifier = Modifier
                                .fillMaxSize(0.85f)
                                .align(Alignment.Center),
                            contentScale = ContentScale.Fit
                        )
                    }

                    GlyphixApp(
                        viewModel = viewModel,
                        onToggleVisualizer = { toggleVisualizer() },
                        onSelectCaptureSource = { source ->
                            viewModel.setCaptureSource(source)
                            val s = service
                            if (s != null && s.isVisualizerRunning) {
                                s.stopVisualizer()
                            }
                            startSourceCapture(source)
                        },
                        onGoogleSignIn = { launchGoogleSignIn() },
                        onOverlayPermissionRequest = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                            overlayPermissionLauncher.launch(intent)
                        }
                    )

                    // Overlays
                    MainOverlays(
                        viewModel = viewModel,
                        selectedDevice = viewModel.selectedDevice.collectAsState().value,
                        onGoogleSignIn = { launchGoogleSignIn() }
                    )
                    CommunityOverlays(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "glyphix" && uri.host == "spotify-callback") {
                viewModel.spotifyAuthManager.handleAuthCallback(uri) { success ->
                    if (success) {
                        Toast.makeText(this, "Spotify connected successfully!", Toast.LENGTH_SHORT).show()
                        viewModel.selectTab(Tab.Spotify)
                    }
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }

    private fun toggleVisualizer() {
        val s = service ?: return
        if (s.isVisualizerRunning) {
            s.stopVisualizer()
        } else {
            startSourceCapture(viewModel.captureSource.value)
        }
    }

    private fun startSourceCapture(source: AudioCaptureService.CaptureSource) {
        val s = service
        val intent = Intent(this, AudioCaptureService::class.java)
        startForegroundService(intent)

        when (source) {
            AudioCaptureService.CaptureSource.INTERNAL -> launchProjection()
            AudioCaptureService.CaptureSource.MIC -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    s?.startMicCapture() ?: pendingVisualizerStartAction(source)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            AudioCaptureService.CaptureSource.VIZUALIZER -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    s?.startVizualizerCapture() ?: pendingVisualizerStartAction(source)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            AudioCaptureService.CaptureSource.SPOTIFY -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    s?.startSpotifyCapture() ?: pendingVisualizerStartAction(source)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    private fun pendingVisualizerStartAction(source: AudioCaptureService.CaptureSource) {
        pendingVisualizerStart = true
    }

    private fun launchProjection() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun deliverProjectionToken(resultCode: Int, data: Intent) {
        val s = service
        if (s != null) {
            s.startCapture(resultCode, data)
        } else {
            pendingResultCode = resultCode
            pendingData = data
            hasPendingToken = true
            pendingVisualizerStart = true
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (_: Exception) {}

            val intent = Intent(this, AudioCaptureService::class.java)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun applyServiceSettings() {
        service?.let {
            it.setDevice(viewModel.selectedDevice.value)
            it.setCaptureSource(viewModel.captureSource.value)
            it.setLatencyMs(viewModel.latencyMs.value)
            it.setGamma(viewModel.gammaValue.value)
            it.setSpectrumGain(viewModel.spectrumGain.value)
            it.setMaxBrightness(viewModel.maxBrightness.value)
            it.setSelectedPreset(viewModel.selectedPreset.value)
            it.setHapticMotorEnabled(viewModel.hapticMotorEnabled.value)
            it.setHapticMode(viewModel.hapticMode.value)
            it.setFlashlightEnabled(viewModel.flashlightEnabled.value)
            viewModel.setFlashlightIntensityLevels(it.flashlightIntensityLevels)
            it.setIdleBreathingEnabled(viewModel.idleBreathingEnabled.value)
            it.setIdlePattern(viewModel.idlePattern.value)
            it.setStrobeEnabled(viewModel.strobeEnabled.value)
            it.setDisableGlyphsWhenSilent(viewModel.disableGlyphsWhenSilent.value)
            it.setLensVisualizerEnabled(viewModel.lensVisualizerEnabled.value)
            it.setLensVisualizerRadius(viewModel.lensVisualizerRadius.value)
            it.setLensVisualizerX(viewModel.lensVisualizerX.value)
            it.setLensVisualizerY(viewModel.lensVisualizerY.value)
            it.setLensVisualizerBarWidth(viewModel.lensVisualizerBarWidth.value)
            it.setLensVisualizerMaxHeight(viewModel.lensVisualizerMaxHeight.value)
            it.setLensVisualizerBarCount(viewModel.lensVisualizerBarCount.value)
            it.setLensVisualizerSensitivity(viewModel.lensVisualizerSensitivity.value)

            it.setOverlayEnabled(viewModel.overlayEnabled.value)
            it.setOverlayTopEnabled(viewModel.overlayTopEnabled.value)
            it.setOverlayBottomEnabled(viewModel.overlayBottomEnabled.value)
            it.setOverlayWidth(viewModel.overlayWidth.value)
            it.setOverlayHeight(viewModel.overlayHeight.value)
            it.setOverlayHeightBottom(viewModel.overlayHeightBottom.value)
            it.setOverlayYOffset(viewModel.overlayYOffset.value)
            it.setOverlaySensitivity(viewModel.overlaySensitivity.value)
            it.setOverlaySensitivityBottom(viewModel.overlaySensitivityBottom.value)

            it.setEdgeVisualizerEnabled(viewModel.edgeVisualizerEnabled.value)
            it.setEdgeThickness(viewModel.edgeThickness.value)
            it.setEdgeSensitivity(viewModel.edgeSensitivity.value)
            it.setEdgeBarCounts(viewModel.edgeBarCountHoriz.value, viewModel.edgeBarCountVert.value)
            it.setEdgeCornerRadius(viewModel.edgeCornerRadius.value)
            it.setEdgeTopEnabled(viewModel.edgeTopEnabled.value)
            it.setEdgeBottomEnabled(viewModel.edgeBottomEnabled.value)
        }
    }

    private fun refreshConnectedAudioRoute() {
        val route = resolvePreferredAudioRoute()
        if (route != null) {
            serviceStatic?.setAudioRoute(route)
            if (viewModel.autoDeviceMemorize.value) {
                viewModel.reloadLatencyForCurrentRoute()
            }
        }
    }

    private fun resolvePreferredAudioRoute(): AudioRoute? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var preferred: AudioDeviceInfo? = null
        for (device in outputs) {
            if (device.isBluetoothOutput()) {
                preferred = device
                break
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.isWiredOutput()) {
                    preferred = device
                    break
                }
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    preferred = device
                    break
                }
            }
        }
        return preferred?.toAudioRoute()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        musicThemeHandler.onDestroy()
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (isNotificationServiceEnabled()) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(musicThemeHandler.sessionsChangedListener)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to remove sessions listener: ${e.message}")
            }
        }
    }
}

fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        } else {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    } else {
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }
}

fun AudioDeviceInfo.isWiredOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET
}

fun AudioDeviceInfo.toAudioRoute(): AudioRoute {
    val name = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "Internal Speaker" else productName.toString()
    return AudioRoute(type.toString() + "_" + name, name)
}

val HeavyEasingSpec = tween<Float>(durationMillis = 600)

@Composable
internal fun GlyphixApp(
    viewModel: MainViewModel,
    onToggleVisualizer: () -> Unit,
    onSelectCaptureSource: (AudioCaptureService.CaptureSource) -> Unit,
    onGoogleSignIn: () -> Unit,
    onOverlayPermissionRequest: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
    val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val visibleTabs = remember(selectedDevice) {
        var tabs = if (selectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            Tab.entries.filter { it != Tab.Glyphs }
        } else {
            Tab.entries.toList()
        }
        if (!viewModel.hasHapticMotor) {
            tabs = tabs.filter { it != Tab.Haptics }
        }
        if (!viewModel.hasFlashlight) {
            tabs = tabs.filter { it != Tab.Flashlight }
        }
        tabs
    }

    val pagerState = rememberPagerState(initialPage = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)) { visibleTabs.size }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        val target = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            isProgrammaticScroll = true
            try {
                val steps = (target - pagerState.currentPage).absoluteValue
                val duration = (350 + (steps - 1) * 80).coerceAtMost(700)

                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = EaseOutCubic
                    )
                )
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page < visibleTabs.size) {
                val tab = visibleTabs[page]
                if (!isProgrammaticScroll && viewModel.selectedTab.value != tab) {
                    viewModel.selectTab(tab)
                }
            }
        }
    }

    val isGlass = LocalIsGlassTheme.current
    val bananaMode = LocalBananaMode.current
    val penisMode = LocalPenisMode.current
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val isMonster = selectedTheme.startsWith("Monster")
    val isFabMenuExpanded by viewModel.isFabMenuExpanded.collectAsStateWithLifecycle()
    val isHamburgerMenuOpen by viewModel.isHamburgerMenuOpen.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()

    val screenTitle = when (selectedTab) {
        Tab.Audio -> "Glyphix"
        Tab.Glyphs -> "Glyphs"
        Tab.Spotify -> "Spotify"
        Tab.Visuals -> "Visuals"
        Tab.Haptics -> "Haptics"
        Tab.Flashlight -> "Torch"
        Tab.Settings -> "Settings"
    }

    val pagePadding = PaddingValues(bottom = 100.dp, top = 6.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar Container: Top Bar + seamlessly attached Hamburger Dropdown Menu
            Box(modifier = Modifier.fillMaxWidth()) {
            FloatingTopBar(
                title = screenTitle,
                onMenuClick = { viewModel.toggleHamburgerMenu() },
                onProfileClick = { viewModel.showProfile() },
                avatarUrl = userProfile?.profilePictureUrl,
                isProfileActive = false
            )

            // Quick Configuration Dropdown Menu (Assets/Hamburger Menu.png)
            HamburgerDropdownMenu(
                isOpen = isHamburgerMenuOpen,
                onDismiss = { viewModel.setHamburgerMenuOpen(false) },
                onSelectGlyphs = { viewModel.selectTab(Tab.Glyphs) },
                onSelectSpotify = { viewModel.selectTab(Tab.Spotify) },
                onSelectHaptics = { viewModel.selectTab(Tab.Haptics) },
                onSelectOverlays = { viewModel.selectTab(Tab.Visuals) },
                onSelectTorch = { viewModel.selectTab(Tab.Flashlight) }
            )
        }

        // Main Page Box: HorizontalPager fills full area; FloatingBottomBar OVERLAPS at bottom
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = visibleTabs.size,
                userScrollEnabled = true
            ) { page ->
            if (page >= visibleTabs.size) return@HorizontalPager
            val tab = visibleTabs[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val absOffset = pageOffset.coerceIn(-1f, 1f).let { kotlin.math.abs(it) }
                        val fraction = 1f - absOffset

                        val scale = 0.85f + (1f - 0.85f) * fraction
                        scaleX = scale
                        scaleY = scale
                        alpha = fraction

                        val maxRotation = 8f
                        val rotationAmount = maxRotation * (1f - fraction)

                        rotationZ = if (pageOffset > 0) -rotationAmount else rotationAmount
                    }
            ) {
                when (tab) {
                    Tab.Audio -> {
                        val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
                        val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                        val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
                        val fftData by viewModel.fftState.collectAsStateWithLifecycle()
                        val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                        val latencyWizardState by viewModel.latencyWizardState.collectAsStateWithLifecycle()
                        val bananaMode by viewModel.bananaModeEnabled.collectAsStateWithLifecycle()
                        val penisMode by viewModel.penisModeEnabled.collectAsStateWithLifecycle()
                        val spotifyPlaybackState by viewModel.spotifyRepository.playbackState.collectAsStateWithLifecycle()

                        AudioScreen(
                            isRunning = isRunning,
                            sessionDuration = totalVisualizedTime,
                            latencyMs = latencyMs,
                            onLatencyChanged = { viewModel.setLatencyMs(it) },
                            latencyPresets = latencyPresets,
                            onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
                            autoDeviceEnabled = autoDeviceEnabled,
                            onAutoDeviceToggle = { viewModel.setAutoDeviceMemorize(it) },
                            connectedDeviceName = MainActivity.serviceStatic?.getActiveAudioRouteName()
                                ?: "Unknown",
                            fftData = fftData,
                            captureSource = captureSource,
                            onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                            latencyWizardState = latencyWizardState,
                            onRunLatencyWizard = { viewModel.runLatencyWizard() },
                            onResetLatencyWizard = { viewModel.resetLatencyWizard() },
                            bananaMode = bananaMode,
                            penisMode = penisMode,
                            spotifyPlaybackState = spotifyPlaybackState,
                            onSpotifyTogglePlay = { viewModel.spotifyRepository.togglePlayPause() },
                            onSpotifyNext = { viewModel.spotifyRepository.skipNext() },
                            onSpotifyPrevious = { viewModel.spotifyRepository.skipPrevious() },
                            onSpotifySeek = { viewModel.spotifyRepository.seekTo(it) },
                            onSpotifyToggleShuffle = { viewModel.spotifyRepository.toggleShuffle() },
                            onSpotifyToggleRepeat = { viewModel.spotifyRepository.toggleRepeat() },
                            onOpenSpotifyTab = {
                                viewModel.selectTab(Tab.Spotify)
                            },
                            padding = pagePadding
                        )
                    }
                    Tab.Glyphs -> {
                        val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
                        val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
                        val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
                        val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
                        val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

                        GlyphsScreen(
                            gammaValue = gammaValue,
                            onGammaChanged = {
                                viewModel.setGammaValue(it); viewModel.persistGamma(
                                it
                            )
                            },
                            maxBrightness = maxBrightness,
                            onMaxBrightnessChanged = { viewModel.setMaxBrightness(it) },
                            presets = presets,
                            selectedPreset = selectedPreset,
                            onPresetSelected = { viewModel.setSelectedPreset(it) },
                            isRunning = isRunning,
                            selectedDevice = selectedDevice,
                            viewModel = viewModel,
                            padding = pagePadding
                        )
                    }
                    Tab.Spotify -> {
                        SpotifyScreen(
                            spotifyRepo = viewModel.spotifyRepository,
                            authManager = viewModel.spotifyAuthManager,
                            onStartVisualizer = {
                                if (!isRunning) {
                                    onToggleVisualizer()
                                }
                            },
                            onActivateSpotifyInput = {
                                viewModel.setCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
                                viewModel.setSpotifyInputActive(true)
                            },
                            modifier = Modifier.padding(pagePadding)
                        )
                    }
                    Tab.Visuals -> {
                        val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
                        VisualsScreen(
                            viewModel = viewModel,
                            overlayEnabled = overlayEnabled,
                            onOverlayEnabledChanged = { viewModel.setOverlayEnabled(it) },
                            onOverlayPermissionRequest = { onOverlayPermissionRequest() },
                            padding = pagePadding
                        )
                    }
                    Tab.Haptics -> {
                        val hapticMotorEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
                        val hapticMode by viewModel.hapticMode.collectAsStateWithLifecycle()
                        val hapticFreqMin by viewModel.hapticFreqMin.collectAsStateWithLifecycle()
                        val hapticFreqMax by viewModel.hapticFreqMax.collectAsStateWithLifecycle()
                        val hapticMultiplier by viewModel.hapticMultiplier.collectAsStateWithLifecycle()
                        val hapticAudioGain by viewModel.hapticAudioGain.collectAsStateWithLifecycle()
                        val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
                        val hapticBeatSensitivity by viewModel.hapticBeatSensitivity.collectAsStateWithLifecycle()
                        val hapticBeatGamma by viewModel.hapticBeatGamma.collectAsStateWithLifecycle()
                        val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()

                        HapticsScreen(
                            hapticMotorEnabled = hapticMotorEnabled,
                            onHapticMotorEnabledChanged = {
                                viewModel.setHapticMotorEnabled(
                                    it
                                )
                            },
                            hapticMode = hapticMode,
                            onHapticModeChanged = { viewModel.setHapticMode(it) },
                            hapticFreqMin = hapticFreqMin,
                            hapticFreqMax = hapticFreqMax,
                            onHapticFreqRangeChanged = { min, max ->
                                viewModel.setHapticFreqRange(
                                    min,
                                    max
                                )
                            },
                            hapticMultiplier = hapticMultiplier,
                            onHapticMultiplierChanged = { viewModel.setHapticMultiplier(it) },
                            hapticAudioGain = hapticAudioGain,
                            onHapticAudioGainChanged = { viewModel.setHapticAudioGain(it) },
                            hapticGamma = hapticGamma,
                            onHapticGammaChanged = { viewModel.setHapticGamma(it) },
                            hapticBeatSensitivity = hapticBeatSensitivity,
                            onHapticBeatSensitivityChanged = {
                                viewModel.setHapticBeatSensitivity(
                                    it
                                )
                            },
                            hapticBeatGamma = hapticBeatGamma,
                            onHapticBeatGammaChanged = { viewModel.setHapticBeatGamma(it) },
                            hapticAmplitudeProvider = { viewModel.hapticAmplitude.value },
                            isBeatDetectedProvider = { isBeatDetected },
                            padding = pagePadding
                        )
                    }
                    Tab.Flashlight -> {
                        val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
                        val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
                        val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
                        val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
                        val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
                        val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
                        val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
                        val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
                        val flashlightLevel by viewModel.flashlightLevel.collectAsStateWithLifecycle()
                        val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()

                        FlashlightScreen(
                            flashlightEnabled = flashlightEnabled,
                            onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                            flashlightMode = flashlightMode,
                            onFlashlightModeChanged = { viewModel.setFlashlightMode(it) },
                            flashlightFreqMin = flashlightFreqMin,
                            flashlightFreqMax = flashlightFreqMax,
                            onFlashlightFreqRangeChanged = { min, max ->
                                viewModel.setFlashlightFreqRange(
                                    min,
                                    max
                                )
                            },
                            flashlightThreshold = flashlightThreshold,
                            onFlashlightThresholdChanged = {
                                viewModel.setFlashlightThreshold(
                                    it
                                )
                            },
                            flashlightSpeedMs = flashlightSpeedMs,
                            onFlashlightSpeedMsChanged = { viewModel.setFlashlightSpeedMs(it) },
                            flashlightBeatSensitivity = flashlightBeatSensitivity,
                            onFlashlightBeatSensitivityChanged = {
                                viewModel.setFlashlightBeatSensitivity(
                                    it
                                )
                            },
                            flashlightIntensityLevels = flashlightIntensityLevels,
                            flashlightCurrentLevel = flashlightLevel,
                            flashlightAmplitudeProvider = { viewModel.flashlightAmplitude.value },
                            isBeatDetectedProvider = { isFlashlightBeatDetected },
                            padding = pagePadding
                        )
                    }
                    Tab.Settings -> {
                        val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                        val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
                        val strobeEnabled by viewModel.strobeEnabled.collectAsStateWithLifecycle()
                        val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()

                        SettingsScreen(
                            viewModel = viewModel,
                            idleBreathingEnabled = idleBreathingEnabled,
                            onIdleBreathingEnabledChanged = {
                                viewModel.setIdleBreathingEnabled(
                                    it
                                )
                            },
                            idlePattern = idlePattern,
                            onIdlePatternChanged = { viewModel.setIdlePattern(it) },
                            strobeEnabled = strobeEnabled,
                            onStrobeEnabledChanged = { viewModel.setStrobeEnabled(it) },
                            disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                            onDisableGlyphsWhenSilentChanged = {
                                viewModel.setDisableGlyphsWhenSilent(
                                    it
                                )
                            },
                            onGoogleSignIn = onGoogleSignIn,
                            padding = pagePadding,
                            onOpenProfile = { viewModel.showProfile() },
                            onOpenMenu = { viewModel.toggleHamburgerMenu() }
                        )
                    }
                }
            }
        }
    }
}

        // Floating Bottom Navigation Bar (Floats directly over pages with no reserved space)
        FloatingBottomBar(
            selectedTab = selectedTab,
            onTabSelected = { viewModel.selectTab(it) },
            isRunning = isRunning,
            onToggleVisualizer = onToggleVisualizer,
            isFabMenuExpanded = isFabMenuExpanded,
            onToggleFabMenu = { viewModel.toggleFabMenu() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // Speed-Dial Capture Source Menu (Assets/FAB menu.png)
        SpeedDialFabMenu(
            isExpanded = isFabMenuExpanded,
            currentSource = captureSource,
            onSelectSource = { source ->
                viewModel.setSpotifyInputActive(false)
                viewModel.setFabMenuExpanded(false)
                onSelectCaptureSource(source)
            },
            onSelectSpotifyInput = {
                viewModel.setSpotifyInputActive(true)
                viewModel.setFabMenuExpanded(false)
                viewModel.selectTab(Tab.Spotify)
                viewModel.spotifyRepository.refreshAllData()
                onSelectCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
            },
            onDismiss = { viewModel.setFabMenuExpanded(false) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
        )
    }
}
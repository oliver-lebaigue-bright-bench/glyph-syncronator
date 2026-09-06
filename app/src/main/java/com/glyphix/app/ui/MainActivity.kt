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
import com.glyphix.app.ui.SecondaryScreens.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
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

            // Restore PC Stream settings
            service?.let { s ->
                viewModel.setPcStreamingActive(s.pcStreamEnabled)
                val savedIp = s.pcStreamTargetIp
                if (!savedIp.isNullOrEmpty()) {
                    viewModel.setPcCompanionIp(savedIp)
                }
            }

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
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                viewModel.linkWithCredential(credential)
            } else {
                Log.e("MainActivity", "Google sign in returned null idToken")
                Toast.makeText(this, "Google sign in failed: no token returned", Toast.LENGTH_SHORT).show()
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            Log.e("MainActivity", "Google sign in failed with status: ${e.statusCode}", e)
            val msg = when (e.statusCode) {
                10 -> "Configuration error (Status 10 - Check SHA-1 key in Firebase Console)"
                12500 -> "Sign in failed (Status 12500)"
                7 -> "Network error (Status 7)"
                else -> "Google sign in failed (${e.statusCode}): ${e.message}"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Google sign in failed", e)
            Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
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

        intent?.data?.let { uri ->
            if (uri.scheme == "glyphix" && uri.host == "spotify-callback") {
                viewModel.spotifyAuthManager.handleAuthCallback(uri)
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

                            // Collect network diagnostic if applicable
                            if (s.getCaptureSource() == AudioCaptureService.CaptureSource.NETWORK || s.getCaptureSource() == AudioCaptureService.CaptureSource.BLUETOOTH) {
                                viewModel.setNetworkPacketsReceived(s.networkPacketsReceivedFlow().value)
                            }

                            if (s.getCaptureSource() == AudioCaptureService.CaptureSource.BLUETOOTH) {
                                viewModel.setBluetoothDeviceName(s.bluetoothDeviceNameFlow().value)
                                viewModel.setBluetoothDeviceAddress(s.bluetoothDeviceAddressFlow().value)
                            }

                            viewModel.setPcPacketsSent(AudioCaptureService.sPcPacketsSent.value)
                            
                            // Use the magnitudes already computed by the service instead of re-calculating
                            val latestMags = s.latestMagnitudes
                            if (latestMags != null && latestMags.size == 512) {
                                viewModel.setFftState(latestMags, latestMags) 
                            }
                        }
                        delay(16.milliseconds) 
                    }
                } else {
                    viewModel.setFftStateEmpty()
                    viewModel.setVisualizerState(floatArrayOf())
                    viewModel.setNetworkPacketsReceived(0)
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
                        onGoogleSignIn = { launchGoogleSignIn() },
                        onSwitchCaptureSource = { switchCaptureSource(it) }
                    )

                    MainOverlays(
                        viewModel = viewModel,
                        selectedDevice = viewModel.selectedDevice.collectAsState().value,
                        onGoogleSignIn = { launchGoogleSignIn() },
                        onOverlayPermissionRequest = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                            overlayPermissionLauncher.launch(intent)
                        }
                    )
                    CommunityOverlays(viewModel = viewModel)
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
            val intent = Intent(this, AudioCaptureService::class.java)
            startForegroundService(intent)

            val source = viewModel.captureSource.value
            s.setCaptureSource(source)
            when (source) {
                AudioCaptureService.CaptureSource.INTERNAL -> launchProjection()
                AudioCaptureService.CaptureSource.MIC -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        s.startVisualizer()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                AudioCaptureService.CaptureSource.VIZUALIZER -> s.startVisualizer()
                AudioCaptureService.CaptureSource.SPOTIFY -> s.startVisualizer()
                AudioCaptureService.CaptureSource.NETWORK -> s.startVisualizer()
                AudioCaptureService.CaptureSource.BLUETOOTH -> s.startVisualizer()
            }
        }
    }

    private fun switchCaptureSource(source: AudioCaptureService.CaptureSource) {
        viewModel.setCaptureSource(source)
        val s = service
        if (s != null && s.isVisualizerRunning) {
            when (source) {
                AudioCaptureService.CaptureSource.INTERNAL -> launchProjection()
                AudioCaptureService.CaptureSource.MIC -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        s.setCaptureSource(source)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                else -> s.setCaptureSource(source)
            }
        } else {
            toggleVisualizer()
        }
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

            it.setPcStreamEnabled(viewModel.isPcStreamingActive.value)
            if (viewModel.pcCompanionIp.value.isNotEmpty()) {
                it.setPcStreamTargetIp(viewModel.pcCompanionIp.value)
            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "glyphix" && uri.host == "spotify-callback") {
                viewModel.spotifyAuthManager.handleAuthCallback(uri)
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
    onGoogleSignIn: () -> Unit,
    onSwitchCaptureSource: (AudioCaptureService.CaptureSource) -> Unit = {}
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
    val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val visibleTabs = remember {
        listOf(
            Tab.Audio,
            Tab.Leaderboard,
            Tab.Info,
            Tab.Settings
        )
    }

    val pagerState = rememberPagerState(initialPage = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)) { visibleTabs.size }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, visibleTabs) {
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

    LaunchedEffect(pagerState, visibleTabs) {
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
    val developerModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()

    val screenTitle = when (selectedTab) {
        Tab.Audio -> "Glyphix"
        Tab.Leaderboard -> "Leaderboard"
        Tab.Spotify -> "Spotify"
        Tab.Glyphs -> "Glyphs"
        Tab.Info -> "Info"
        Tab.Haptics -> "Haptics"
        Tab.Flashlight -> "Torch"
        Tab.Settings -> "Settings"
    }

    val pagePadding = PaddingValues(bottom = 100.dp, top = 6.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Bar Container: Top Bar + seamlessly attached Hamburger Dropdown Menu
        Box(modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            FloatingTopBar(
                title = screenTitle,
                onMenuClick = { viewModel.toggleHamburgerMenu() },
                onProfileClick = { viewModel.showProfile() },
                avatarUrl = userProfile?.profilePictureUrl,
                isProfileActive = false,
                onTitleLongClick = if (selectedTab == Tab.Settings) {
                    {
                        val newState = !developerModeEnabled
                        viewModel.setDeveloperModeEnabled(newState)
                        Toast.makeText(
                            context,
                            if (newState) "Developer mode enabled" else "Developer mode disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else null
            )

            // Quick Configuration Dropdown Menu (Assets/Hamburger Menu.png)
            HamburgerDropdownMenu(
                isOpen = isHamburgerMenuOpen,
                onDismiss = { viewModel.setHamburgerMenuOpen(false) },
                onSelectGlyphs = { viewModel.showGlyphs() },
                onSelectSpotify = { viewModel.showSpotify() },
                onSelectHaptics = { viewModel.showHaptics() },
                onSelectOverlays = { viewModel.showVisuals() },
                onSelectTorch = { viewModel.showFlashlight() }
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

                        val scale = 0.82f + (1f - 0.82f) * fraction
                        scaleX = scale
                        scaleY = scale
                        alpha = fraction.pow(1.5f) // Softer fade

                        val maxRotation = 10f
                        val rotationAmount = maxRotation * (1f - fraction)
                        rotationZ = if (pageOffset > 0) -rotationAmount else rotationAmount
                        
                        translationY = 50f * (1f - fraction)
                    }
            ) {
                when (tab) {
                    Tab.Audio -> {
                        val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
                        val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                        val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
                        val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                        val latencyWizardState by viewModel.latencyWizardState.collectAsStateWithLifecycle()
                        val bananaMode by viewModel.bananaModeEnabled.collectAsStateWithLifecycle()
                        val penisMode by viewModel.penisModeEnabled.collectAsStateWithLifecycle()
                        val spotifyPlaybackState by viewModel.spotifyRepository.playbackState.collectAsStateWithLifecycle()
                        val networkPacketsReceived by viewModel.networkPacketsReceived.collectAsStateWithLifecycle()
                        val bluetoothDeviceName by viewModel.bluetoothDeviceName.collectAsStateWithLifecycle()
                        val bluetoothDeviceAddress by viewModel.bluetoothDeviceAddress.collectAsStateWithLifecycle()
                        val pcPacketsSent by viewModel.pcPacketsSent.collectAsStateWithLifecycle()
                        val desktopSyncDirection by viewModel.desktopSyncDirection.collectAsStateWithLifecycle()
                        val pcCompanionIp by viewModel.pcCompanionIp.collectAsStateWithLifecycle()
                        val isPcStreamingActive by viewModel.isPcStreamingActive.collectAsStateWithLifecycle()

                        val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
                        val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
                        val vizState = viewModel.visualizerState.collectAsStateWithLifecycle()

                        val fftDataState = viewModel.fftState.collectAsStateWithLifecycle()
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
                            fftData = { fftDataState.value },
                            captureSource = captureSource,
                            onCaptureSourceChanged = { onSwitchCaptureSource(it) },
                            networkPacketsReceived = networkPacketsReceived,
                            bluetoothDeviceName = bluetoothDeviceName,
                            bluetoothDeviceAddress = bluetoothDeviceAddress,
                            pcPacketsSent = pcPacketsSent,
                            desktopSyncDirection = desktopSyncDirection,
                            pcCompanionIp = pcCompanionIp,
                            isPcStreamingActive = isPcStreamingActive,
                            onTogglePcStream = { enabled ->
                                viewModel.setPcStreamingActive(enabled)
                                MainActivity.serviceStatic?.setPcStreamEnabled(enabled)
                                if (enabled && !isRunning) {
                                    onToggleVisualizer()
                                }
                            },
                            onPcIpChanged = { ip ->
                                viewModel.setPcCompanionIp(ip)
                                MainActivity.serviceStatic?.setPcStreamTargetIp(ip)
                            },
                            onDiscoverPc = {
                                MainActivity.serviceStatic?.discoverPcCompanion { ip ->
                                    viewModel.setPcCompanionIp(ip)
                                    MainActivity.serviceStatic?.setPcStreamTargetIp(ip)
                                    Toast.makeText(context, "Found PC Companion at $ip", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSyncDirectionChanged = { direction ->
                                viewModel.setDesktopSyncDirection(direction)
                                if (direction == "PC_TO_PHONE") {
                                    onSwitchCaptureSource(AudioCaptureService.CaptureSource.NETWORK)
                                } else {
                                    if (captureSource == AudioCaptureService.CaptureSource.NETWORK || captureSource == AudioCaptureService.CaptureSource.BLUETOOTH) {
                                        onSwitchCaptureSource(AudioCaptureService.CaptureSource.INTERNAL)
                                    }
                                }
                            },
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
                            onOpenSpotifyTab = { viewModel.showSpotify() },
                            onToggleVisualizer = onToggleVisualizer,
                            viewModel = viewModel,
                            selectedDevice = selectedDevice,
                            vizStateProvider = { vizState.value },
                            presets = presets,
                            selectedPreset = selectedPreset,
                            onPresetSelected = { viewModel.setSelectedPreset(it) },
                            padding = pagePadding
                        )
                    }
                    Tab.Spotify -> {
                        SpotifyScreen(
                            spotifyRepo = viewModel.spotifyRepository,
                            authManager = viewModel.spotifyAuthManager,
                            onStartVisualizer = {
                                viewModel.setCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
                                MainActivity.serviceStatic?.startCapture(0, null)
                            },
                            onActivateSpotifyInput = {
                                viewModel.setCaptureSource(AudioCaptureService.CaptureSource.SPOTIFY)
                            },
                            onDismiss = null,
                            modifier = Modifier.padding(paddingValues = pagePadding)
                        )
                    }
                    Tab.Leaderboard -> {
                        val leaderboardEntries by viewModel.leaderboardEntries.collectAsStateWithLifecycle()
                        LeaderboardScreen(
                            entries = leaderboardEntries,
                            onDismiss = { viewModel.navigateBack() },
                            showTopBar = false,
                            modifier = Modifier.padding(paddingValues = pagePadding)
                        )
                    }
                    Tab.Info -> {
                        AboutScreen(
                            viewModel = viewModel,
                            onDismiss = null
                        )
                    }
                    Tab.Settings -> {
                        val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                        val aodEnabled by viewModel.aodEnabled.collectAsStateWithLifecycle()
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
                    else -> {}
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
            isRunning = isRunning,
            onToggleVisualizer = onToggleVisualizer,
            currentSource = captureSource,
            onSelectSource = { source ->
                onSwitchCaptureSource(source)
                viewModel.setFabMenuExpanded(false)
            },
            onDismiss = { viewModel.setFabMenuExpanded(false) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
        )
    }
}
}
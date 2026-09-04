package com.glyphix.app.ui.PrimaryScreens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.spotify.*
import com.glyphix.app.ui.*
import java.util.Locale

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDarkGreen = Color(0xFF14833B)
private val SpotifyBlack = Color(0xFF121212)
private val SpotifyElevated = Color(0xFF242424)

/**
 * Full-fledged Spotify Hub with OAuth 2.0 PKCE authentication,
 * Playlists, Search, and on-demand Playback controls.
 */
@Composable
fun SpotifyScreen(
    spotifyRepo: SpotifyRepository,
    authManager: SpotifyAuthManager,
    onStartVisualizer: () -> Unit = {},
    onActivateSpotifyInput: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onDismiss != null) {
        androidx.activity.compose.BackHandler { onDismiss() }
    }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isGlass = LocalIsGlassTheme.current

    val isLoggedIn by authManager.isLoggedIn.collectAsState()
    val isAuthenticating by authManager.isAuthenticating.collectAsState()
    val authError by authManager.authError.collectAsState()

    val userProfile by spotifyRepo.userProfile.collectAsState()
    val playlists by spotifyRepo.playlists.collectAsState()
    val recentlyPlayed by spotifyRepo.recentlyPlayed.collectAsState()
    val topTracks by spotifyRepo.topTracks.collectAsState()
    val playbackState by spotifyRepo.playbackState.collectAsState()
    val searchTracks by spotifyRepo.searchTracks.collectAsState()
    val searchPlaylists by spotifyRepo.searchPlaylists.collectAsState()
    val selectedPlaylist by spotifyRepo.selectedPlaylist.collectAsState()
    val selectedPlaylistTracks by spotifyRepo.selectedPlaylistTracks.collectAsState()
    val isLoading by spotifyRepo.isLoading.collectAsState()
    val isSearching by spotifyRepo.isSearching.collectAsState()
    val isPlaylistLoading by spotifyRepo.isPlaylistLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showClientIdDialog by remember { mutableStateOf(false) }

    val onPlayAnyTrack: (String) -> Unit = { trackUri ->
        onActivateSpotifyInput()
        onStartVisualizer()
        spotifyRepo.playTrack(trackUri)
    }

    val onPlayAnyPlaylist: (String, String?) -> Unit = { playlistUri, trackUri ->
        onActivateSpotifyInput()
        onStartVisualizer()
        spotifyRepo.playPlaylist(playlistUri, trackUri)
    }

    LaunchedEffect(authError) {
        authError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = LocalAppSpacing.current.edge),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onDismiss != null) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphixBackButton(onClick = onDismiss)
                Spacer(modifier = Modifier.width(16.dp))
                ScreenTitle(text = "Spotify", modifier = Modifier.padding(bottom = 0.dp))
            }
        }

        if (!isLoggedIn) {
            // Logged Out Hero Card
            SpotifyConnectHeroCard(
                isAuthenticating = isAuthenticating,
                onConnectClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    authManager.startAuthentication(context)
                },
                onCustomClientIdClick = { showClientIdDialog = true }
            )
        } else {
            // Logged In Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 1. User Profile Header & Status
                item {
                    SpotifyUserHeaderCard(
                        profile = userProfile,
                        onRefresh = { spotifyRepo.refreshAllData() },
                        onLogout = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            authManager.logout()
                        }
                    )
                }

                // 2. Active Now Playing Player Card
                item {
                    SpotifyNowPlayingCard(
                        playbackState = playbackState,
                        onTogglePlay = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onActivateSpotifyInput()
                            onStartVisualizer()
                            spotifyRepo.togglePlayPause()
                        },
                        onNext = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onActivateSpotifyInput()
                            spotifyRepo.skipNext()
                        },
                        onPrevious = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onActivateSpotifyInput()
                            spotifyRepo.skipPrevious()
                        },
                        onSeek = { posMs ->
                            spotifyRepo.seekTo(posMs)
                        },
                        onToggleShuffle = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            spotifyRepo.toggleShuffle()
                        },
                        onToggleRepeat = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            spotifyRepo.toggleRepeat()
                        },
                        onStartVisualizer = onStartVisualizer
                    )
                }

                // 3. Search Bar
                item {
                    SpotifySearchCard(
                        query = searchQuery,
                        onQueryChange = { newQuery ->
                            searchQuery = newQuery
                            spotifyRepo.search(newQuery, debounceMs = 300L)
                        },
                        onSearchTrigger = { q ->
                            spotifyRepo.search(q, debounceMs = 0L)
                        },
                        onClear = {
                            searchQuery = ""
                            spotifyRepo.search("", debounceMs = 0L)
                        }
                    )
                }

                // 4. Search Results (if searching)
                if (searchQuery.isNotBlank()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Search Results",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = mockupTextColor()
                            )
                            if (isSearching) {
                                CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    if (isSearching && searchTracks.isEmpty() && searchPlaylists.isEmpty()) {
                        item {
                            MockupCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Searching Spotify...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = mockupSubtextColor()
                                    )
                                }
                            }
                        }
                    } else if (searchTracks.isEmpty() && searchPlaylists.isEmpty()) {
                        item {
                            MockupCard {
                                Text(
                                    text = "No matching songs found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = mockupSubtextColor(),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    } else {
                        if (searchPlaylists.isNotEmpty()) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(searchPlaylists) { playlist ->
                                        SpotifyPlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                spotifyRepo.loadPlaylistTracks(playlist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        items(searchTracks) { track ->
                            SpotifyTrackItemCard(
                                track = track,
                                isCurrent = playbackState?.item?.id == track.id,
                                isPlaying = playbackState?.is_playing == true && playbackState?.item?.id == track.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onPlayAnyTrack(track.safeUri)
                                }
                            )
                        }
                    }
                }

                // 5. Selected Playlist Details (if open)
                if (selectedPlaylist != null) {
                    item {
                        SpotifyPlaylistDetailsCard(
                            playlist = selectedPlaylist!!,
                            tracks = selectedPlaylistTracks,
                            isLoading = isPlaylistLoading,
                            currentTrackId = playbackState?.item?.id,
                            isPlaying = playbackState?.is_playing == true,
                            onClose = { spotifyRepo.closePlaylistDetails() },
                            onPlayEntirePlaylist = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onPlayAnyPlaylist(selectedPlaylist!!.safeUri, null)
                            },
                            onOpenClientIdDialog = {
                                showClientIdDialog = true
                            },
                            onPlayTrack = { track ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onPlayAnyPlaylist(selectedPlaylist!!.safeUri, track.safeUri)
                            }
                        )
                    }
                } else if (searchQuery.isBlank()) {
                    // 6. User Playlists Carousel
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Your Playlists (${playlists.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = mockupTextColor()
                            )

                            if (playlists.isEmpty() && isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(32.dp))
                                }
                            } else if (playlists.isEmpty()) {
                                MockupCard {
                                    Text(
                                        text = "No playlists found in your account.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = mockupSubtextColor()
                                    )
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(playlists) { playlist ->
                                        SpotifyPlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                spotifyRepo.loadPlaylistTracks(playlist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. Recently Played Tracks
                    if (recentlyPlayed.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recently Played",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = mockupTextColor(),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(recentlyPlayed.take(8)) { track ->
                            SpotifyTrackItemCard(
                                track = track,
                                isCurrent = playbackState?.item?.id == track.id,
                                isPlaying = playbackState?.is_playing == true && playbackState?.item?.id == track.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onPlayAnyTrack(track.safeUri)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClientIdDialog) {
        SpotifyCustomClientIdDialog(
            currentClientId = authManager.getClientId(),
            onDismiss = { showClientIdDialog = false },
            onSave = { newId ->
                authManager.setClientId(newId)
                showClientIdDialog = false
                if (newId.isNotBlank()) {
                    authManager.startAuthentication(context)
                }
            }
        )
    }
}

/**
 * Logged Out Hero Card
 */
@Composable
private fun SpotifyConnectHeroCard(
    isAuthenticating: Boolean,
    onConnectClick: () -> Unit,
    onCustomClientIdClick: () -> Unit
) {
    val isGlass = LocalIsGlassTheme.current

    MockupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Glowing Spotify Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SpotifyGreen, SpotifyDarkGreen)
                        )
                    )
                    .shadow(12.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Spotify",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Spotify Music & Glyphs",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = mockupTextColor(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Connect your Spotify account to browse playlists, search any song, and trigger beat-synced Nothing Glyphs.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = mockupSubtextColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Features Checklist
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotifyFeatureRow(icon = Icons.AutoMirrored.Outlined.QueueMusic, text = "Access your Library & Playlists")
                SpotifyFeatureRow(icon = Icons.Outlined.Search, text = "On-demand song search & picking")
                SpotifyFeatureRow(icon = Icons.Outlined.GraphicEq, text = "Zero-latency Glyph light synchronization")
            }

            Spacer(Modifier.height(4.dp))

            // Main Connect Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(if (isGlass) 2.dp else 6.dp, RoundedCornerShape(27.dp)),
                shape = RoundedCornerShape(27.dp),
                color = SpotifyGreen,
                onClick = onConnectClick,
                enabled = !isAuthenticating
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Connect with Spotify",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // Developer Client ID setting button
            TextButton(
                onClick = onCustomClientIdClick
            ) {
                Text(
                    text = "Configure Spotify Client ID",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = mockupSubtextColor()
                )
            }
        }
    }
}

@Composable
private fun SpotifyFeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = mockupTextColor()
        )
    }
}

/**
 * User Profile Header Card
 */
@Composable
private fun SpotifyUserHeaderCard(
    profile: SpotifyUserProfile?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    MockupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                val avatarUrl = profile?.images?.firstOrNull()?.url
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile?.displayName ?: "Spotify User",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = mockupTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val isPremium = profile?.product?.equals("premium", ignoreCase = true) == true
                    val planBadge = if (isPremium) "Spotify Premium" else if (profile?.product != null) "Spotify Free" else "Spotify Account"
                    Text(
                        text = planBadge,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = if (isPremium) SpotifyGreen else mockupSubtextColor()
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = mockupSubtextColor(),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Comprehensive Now Playing Card with live controls
 */
@Composable
private fun SpotifyNowPlayingCard(
    playbackState: SpotifyPlaybackState?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onStartVisualizer: () -> Unit
) {
    val track = playbackState?.item
    val isPlaying = playbackState?.is_playing == true
    val isGlass = LocalIsGlassTheme.current

    val currentMs = playbackState?.progress_ms ?: 0L
    val durationMs = track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val progressFraction = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    MockupCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Album Art
                val coverUrl = track?.imageUrl
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Cover",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .shadow(4.dp, RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.DarkGray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title & Artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = track?.name ?: "No Track Playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = mockupTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artistNames ?: "Pick a playlist or song below",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = mockupSubtextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playbackState?.device != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speaker,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = playbackState.device.name ?: "Spotify Device",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = SpotifyGreen
                            )
                        }
                    }
                }
            }

            // Scrubber Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = if (isSeeking) seekFraction else progressFraction,
                    onValueChange = {
                        isSeeking = true
                        seekFraction = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekFraction * durationMs).toLong()
                        onSeek(targetMs)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedCurrentMs = if (isSeeking) (seekFraction * durationMs).toLong() else currentMs
                    Text(
                        text = formatDuration(displayedCurrentMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = mockupSubtextColor()
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = mockupSubtextColor()
                    )
                }
            }

            // Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    val isShuffle = playbackState?.shuffle_state == true
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) SpotifyGreen else mockupSubtextColor().copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Previous
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = mockupTextColor(),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause FAB
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(if (isGlass) 2.dp else 4.dp, CircleShape),
                    shape = CircleShape,
                    color = SpotifyGreen,
                    onClick = onTogglePlay
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Next
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = mockupTextColor(),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    val repeatMode = playbackState?.repeat_state ?: "off"
                    val isRepeat = repeatMode != "off"
                    Icon(
                        imageVector = if (repeatMode == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) SpotifyGreen else mockupSubtextColor().copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Search Card
 */
@Composable
private fun SpotifySearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchTrigger: (String) -> Unit,
    onClear: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    MockupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    onSearchTrigger(query)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(22.dp)
                )
            }

            TextField(
                value = query,
                onValueChange = {
                    onQueryChange(it)
                },
                placeholder = {
                    Text(
                        text = "Search songs, artists, playlists...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mockupSubtextColor().copy(alpha = 0.7f)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onSearchTrigger(query)
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = mockupTextColor(),
                    unfocusedTextColor = mockupTextColor()
                ),
                modifier = Modifier.weight(1f)
            )

            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onClear()
                    keyboardController?.hide()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = mockupSubtextColor(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Playlist Card
 */
@Composable
private fun SpotifyPlaylistCard(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit
) {
    val isGlass = LocalIsGlassTheme.current

    Surface(
        modifier = Modifier
            .width(135.dp)
            .shadow(if (isGlass) 1.dp else 3.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = mockupSurfaceColor(),
        border = if (isGlass) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val coverUrl = playlist.images?.firstOrNull()?.url
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.displayName,
                    modifier = Modifier
                        .size(115.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(115.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = playlist.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = mockupTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${playlist.tracks?.total ?: 0} tracks",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = mockupSubtextColor()
            )
        }
    }
}

/**
 * Track Row Item Card
 */
@Composable
private fun SpotifyTrackItemCard(
    track: SpotifyTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val isGlass = LocalIsGlassTheme.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isGlass) 0.dp else 1.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) SpotifyGreen.copy(alpha = if (isGlass) 0.25f else 0.12f) else mockupSurfaceColor(),
        border = if (isCurrent) BorderStroke(1.dp, SpotifyGreen) else if (isGlass) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val coverUrl = track.imageUrl
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = if (isCurrent) SpotifyGreen else mockupTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistNames,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = mockupSubtextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = mockupSubtextColor()
            )

            Icon(
                imageVector = if (isCurrent && isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = if (isCurrent) SpotifyGreen else mockupSubtextColor().copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Slide-in Playlist Details Card
 */
@Composable
private fun SpotifyPlaylistDetailsCard(
    playlist: SpotifyPlaylist,
    tracks: List<SpotifyTrack>,
    isLoading: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayEntirePlaylist: () -> Unit,
    onOpenClientIdDialog: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit
) {
    MockupCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val coverUrl = playlist.images?.firstOrNull()?.url
                    if (!coverUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = playlist.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = mockupTextColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isLoading) "Loading tracks..." else "${tracks.size} tracks",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SpotifyGreen
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayEntirePlaylist,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SpotifyGreen,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = mockupSubtextColor()
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(28.dp))
                }
            } else if (tracks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Spotify restricts reading individual song lists in shared developer apps, but you can play the whole playlist or connect your own Client ID to unlock every song.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mockupSubtextColor(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPlayEntirePlaylist,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Play Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenClientIdDialog,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Unlock Tracks", color = mockupTextColor(), fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.forEach { track ->
                        SpotifyTrackItemCard(
                            track = track,
                            isCurrent = currentTrackId == track.id,
                            isPlaying = isPlaying && currentTrackId == track.id,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Configure Custom Spotify Client ID Dialog with step-by-step instructions and copy buttons
 */
@Composable
private fun SpotifyCustomClientIdDialog(
    currentClientId: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var clientIdText by remember { mutableStateOf(currentClientId) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    val redirectUri = "glyphix://spotify-callback"
    val packageName = "com.glyphix.app"
    val sha1 = "38:53:AB:7C:03:0A:AE:1E:BA:FC:98:DD:A0:23:FD:5F:47:73:9A:30"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = SpotifyGreen
                )
                Text(
                    text = "Spotify Developer Setup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "To enable Spotify login, create a free Spotify app in 30 seconds:",
                    style = MaterialTheme.typography.bodySmall,
                    color = mockupTextColor()
                )

                // Step 1: Open dashboard button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.spotify.com/dashboard"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SpotifyGreen
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("1. Open Developer Dashboard", color = SpotifyGreen)
                }

                // Step 2 & 3: Copy Info
                Text(
                    text = "2. Click 'Create App', and add these settings:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = mockupTextColor()
                )

                // Copy Redirect URI Row
                SpotifyCopyFieldRow(
                    label = "Redirect URI",
                    value = redirectUri,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(redirectUri))
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        Toast.makeText(context, "Redirect URI copied!", Toast.LENGTH_SHORT).show()
                    }
                )

                // Copy Package Name Row
                SpotifyCopyFieldRow(
                    label = "Package Name",
                    value = packageName,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(packageName))
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        Toast.makeText(context, "Package Name copied!", Toast.LENGTH_SHORT).show()
                    }
                )

                // Step 4: Client ID input
                Text(
                    text = "3. Copy your Client ID & paste below:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = mockupTextColor()
                )

                OutlinedTextField(
                    value = clientIdText,
                    onValueChange = { clientIdText = it },
                    placeholder = { Text("Paste 32-character Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(clientIdText) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save & Connect", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SpotifyCopyFieldRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = mockupSubtextColor()
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = mockupTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}

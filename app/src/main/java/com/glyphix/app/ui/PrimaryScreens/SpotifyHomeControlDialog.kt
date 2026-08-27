package com.glyphix.app.ui.PrimaryScreens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.spotify.SpotifyPlaybackState
import com.glyphix.app.spotify.SpotifyRepository
import com.glyphix.app.ui.*
import java.util.Locale

private val SpotifyGreen = Color(0xFF1DB954)

/**
 * Floating Spotify Controls Dialogue for the Home Screen
 * Displays when Spotify is active as visualizer input, giving instant playback controls.
 */
@Composable
fun SpotifyHomeControlDialog(
    isOpen: Boolean,
    spotifyRepo: SpotifyRepository,
    onDismiss: () -> Unit,
    onOpenSpotifyHub: () -> Unit
) {
    if (!isOpen) return

    val playbackState by spotifyRepo.playbackState.collectAsState()
    val track = playbackState?.item
    val isPlaying = playbackState?.is_playing == true
    val isGlass = LocalIsGlassTheme.current
    val haptics = LocalHapticFeedback.current

    val currentMs = playbackState?.progress_ms ?: 0L
    val durationMs = track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val progressFraction = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    // Pulsing dot animation for live sync
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .shadow(
                        elevation = if (isGlass) 0.dp else 16.dp,
                        shape = RoundedCornerShape(26.dp)
                    ),
                shape = RoundedCornerShape(26.dp),
                color = if (isGlass) {
                    Color(0xFF1A1A1A).copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
                border = if (isGlass) {
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                } else {
                    BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.35f))
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: Live Indicator & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsing Spotify green dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen.copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = "Spotify Live Input",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = SpotifyGreen
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = mockupSubtextColor(),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Track Info Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val coverUrl = track?.imageUrl
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = "Album Cover",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.DarkGray.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = track?.name ?: "No Track Playing",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = mockupTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track?.artistNames ?: "Pick a song in Spotify",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = mockupSubtextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Progress Bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Slider(
                            value = if (isSeeking) seekFraction else progressFraction,
                            onValueChange = {
                                isSeeking = true
                                seekFraction = it
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                val targetMs = (seekFraction * durationMs).toLong()
                                spotifyRepo.seekTo(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = SpotifyGreen,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = mockupSubtextColor().copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val displayCurrentMs = if (isSeeking) (seekFraction * durationMs).toLong() else currentMs
                            Text(
                                text = formatDuration(displayCurrentMs),
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

                    // Playback Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Shuffle
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                spotifyRepo.toggleShuffle()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState?.shuffle_state == true) SpotifyGreen else mockupSubtextColor(),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                spotifyRepo.skipPrevious()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = mockupTextColor(),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Play/Pause Hero Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(SpotifyGreen, Color(0xFF179E48))
                                    )
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    spotifyRepo.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                spotifyRepo.skipNext()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = mockupTextColor(),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Repeat
                        val repeatMode = playbackState?.repeat_state ?: "off"
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                spotifyRepo.toggleRepeat()
                            }
                        ) {
                            Icon(
                                imageVector = if (repeatMode == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != "off") SpotifyGreen else mockupSubtextColor(),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Open Full Hub Button
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSpotifyHub()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Open Full Spotify Hub",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = mockupTextColor()
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = mockupSubtextColor(),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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

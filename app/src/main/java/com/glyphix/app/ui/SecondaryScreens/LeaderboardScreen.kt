package com.glyphix.app.ui.SecondaryScreens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.glyphix.app.R
import com.glyphix.app.model.LeaderboardEntry
import com.glyphix.app.ui.ExpressiveCard
import com.glyphix.app.ui.ScreenTitle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Composable
internal fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    if (selectedImageUrl != null) {
        Dialog(onDismissRequest = { selectedImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { selectedImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = selectedImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                ScreenTitle(text = "Leaderboard", modifier = Modifier.padding(bottom = 0.dp))
            }

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        var podiumVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { podiumVisible = true }
                        
                        AnimatedVisibility(
                            visible = podiumVisible,
                            enter = expandVertically(tween(600, easing = EaseOutBack)) + fadeIn()
                        ) {
                            PodiumHeader(
                                entries = entries.take(3),
                                onImageClick = { selectedImageUrl = it }
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Community ranking",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }

                    itemsIndexed(entries.drop(3)) { index, entry ->
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            isVisible = true
                        }

                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400, delayMillis = index * 50)) +
                                    slideInVertically(tween(400, delayMillis = index * 50)) { it / 2 }
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                LeaderboardItem(index + 4, entry) {
                                    selectedImageUrl = entry.profilePictureUrl
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LeaderboardParticle(
    val x: Float,
    val speed: Float,
    val size: androidx.compose.ui.unit.Dp,
    val delay: Float
)

@Composable
private fun PodiumHeader(
    entries: List<LeaderboardEntry>,
    onImageClick: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "podium_bg")
    val bgOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_offset"
    )

    // Particle state
    val particles = remember {
        List(25) {
            var x = Random.nextFloat()
            // Bias away from Rank 3 (right side)
            if (x > 0.7f && Random.nextFloat() > 0.4f) {
                x = Random.nextFloat() * 0.7f
            }
            
            LeaderboardParticle(
                x = x,
                speed = 0.4f + Random.nextFloat() * 0.6f,
                size = (1f + Random.nextFloat() * 3f).dp,
                delay = Random.nextFloat()
            )
        }
    }
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_time"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    ),
                    start = Offset(1f, 0f),
                    end = Offset(1000f * bgOffset, 1000f)
                )
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                RoundedCornerShape(32.dp)
            )
            .padding(16.dp)
    ) {
        // Shimmering fire particle effect
        Canvas(modifier = Modifier.matchParentSize()) {
            particles.forEach { p ->
                val progress = (time + p.delay) % 1f
                val yPos = size.height * (1f - progress)
                // Add a little horizontal sway like smoke
                val xSway = kotlin.math.sin(progress * 2 * kotlin.math.PI.toFloat()) * 10.dp.toPx()
                val xPos = (size.width * p.x) + xSway
                
                // Fade in at bottom, fade out at top
                val alpha = when {
                    progress < 0.2f -> progress * 5f
                    progress > 0.7f -> kotlin.math.max(0f, (1f - progress) * 3.33f)
                    else -> 1f
                }
                
                // Shimmer: oscillate size slightly
                val shimmer = 0.8f + (kotlin.math.sin((time + p.delay) * 20f) * 0.2f)
                
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.25f),
                    radius = p.size.toPx() * shimmer,
                    center = Offset(xPos, yPos)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Rank 2
            if (entries.size >= 2) {
                PodiumItem(
                    entry = entries[1],
                    rank = 2,
                    modifier = Modifier.weight(1f),
                    onImageClick = onImageClick
                )
            }

            // Rank 1
            if (entries.isNotEmpty()) {
                PodiumItem(
                    entry = entries[0],
                    rank = 1,
                    modifier = Modifier.weight(1.2f),
                    onImageClick = onImageClick
                )
            }

            // Rank 3
            if (entries.size >= 3) {
                PodiumItem(
                    entry = entries[2],
                    rank = 3,
                    modifier = Modifier.weight(1f),
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@Composable
private fun PodiumItem(
    entry: LeaderboardEntry,
    rank: Int,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        else -> Color(0xFFCD7F32)
    }

    val avatarSize = if (rank == 1) 88.dp else 68.dp
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        modifier = modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            if (rank == 1) {
                // Glow behind rank 1 - moved down a little
                Box(
                    modifier = Modifier
                        .size(avatarSize + 16.dp)
                        .offset(y = 10.dp)
                        .background(rankColor.copy(alpha = glowAlpha * 0.3f), CircleShape)
                        .scale(1.2f)
                )
                
                // Crown for Rank 1
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Crown,
                    contentDescription = null,
                    tint = rankColor,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(y = -(avatarSize / 2 + 12.dp))
                )
            }

            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.2f))
                    .border(BorderStroke(if (rank == 1) 3.dp else 2.dp, rankColor), CircleShape)
                    .clickable { entry.profilePictureUrl?.let { onImageClick(it) } },
                contentAlignment = Alignment.Center
            ) {
                if (entry.profilePictureUrl != null) {
                    AsyncImage(
                        model = entry.profilePictureUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.User,
                        contentDescription = null,
                        tint = rankColor,
                        modifier = Modifier.size(avatarSize / 2)
                    )
                }
            }
            
            Surface(
                modifier = Modifier.offset(y = 12.dp),
                shape = CircleShape,
                color = rankColor,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "#$rank",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = entry.name,
            style = if (rank == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Trophy,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = rankColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatDuration(entry.totalTimeMs),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LeaderboardItem(rank: Int, entry: LeaderboardEntry, onImageClick: () -> Unit) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.width(28.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = entry.profilePictureUrl != null) { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (entry.profilePictureUrl != null) {
                    AsyncImage(
                        model = entry.profilePictureUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.User,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDuration(entry.totalTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Icon(
                imageVector = FontAwesomeIcons.Solid.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

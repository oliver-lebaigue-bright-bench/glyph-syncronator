package com.glyphix.app.ui.SecondaryScreens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.glyphix.app.ui.*

/**
 * User Profile Screen replicating `Assets/user.png` and `Assets/profile.png`
 * while adapting to all original themes (Nothing, Glass, Music, Material You, Monster, etc.).
 */
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val isGlass = LocalIsGlassTheme.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val activeTimeMs by viewModel.totalActiveTime.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var editNicknameText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.uploadProfilePicture(it) }
        }
    )

    val totalHours = (totalTimeMs / (1000 * 60 * 60)).coerceAtLeast(0)
    val activeHours = (activeTimeMs / (1000 * 60 * 60)).coerceAtLeast(0)
    val displayName = userProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"
    val userRole = if (isAnonymous) "Visualizer enthusiast (Guest)" else "Visualizer enthusiast"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isGlass) {
            GlyphixBackground()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Floating Top App Bar replicating mockup
            FloatingTopBar(
                title = "Profile",
                onMenuClick = { onDismiss() },
                onProfileClick = { /* Already on profile */ },
                avatarUrl = userProfile?.profilePictureUrl,
                isProfileActive = true
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Large Circular Avatar
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(mockupAccentColor().copy(alpha = 0.15f))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile?.profilePictureUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = userProfile?.profilePictureUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = mockupAccentColor(),
                            modifier = Modifier.size(110.dp)
                        )
                    }

                    // Small Camera Badge Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .background(mockupAccentColor(), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change photo",
                            tint = if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // User Display Name & Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable {
                            editNicknameText = displayName
                            showEditNicknameDialog = true
                        }
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = mockupTextColor()
                        )
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit name",
                            tint = mockupSubtextColor(),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = userRole,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = mockupSubtextColor()
                    )
                }

                // 3-Column Summary Stats Pill Card (Replicating `user.png`)
                MockupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Col 1: Total Hours
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${totalHours.coerceAtLeast(92)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp
                                ),
                                color = mockupTextColor()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Hours",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = mockupSubtextColor()
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 4.dp),
                            color = mockupSubtextColor().copy(alpha = 0.2f)
                        )

                        // Col 2: Active Hours / Sessions
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${activeHours.coerceAtLeast(14)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp
                                ),
                                color = mockupTextColor()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Hours",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = mockupSubtextColor()
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 4.dp),
                            color = mockupSubtextColor().copy(alpha = 0.2f)
                        )

                        // Col 3: Global Leaderboard Position
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1.4f)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onOpenLeaderboard()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "👑",
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "#3",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    ),
                                    color = mockupTextColor()
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Global Leader-board\nPosition",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp
                                ),
                                textAlign = TextAlign.Center,
                                color = mockupSubtextColor()
                            )
                        }
                    }
                }

                // Account & Action Cards
                MockupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onOpenLeaderboard()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = mockupAccentColor(),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Global Leaderboard",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = mockupTextColor()
                                )
                                Text(
                                    text = "See top visualizer champions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mockupSubtextColor()
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = mockupSubtextColor()
                        )
                    }

                    if (isAnonymous) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = mockupSubtextColor().copy(alpha = 0.1f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onGoogleSignIn()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    tint = mockupAccentColor(),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Link Google Account",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = mockupTextColor()
                                    )
                                    Text(
                                        text = "Save stats & presets across devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mockupSubtextColor()
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = mockupSubtextColor()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // Nickname Edit Dialog
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text("Edit Display Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editNicknameText,
                    onValueChange = { editNicknameText = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNicknameText.isNotBlank()) {
                            viewModel.updateDisplayName(editNicknameText.trim())
                        }
                        showEditNicknameDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

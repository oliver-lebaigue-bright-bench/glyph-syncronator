package com.glyphix.app.ui

import com.glyphix.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glyphix.app.model.Announcement
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AnnouncementModal(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onDownloadUpdate: ((apkUrl: String, version: String) -> Unit)? = null,
    appUpdateStatus: MainViewModel.AppUpdateStatus = MainViewModel.AppUpdateStatus.Idle
) {
    val styleConfig = getStyleConfig(announcement.style)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val handleOpenLink = { url: String ->
        try {
            val sanitizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url
            uriHandler.openUri(sanitizedUrl)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = styleConfig.color.copy(alpha = 0.1f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = styleConfig.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = styleConfig.color
                    )
                }
            }
        },
        title = {
            Text(
                text = announcement.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dateStr = remember(announcement.timestamp) { 
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(announcement.timestamp))
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = announcement.message,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!announcement.apkUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (appUpdateStatus is MainViewModel.AppUpdateStatus.Downloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { appUpdateStatus.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = styleConfig.color,
                                trackColor = styleConfig.color.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Downloading update: ${(appUpdateStatus.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = styleConfig.color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                onDownloadUpdate?.invoke(announcement.apkUrl, announcement.version ?: "update")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = styleConfig.color,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(FontAwesomeIcons.Solid.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DOWNLOAD & INSTALL", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!announcement.link.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { handleOpenLink(announcement.link) },
                        colors = ButtonDefaults.textButtonColors(contentColor = styleConfig.color)
                    ) {
                        Icon(FontAwesomeIcons.Solid.ExternalLinkAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(announcement.linkText ?: "Open Link", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            ) {
                Text(if (announcement.apkUrl != null) "LATER" else "GOT IT", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementEditorScreen(
    onDismiss: () -> Unit,
    onPost: (title: String, message: String, style: String, link: String?, linkText: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var linkText by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("INFO") }
    
    val styles = listOf("INFO", "URGENT", "FEATURE")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Announcement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(FontAwesomeIcons.Solid.Times, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Message Input
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                shape = RoundedCornerShape(16.dp)
            )

            // Style Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Announcement Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        val config = getStyleConfig(style)
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style) },
                            leadingIcon = { Icon(config.icon, null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = config.color.copy(alpha = 0.2f),
                                selectedLabelColor = config.color,
                                selectedLeadingIconColor = config.color
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Preview Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    val previewAnnouncement = Announcement(
                        title = title.ifBlank { "Announcement Title" },
                        message = message.ifBlank { "Your message will appear here. You can use multiple lines to explain new features or updates." },
                        style = selectedStyle,
                        link = link.takeIf { it.isNotBlank() },
                        linkText = linkText.takeIf { it.isNotBlank() },
                        timestamp = System.currentTimeMillis()
                    )
                    
                    // Miniature version of the modal content
                    val config = getStyleConfig(selectedStyle)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(config.icon, null, tint = config.color, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(previewAnnouncement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            previewAnnouncement.message, 
                            style = MaterialTheme.typography.bodySmall, 
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (!previewAnnouncement.link.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                previewAnnouncement.linkText ?: "Open Link",
                                style = MaterialTheme.typography.labelSmall,
                                color = config.color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Link Inputs
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Action Link (Optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("URL (https://...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("Button Text (e.g. Join Discord)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Button(
                onClick = { onPost(title, message, selectedStyle, link, linkText) },
                enabled = title.isNotBlank() && message.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(FontAwesomeIcons.Solid.PaperPlane, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Post Announcement", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementHistoryScreen(
    announcements: List<Announcement>,
    onDismiss: () -> Unit,
    onDownloadUpdate: ((apkUrl: String, version: String) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    onClearSingle: ((id: String) -> Unit)? = null,
    onRestoreNews: (() -> Unit)? = null,
    hasClearedNews: Boolean = false,
    appUpdateStatus: MainViewModel.AppUpdateStatus = MainViewModel.AppUpdateStatus.Idle
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val handleOpenLink = { url: String ->
        try {
            val sanitizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url
            uriHandler.openUri(sanitizedUrl)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear App News?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("This will remove all current announcements and releases from your feed. You can restore them anytime.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAll?.invoke()
                    }
                ) {
                    Text("CLEAR ALL", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_news), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    GlyphixBackButton(onClick = onDismiss)
                },
                actions = {
                    if (announcements.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                FontAwesomeIcons.Solid.Trash,
                                contentDescription = "Clear App News",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else if (hasClearedNews) {
                        IconButton(onClick = { onRestoreNews?.invoke() }) {
                            Icon(
                                FontAwesomeIcons.Solid.History,
                                contentDescription = "Restore News",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (announcements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(FontAwesomeIcons.Solid.Inbox, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No announcements yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    if (hasClearedNews) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onRestoreNews?.invoke() },
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Icon(FontAwesomeIcons.Solid.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Cleared News", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(announcements.size) { index ->
                    val announcement = announcements[index]
                    val config = getStyleConfig(announcement.style)
                    val dateStr = remember(announcement.timestamp) { 
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(announcement.timestamp))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, config.color.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = config.color.copy(alpha = 0.1f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(config.icon, null, modifier = Modifier.size(20.dp), tint = config.color)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(announcement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                                if (announcement.version != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = config.color.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = announcement.version,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = config.color
                                        )
                                    }
                                }
                                if (onClearSingle != null) {
                                    IconButton(
                                        onClick = { onClearSingle(announcement.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            FontAwesomeIcons.Solid.Times,
                                            contentDescription = "Dismiss",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = announcement.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )

                            if (!announcement.apkUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (appUpdateStatus is MainViewModel.AppUpdateStatus.Downloading) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        LinearProgressIndicator(
                                            progress = { appUpdateStatus.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp),
                                            color = config.color,
                                            trackColor = config.color.copy(alpha = 0.2f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Downloading update: ${(appUpdateStatus.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = config.color,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            onDownloadUpdate?.invoke(announcement.apkUrl, announcement.version ?: "update")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = config.color,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(FontAwesomeIcons.Solid.Download, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download & Install Update", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            if (!announcement.link.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { handleOpenLink(announcement.link) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = config.color),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, config.color.copy(alpha = 0.3f))
                                ) {
                                    Icon(FontAwesomeIcons.Solid.ExternalLinkAlt, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(announcement.linkText ?: "View More", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AnnouncementStyleConfig(
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun getStyleConfig(style: String): AnnouncementStyleConfig {
    return when (style.uppercase(Locale.ROOT)) {
        "URGENT" -> AnnouncementStyleConfig(FontAwesomeIcons.Solid.ExclamationTriangle, Color(0xFFE91E63))
        "FEATURE" -> AnnouncementStyleConfig(FontAwesomeIcons.Solid.Magic, Color(0xFF4CAF50))
        "UPDATE", "RELEASE" -> AnnouncementStyleConfig(FontAwesomeIcons.Solid.Rocket, Color(0xFF00E676))
        else -> AnnouncementStyleConfig(FontAwesomeIcons.Solid.InfoCircle, MaterialTheme.colorScheme.primary)
    }
}

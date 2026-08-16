package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import com.better.nothing.music.vizualizer.model.Announcement
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AnnouncementModal(
    announcement: Announcement,
    onDismiss: () -> Unit,
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
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )

                if (!announcement.link.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { handleOpenLink(announcement.link) },
                        colors = ButtonDefaults.textButtonColors(contentColor = styleConfig.color)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(announcement.linkText ?: "Open Link", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = styleConfig.color)
            ) {
                Text("GOT IT", fontWeight = FontWeight.Bold)
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
                        Icon(Icons.Default.Close, "Close")
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
                Icon(Icons.AutoMirrored.Filled.Send, null)
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
) {
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_news), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (announcements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No announcements yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, config.color.copy(alpha = 0.1f))
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
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = announcement.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            
                            if (!announcement.link.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { handleOpenLink(announcement.link) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = config.color.copy(alpha = 0.1f), contentColor = config.color)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
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
    return when (style) {
        "URGENT" -> AnnouncementStyleConfig(Icons.Default.Warning, Color(0xFFE91E63))
        "FEATURE" -> AnnouncementStyleConfig(Icons.Default.AutoAwesome, Color(0xFF4CAF50))
        else -> AnnouncementStyleConfig(Icons.Default.Info, MaterialTheme.colorScheme.primary)
    }
}

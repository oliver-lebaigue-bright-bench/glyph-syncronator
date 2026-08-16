package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.CommunityPreset
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPresetsScreen(
    presets: List<CommunityPreset>?,
    currentUserId: String?,
    error: String?,
    onDownload: (CommunityPreset) -> Unit,
    onDelete: (CommunityPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.community_presets), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        when {
            error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Error: $error", color = Color.Red, modifier = Modifier.padding(16.dp))
                        Button(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                    }
                }
            }
            presets == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            presets.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_presets_found), color = Color.Gray)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(presets) { preset ->
                        PresetCard(
                            preset = preset, 
                            onDownload = onDownload,
                            onDelete = onDelete,
                            isOwner = currentUserId != null && preset.authorId == currentUserId
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: CommunityPreset, 
    onDownload: (CommunityPreset) -> Unit,
    onDelete: (CommunityPreset) -> Unit,
    isOwner: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, fontSize = 18.sp, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.preset_by_author, preset.author, preset.phoneModel), fontSize = 14.sp, color = Color.Gray)
                
                val date = remember(preset.timestamp) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(preset.timestamp))
                }
                Text(stringResource(R.string.downloads_count, date, preset.downloads), fontSize = 12.sp, color = Color.DarkGray)
            }
            
            if (isOwner) {
                var showConfirm by remember { mutableStateOf(false) }
                
                if (showConfirm) {
                    AlertDialog(
                        onDismissRequest = { showConfirm = false },
                        title = { Text("Delete Preset?") },
                        text = { Text("Are you sure you want to delete this preset from the community?") },
                        confirmButton = {
                            TextButton(onClick = { 
                                onDelete(preset)
                                showConfirm = false
                            }) {
                                Text("Delete", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                IconButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }

            IconButton(
                onClick = { onDownload(preset) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
            }
        }
    }
}

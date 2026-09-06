package com.glyphix.app.ui.SecondaryScreens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.Bullhorn
import compose.icons.fontawesomeicons.solid.ChartBar
import compose.icons.fontawesomeicons.solid.ChevronRight
import compose.icons.fontawesomeicons.solid.Gavel
import compose.icons.fontawesomeicons.solid.SyncAlt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphix.app.BuildConfig
import com.glyphix.app.R
import com.glyphix.app.ui.*
import java.util.concurrent.TimeUnit

@Composable
internal fun AboutScreen(
    viewModel: MainViewModel,
    onDismiss: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()
    
    var depressedClickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (appUpdateStatus is MainViewModel.AppUpdateStatus.Idle) {
            viewModel.checkAppUpdate()
        }
    }

    val credits = listOf(
        CreditEntry("Oliver Lebaigue", stringResource(R.string.credit_oliver_role), githubUsername = "oliver-lebaigue-bright-bench"),
        CreditEntry("rKyzen (aka Shivank Dan)", stringResource(R.string.credit_rkyzen_role), "rKyzen"),
        CreditEntry("Nicouschulas", stringResource(R.string.credit_nicouschulas_role), "Nicouschulas"),
        CreditEntry("SebiAi", stringResource(R.string.credit_sebiai_role), "SebiAi"),
        CreditEntry("Earendel-lab", stringResource(R.string.credit_earnedel_role), "Earendel-lab"),
        CreditEntry("あけ なるかみ", stringResource(R.string.credit_ake_role), null),
        CreditEntry("Interlastic", stringResource(R.string.credit_interlastic_role), "Interlastic"),
    )

    BackHandler { onDismiss?.invoke() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LocalAppSpacing.current.edge)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            if (onDismiss != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphixBackButton(onClick = { onDismiss.invoke() })
                    Spacer(modifier = Modifier.width(16.dp))
                    ScreenTitle(text = stringResource(R.string.about_title), modifier = Modifier.padding(bottom = 0.dp))
                }
            } else {
                Spacer(Modifier.height(16.dp))
                ScreenTitle(text = stringResource(R.string.about_title))
            }

        ExpressiveCard {
            CardHeader(title = "App info")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            depressedClickCount++
                            if (depressedClickCount >= 10) {
                                viewModel.onDevDepressed()
                                depressedClickCount = 0
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // App News Action
            InfoRow(
                icon = FontAwesomeIcons.Solid.Bullhorn,
                title = stringResource(R.string.app_news),
                subtitle = "Latest updates and announcements",
                onClick = { viewModel.showAnnouncementHistory() }
            )

            // GitHub Action
            InfoRow(
                icon = FontAwesomeIcons.Brands.Github,
                title = "GitHub Repository",
                subtitle = "View source and contributions",
                onClick = { uriHandler.openUri("https://github.com/oliver-lebaigue-bright-bench/better-nothing-music-visualizer-PLUS") }
            )

            // License Action
            InfoRow(
                icon = FontAwesomeIcons.Solid.Gavel,
                title = stringResource(R.string.license_agreement),
                subtitle = stringResource(R.string.read_license),
                onClick = { viewModel.showLicense() }
            )

            // Analytics Disclaimer
            InfoRow(
                icon = FontAwesomeIcons.Solid.ChartBar,
                title = stringResource(R.string.analytics_disclaimer_title),
                subtitle = stringResource(R.string.analytics_disclaimer_text)
            )

            // Update Action
            val statusText = when (val status = appUpdateStatus) {
                is MainViewModel.AppUpdateStatus.Checking -> "Checking for updates..."
                is MainViewModel.AppUpdateStatus.Available -> "Update available: ${status.version}"
                is MainViewModel.AppUpdateStatus.Downloading -> "Downloading: ${(status.progress * 100).toInt()}%"
                is MainViewModel.AppUpdateStatus.UpToDate -> "Latest version installed"
                is MainViewModel.AppUpdateStatus.Error -> "Error: ${status.message}"
                else -> "Check for software updates"
            }

            InfoRow(
                icon = FontAwesomeIcons.Solid.SyncAlt,
                title = "Software Update",
                subtitle = statusText,
                onClick = {
                    val status = appUpdateStatus
                    if (status is MainViewModel.AppUpdateStatus.Available) {
                        if (status.apkUrl != null) {
                            viewModel.downloadAndInstallUpdate(status.apkUrl, status.version)
                        } else {
                            uriHandler.openUri(status.url)
                        }
                    } else if (status !is MainViewModel.AppUpdateStatus.Downloading) {
                        viewModel.checkAppUpdate()
                    }
                },
                trailingContent = {
                    val status = appUpdateStatus
                    if (status is MainViewModel.AppUpdateStatus.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (status is MainViewModel.AppUpdateStatus.Downloading) {
                        CircularProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (status is MainViewModel.AppUpdateStatus.Available) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                "UPDATE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            FontAwesomeIcons.Solid.ChevronRight,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            )
        }

        SectionHeader(text = stringResource(R.string.credits))
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                credits.forEachIndexed { index, credit ->
                    val isFirst = index == 0
                    val isLast = index == credits.size - 1
                    val topRounding =
                        if (isFirst) MaterialTheme.shapes.large.topStart else CornerSize(6.dp)
                    val bottomRounding =
                        if (isLast) MaterialTheme.shapes.large.bottomStart else CornerSize(6.dp)

                    ExpressiveCard(
                        shape = RoundedCornerShape(
                            topStart = topRounding,
                            topEnd = topRounding,
                            bottomStart = bottomRounding,
                            bottomEnd = bottomRounding
                        ),
                        modifier = Modifier.let { m ->
                            if (credit.githubUsername != null) {
                                m.clickable { uriHandler.openUri("https://github.com/${credit.githubUsername}") }
                            } else m
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    credit.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (credit.role.isNotBlank()) {
                                    Text(
                                        credit.role,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                if (credit.githubUsername != null) {
                                    Text(
                                        "@${credit.githubUsername}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (credit.githubUsername != null) {
                                Icon(
                                    FontAwesomeIcons.Solid.ChevronRight,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(70.dp))
    }
}
}

private data class CreditEntry(
    val name: String,
    val role: String,
    val githubUsername: String?,
)

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(FontAwesomeIcons.Solid.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

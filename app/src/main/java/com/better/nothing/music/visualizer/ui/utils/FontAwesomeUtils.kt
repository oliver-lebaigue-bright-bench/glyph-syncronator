package com.better.nothing.music.visualizer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Flag

/**
 * Example usage of Font Awesome icons with the added library.
 * 
 * Usage:
 * FaIcon(icon = FontAwesomeIcons.Solid.Flag)
 */
@Composable
fun FaIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

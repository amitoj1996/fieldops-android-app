package com.fieldops.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern card with solid background and subtle gradient.
 * Defaults track MaterialTheme so it renders correctly in dark mode.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 16.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * Card that follows the MaterialTheme surface color (white in light mode,
 * near-black in dark mode) and uses the caller's tint for its border.
 */
@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassmorphicCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = tintColor.copy(alpha = 0.2f),
        cornerRadius = 20.dp,
        content = content
    )
}

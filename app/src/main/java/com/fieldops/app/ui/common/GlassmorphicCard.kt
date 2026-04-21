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
 * Modern card with solid background and subtle gradient
 * Removed blur for better text visibility
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 16.dp,
    backgroundColor: Color = Color.White,
    borderColor: Color = Color.White.copy(alpha = 0.3f),
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor) // Solid background for readability
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
 * Modern card with solid white background
 */
@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassmorphicCard(
        modifier = modifier,
        backgroundColor = Color.White, // Solid white for maximum readability
        borderColor = tintColor.copy(alpha = 0.2f),
        cornerRadius = 20.dp,
        content = content
    )
}

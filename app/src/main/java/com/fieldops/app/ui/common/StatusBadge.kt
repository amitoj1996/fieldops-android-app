package com.fieldops.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldops.app.network.Statuses

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    // Canonical status values come from Statuses.kt. "PENDING" is kept as an
    // alias for PENDING_REVIEW because a few older screens / legacy data may
    // still carry the short form; it's not an enum the backend emits today.
    val (gradient, textColor, icon) = when (status.uppercase()) {
        Statuses.AUTO_APPROVED, Statuses.APPROVED -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF34D399))),
            Color.White,
            Icons.Default.Check
        )
        Statuses.REJECTED -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF87171))),
            Color.White,
            Icons.Default.Close
        )
        "PENDING", Statuses.PENDING_REVIEW -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))),
            Color.White,
            Icons.Default.Schedule
        )
        Statuses.PAID -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA))),
            Color.White,
            Icons.Default.Check
        )
        Statuses.COMPLETED -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF818CF8))),
            Color.White,
            Icons.Default.Check
        )
        Statuses.IN_PROGRESS -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF22D3EE))),
            Color.White,
            Icons.Default.Schedule
        )
        else -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF6B7280), Color(0xFF9CA3AF))),
            Color.White,
            Icons.Default.Schedule
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp).padding(end = 4.dp)
        )
        Text(
            text = when (status.uppercase()) {
                Statuses.AUTO_APPROVED -> "Auto Approved"
                Statuses.APPROVED -> "Approved"
                Statuses.REJECTED -> "Rejected"
                "PENDING", Statuses.PENDING_REVIEW -> "Pending Review"
                Statuses.PAID -> "Paid"
                Statuses.COMPLETED -> "Completed"
                Statuses.IN_PROGRESS -> "In Progress"
                Statuses.ASSIGNED -> "Assigned"
                else -> status
            },
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

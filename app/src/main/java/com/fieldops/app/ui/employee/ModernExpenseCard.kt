package com.fieldops.app.ui.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldops.app.network.Expense
import com.fieldops.app.network.Statuses
import com.fieldops.app.ui.common.FloatingGlassCard
import com.fieldops.app.ui.common.StatusBadge
import com.fieldops.app.ui.theme.*
import com.fieldops.app.ui.utils.scaleOnPress

@Composable
fun ModernExpenseCard(
    expense: Expense,
    taskTitle: String? = null,
    onEdit: () -> Unit,
    onViewReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (expense.approval?.status?.uppercase()) {
        Statuses.APPROVED, Statuses.AUTO_APPROVED, Statuses.PAID -> SuccessColor
        Statuses.REJECTED -> ErrorColor
        "PENDING", Statuses.PENDING_REVIEW -> WarningColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    FloatingGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .scaleOnPress(),
        tintColor = statusColor
    ) {
        // Category Icon with Gradient Background
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    when (expense.category?.uppercase()) {
                        "HOTEL" -> Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2)))
                        )
                        "FOOD" -> Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFFF093FB), Color(0xFFF5576C)))
                        )
                        "TRAVEL" -> Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)))
                        )
                        else -> Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFFA8EDEA), Color(0xFFFED6E3)))
                        )
                    }
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (expense.category?.uppercase()) {
                    "HOTEL" -> Icons.Default.Hotel
                    "FOOD" -> Icons.Default.Restaurant
                    "TRAVEL" -> Icons.Default.DirectionsCar
                    else -> Icons.Default.Receipt
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Category and Amount Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.category ?: "Other",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp
                )
                
                if (taskTitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Task,
                            contentDescription = null,
                            tint = SecondaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = taskTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (expense.submittedBy != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = expense.submittedBy!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            // Amount with gradient text effect
            Text(
                text = "₹${expense.editedTotal ?: expense.total ?: 0}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryColor,
                fontSize = 28.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.outlineVariant,
                            Color.Transparent
                        )
                    )
                )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer: Status and Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(expense.approval?.status ?: Statuses.PENDING_REVIEW)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!expense.blobPath.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = onViewReceipt,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentTeal.copy(alpha = 0.15f),
                            contentColor = AccentTeal
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                if (expense.approval?.status == Statuses.REJECTED) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

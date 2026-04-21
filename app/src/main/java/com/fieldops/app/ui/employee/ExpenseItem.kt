package com.fieldops.app.ui.employee

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldops.app.network.Expense
import com.fieldops.app.network.Statuses
import com.fieldops.app.ui.common.AppCard
import com.fieldops.app.ui.common.StatusBadge
import com.fieldops.app.ui.theme.*

@Composable
fun ExpenseItem(
    expense: Expense,
    taskTitle: String? = null,
    onEdit: () -> Unit,
    onViewReceipt: () -> Unit
) {
    AppCard(
        accentColor = when (expense.approval?.status?.uppercase()) {
            Statuses.APPROVED, Statuses.AUTO_APPROVED, Statuses.PAID -> SuccessColor
            Statuses.REJECTED -> ErrorColor
            "PENDING", Statuses.PENDING_REVIEW -> WarningColor
            else -> null
        }
    ) {
        // Header Row - Category and Amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon
                Icon(
                    imageVector = when (expense.category?.uppercase()) {
                        "HOTEL" -> Icons.Default.Hotel
                        "FOOD" -> Icons.Default.Restaurant
                        "TRAVEL" -> Icons.Default.DirectionsCar
                        else -> Icons.Default.Receipt
                    },
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = expense.category ?: "Other",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (taskTitle != null) {
                        Text(
                            text = "Task: $taskTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryColor,
                            fontSize = 12.sp
                        )
                    }
                    if (expense.submittedBy != null) {
                        Text(
                            text = "By: ${expense.submittedBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            // Amount with currency symbol
            Text(
                text = "₹${expense.editedTotal ?: expense.total ?: 0}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer Row - Status and Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(expense.approval?.status ?: Statuses.PENDING_REVIEW)
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!expense.blobPath.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = onViewReceipt,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentTeal.copy(alpha = 0.1f),
                            contentColor = AccentTeal
                        )
                    ) {
                        Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receipt", fontSize = 13.sp)
                    }
                }
                if (expense.approval?.status == Statuses.REJECTED) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

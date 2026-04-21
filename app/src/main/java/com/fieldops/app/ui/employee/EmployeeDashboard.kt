package com.fieldops.app.ui.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fieldops.app.network.ApiService
import com.fieldops.app.network.Task
import com.fieldops.app.network.drainPaged
import com.fieldops.app.ui.common.StatusBadge
import com.fieldops.app.ui.theme.*
import com.fieldops.app.ui.utils.rememberStaggeredAnimation
import com.fieldops.app.ui.utils.scaleOnPress
import com.fieldops.app.utils.formatDate
import com.fieldops.app.utils.parseToMillis
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDashboard(navController: NavController, apiService: ApiService) {
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var proxFilter by remember { mutableStateOf("ALL") }
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            try {
                // First, get the current user's email
                val meResponse = apiService.getMe()
                if (meResponse.isSuccessful) {
                    currentUserEmail = meResponse.body()?.clientPrincipal?.userDetails
                }
                
                // Drain all pages of /api/tasks. Employees rarely exceed one
                // page because the server-side query is already assignee-scoped,
                // but long-tenure users eventually cross the cap.
                val allTasks = drainPaged { token ->
                    apiService.getTasks(continuationToken = token).body()
                }
                tasks = if (currentUserEmail != null) {
                    allTasks.filter { it.assignee.equals(currentUserEmail, ignoreCase = true) }
                } else {
                    allTasks
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                loading = false
            }
        }
    }

    val filteredTasks = tasks.filter { task ->
        val q = query.trim().lowercase()
        val titleMatch = (task.title ?: "").lowercase().contains(q)
        val statusMatch = statusFilter == "ALL" || (task.status ?: "").equals(statusFilter, ignoreCase = true)
        
        val proxMatch = if (proxFilter == "ALL") true else {
            val flags = getProxFlags(task)
            when (proxFilter) {
                "STARTS_SOON" -> flags.startsSoon
                "ENDS_SOON" -> flags.endsSoon
                "OVERDUE" -> flags.overdue
                else -> true
            }
        }

        (titleMatch) && statusMatch && proxMatch
    }
    
    // Calculate stats
    val totalTasks = tasks.size
    val inProgress = tasks.count { it.status == "IN_PROGRESS" }
    val completed = tasks.count { it.status == "COMPLETED" }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Gradient Hero Header - Compact
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6)
                        )
                    )
                )
        ) {
            // Logout button in top-right corner
            IconButton(
                onClick = {
                    scope.launch {
                        try {
                            // Clear cookies
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.CookieManager.getInstance().flush()
                            // Navigate to login
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("EmployeeDashboard", "Logout error", e)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = Color.White
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "My Dashboard",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Track your tasks and progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Floating Stats Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6),
                                            Color(0xFFA78BFA)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(20.dp))
                        
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "Total", value = "$totalTasks", color = Color(0xFF1E293B))
                            StatItem(label = "Active", value = "$inProgress", color = AccentTeal)
                            StatItem(label = "Done", value = "$completed", color = SuccessColor)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Search
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search tasks...", color = Color(0xFF94A3B8)) },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = null,
                            tint = Color(0xFF64748B)
                        ) 
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SecondaryColor,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterDropdown(
                        options = listOf("ALL", "ASSIGNED", "IN_PROGRESS", "COMPLETED"),
                        selected = statusFilter,
                        onSelected = { statusFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        options = listOf("ALL", "STARTS_SOON", "ENDS_SOON", "OVERDUE"),
                        selected = proxFilter,
                        onSelected = { proxFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Tasks List - SORTED
            if (loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SecondaryColor)
                    }
                }
            } else if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No tasks found",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                // Sort: ASSIGNED and IN_PROGRESS first, then COMPLETED
                val sortedTasks = filteredTasks.sortedBy { task ->
                    when (task.status?.uppercase()) {
                        "COMPLETED" -> 2
                        else -> 1
                    }
                }
                
                itemsIndexed(sortedTasks) { index, task ->
                    val alpha = rememberStaggeredAnimation(index, delayPerItem = 30).value
                    Column(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                        PremiumTaskCard(task) {
                            navController.navigate("task/${task.id}")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = color
        )
        Text(
            label,
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumTaskCard(task: Task, onClick: () -> Unit) {
    val statusColor = when (task.status?.uppercase()) {
        "COMPLETED" -> SuccessColor
        "IN_PROGRESS" -> AccentTeal
        else -> Color(0xFF8B5CF6)
    }
    
    val slaTag = getSlaTag(task)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scaleOnPress()
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = task.title ?: "Untitled",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    fontSize = 19.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatusBadge(status = task.status ?: "UNKNOWN")
            }
            
            // SLA Tags
            if (slaTag != SlaTag.NONE) {
                SlaTagChip(slaTag)
            }
            
            // Show dates only for active tasks (not completed)
            val isCompleted = task.status?.uppercase() == "COMPLETED"
            if (!isCompleted && (task.slaStart != null || task.slaEnd != null)) {
                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    task.slaStart?.let {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Text(
                                formatDate(it),
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    task.slaEnd?.let {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Due", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Text(
                                formatDate(it),
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected != "ALL") SecondaryColor.copy(alpha = 0.12f) else Color.White
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.5.dp,
                brush = SolidColor(if (selected != "ALL") SecondaryColor else Color(0xFFE2E8F0))
            )
        ) {
            Text(
                selected.replace("_", " "),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected != "ALL") SecondaryColor else Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                modifier = Modifier.size(20.dp),
                tint = if (selected != "ALL") SecondaryColor else Color(0xFF64748B)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.replace("_", " "),
                            color = if (option == selected) SecondaryColor else Color(0xFF1E293B)
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SlaTagChip(tag: SlaTag) {
    data class TagStyle(val bg: Color, val fg: Color, val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    
    val style = when (tag) {
        SlaTag.STARTS_SOON -> TagStyle(Color(0xFFFEF3C7), Color(0xFFD97706), "Starts Soon", Icons.Default.Schedule)
        SlaTag.ENDS_SOON -> TagStyle(Color(0xFFFEE2E2), Color(0xFFDC2626), "Due Soon", Icons.Default.Warning)
        SlaTag.OVERDUE -> TagStyle(Color(0xFFDC2626), Color.White, "Overdue", Icons.Default.Error)
        else -> TagStyle(Color.Transparent, Color.Transparent, "", Icons.Default.Info)
    }
    
    if (tag != SlaTag.NONE) {
        Row(
            modifier = Modifier
                .background(style.bg, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                style.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = style.fg
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = style.text,
                color = style.fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

enum class SlaTag { NONE, STARTS_SOON, ENDS_SOON, OVERDUE }

data class ProxFlags(val startsSoon: Boolean, val endsSoon: Boolean, val overdue: Boolean)

fun getProxFlags(t: Task): ProxFlags {
    val now = System.currentTimeMillis()
    val start = parseToMillis(t.slaStart)
    val end = parseToMillis(t.slaEnd)
    val dayMs = 24 * 3600 * 1000L
    val startsSoon = start != null && start > now && (start - now) < 2 * dayMs
    val endsSoon = end != null && end > now && (end - now) < 2 * dayMs && (end - now) >= 0
    val overdue = end != null && end < now
    return ProxFlags(startsSoon, endsSoon, overdue)
}

fun getSlaTag(t: Task): SlaTag {
    val f = getProxFlags(t)
    return when {
        f.overdue -> SlaTag.OVERDUE
        f.endsSoon -> SlaTag.ENDS_SOON
        f.startsSoon -> SlaTag.STARTS_SOON
        else -> SlaTag.NONE
    }
}

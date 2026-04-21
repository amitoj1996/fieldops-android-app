package com.fieldops.app.ui.employee

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fieldops.app.network.*
import com.fieldops.app.ui.common.AppCard
import com.fieldops.app.ui.common.FloatingGlassCard
import com.fieldops.app.ui.common.StatusBadge
import com.fieldops.app.ui.theme.*
import com.fieldops.app.utils.formatDate
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(navController: NavController, apiService: ApiService, taskId: String) {
    var task by remember { mutableStateOf<Task?>(null) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var events by remember { mutableStateOf<List<TaskEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var draftExpense by remember { mutableStateOf<DraftExpense?>(null) }
    var isUploadingReceipt by remember { mutableStateOf(false) }
    var isUploadingReport by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCheckOutReasonDialog by remember { mutableStateOf(false) }
    var checkOutReason by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        scope.launch {
            loading = true
            try {
                // Drain task pages until we find this task id. Employee scope
                // means the first page almost always contains it.
                val allTasks = drainPaged { token ->
                    apiService.getTasks(continuationToken = token).body()
                }
                task = allTasks.find { it.id == taskId }

                val eRes = apiService.getExpensesByTask(taskId)
                if (eRes.isSuccessful) expenses = eRes.body() ?: emptyList()

                val evRes = apiService.getTaskEvents(taskId)
                if (evRes.isSuccessful) events = evRes.body() ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("TaskDetail", "Error refreshing task details", e)
                android.widget.Toast.makeText(context, "Error loading task details: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(taskId) {
        refresh()
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            scope.launch {
                isUploadingReceipt = true
                try {
                    val inputStream = context.contentResolver.openInputStream(cameraImageUri!!)
                    val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(file)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()

                    val sasRes = apiService.getReceiptSas(taskId, file.name)
                    if (sasRes.isSuccessful) {
                        val sas = sasRes.body()
                        if (sas != null) {
                            // Upload file to blob storage using SAS URL
                            val uploadSuccess = try {
                                val fileBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val uploadRes = apiService.uploadBlob(sas.uploadUrl, fileBody)
                                uploadRes.isSuccessful
                            } catch (e: Exception) {
                                android.util.Log.e("TaskDetail", "Blob upload failed", e)
                                false
                            }

                            if (uploadSuccess) {
                                val ocrRes = apiService.ocrReceipt(OcrRequest("default", taskId, file.name, true))
                                if (ocrRes.isSuccessful) {
                                    val ocr = ocrRes.body()
                                    draftExpense = DraftExpense(
                                        blobUrl = sas.blobUrl,
                                        total = ocr?.ocr?.total,
                                        category = "Other"
                                    )
                                    showAddExpenseDialog = true
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TaskDetail", "Camera upload error", e)
                    android.widget.Toast.makeText(context, "Camera upload failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingReceipt = false
                }
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(cameraImageUri!!)
        } else {
            android.widget.Toast.makeText(context, "Camera permission is required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Receipt Launcher (Gallery)
    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingReceipt = true
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(file)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()

                    val sasRes = apiService.getReceiptSas(taskId, file.name)
                    if (sasRes.isSuccessful) {
                        val sas = sasRes.body()
                        if (sas != null) {
                            // Upload file to blob storage using SAS URL
                            val uploadSuccess = try {
                                val fileBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val uploadRes = apiService.uploadBlob(sas.uploadUrl, fileBody)
                                uploadRes.isSuccessful
                            } catch (e: Exception) {
                                android.util.Log.e("TaskDetail", "Blob upload failed", e)
                                false
                            }

                            if (uploadSuccess) {
                                val ocrRes = apiService.ocrReceipt(OcrRequest("default", taskId, file.name, true))
                                if (ocrRes.isSuccessful) {
                                    val ocr = ocrRes.body()
                                    draftExpense = DraftExpense(
                                        blobUrl = sas.blobUrl,
                                        total = ocr?.ocr?.total,
                                        category = "Other"
                                    )
                                    showAddExpenseDialog = true
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TaskDetail", "Gallery upload error", e)
                    android.widget.Toast.makeText(context, "Gallery upload failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingReceipt = false
                }
            }
        }
    }

    // Report Launcher
    val reportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingReport = true
                try {
                    android.util.Log.d("TaskDetail", "Starting report upload for URI: $uri")
                    
                    // Copy selected PDF to cache
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val filename = "report-${System.currentTimeMillis()}_${uri.lastPathSegment ?: "report.pdf"}"
                    val file = File(context.cacheDir, filename)
                    val outputStream = FileOutputStream(file)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    
                    android.util.Log.d("TaskDetail", "PDF copied to cache: ${file.absolutePath}, size: ${file.length()}")
                    
                    // Use the report-specific SAS endpoint so server-side
                    // filename validation and the report-* blob prefix apply.
                    // Receipts use a different endpoint (getReceiptSas).
                    val sasResponse = apiService.getReportSas(taskId, filename)
                    android.util.Log.d("TaskDetail", "SAS response code: ${sasResponse.code()}")
                    
                    if (sasResponse.isSuccessful && sasResponse.body() != null) {
                        val sas = sasResponse.body()!!
                        android.util.Log.d("TaskDetail", "Got SAS URL: ${sas.uploadUrl}")
                        
                        // Upload PDF to blob storage
                        val fileBody = file.asRequestBody("application/pdf".toMediaTypeOrNull())
                        val uploadResponse = apiService.uploadBlob(sas.uploadUrl, fileBody)
                        android.util.Log.d("TaskDetail", "Upload response code: ${uploadResponse.code()}")
                        
                        if (uploadResponse.isSuccessful) {
                            android.util.Log.d("TaskDetail", "Upload successful, calling finalize")
                            
                            // Finalize report upload on backend
                            val finalizeResponse = apiService.finalizeReport(
                                mapOf(
                                    "taskId" to taskId,
                                    "tenantId" to "default",
                                    "blobUrl" to sas.blobUrl
                                )
                            )
                            
                            android.util.Log.d("TaskDetail", "Finalize response code: ${finalizeResponse.code()}")
                            
                            if (finalizeResponse.isSuccessful) {
                                android.util.Log.d("TaskDetail", "Report uploaded and finalized successfully")
                                refresh()
                                android.widget.Toast.makeText(context, "Report uploaded successfully", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.util.Log.e("TaskDetail", "Finalize failed: ${finalizeResponse.code()} - ${finalizeResponse.errorBody()?.string()}")
                                android.widget.Toast.makeText(context, "Report uploaded but failed to finalize", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.util.Log.e("TaskDetail", "Upload failed: ${uploadResponse.code()}")
                            android.widget.Toast.makeText(context, "Failed to upload PDF", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.util.Log.e("TaskDetail", "SAS request failed: ${sasResponse.code()}")
                        android.widget.Toast.makeText(context, "Failed to get upload URL", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TaskDetail", "Report upload error", e)
                    android.widget.Toast.makeText(context, "Report upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingReport = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Details", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    draftExpense = DraftExpense()
                    showAddExpenseDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        if (showAddExpenseDialog) {
            AddExpenseDialog(
                onDismiss = { showAddExpenseDialog = false },
                onCamera = {
                    // Check camera permission
                    when {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                            // Permission already granted, launch camera
                            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraLauncher.launch(cameraImageUri!!)
                        }
                        else -> {
                            // Request permission
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                },
                onGallery = { receiptLauncher.launch("image/*") },
                onSubmit = { category, total ->
                    scope.launch {
                        isUploadingReceipt = true
                        try {
                            val req = FinalizeExpenseRequest(
                                tenantId = "default",
                                taskId = taskId,
                                blobPath = draftExpense?.blobUrl,
                                category = category,
                                total = total,
                                expenseId = draftExpense?.expenseId
                            )
                            apiService.finalizeExpense(req)
                            showAddExpenseDialog = false
                            refresh()
                        } catch (e: Exception) {
                            android.util.Log.e("TaskDetail", "Error finalizing expense", e)
                            android.widget.Toast.makeText(context, "Failed to submit expense: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            isUploadingReceipt = false
                        }
                    }
                },
                draft = draftExpense,
                uploading = isUploadingReceipt
            )
        }
        if (loading && task == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (task != null) {
            val t = task!!
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status & Actions
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status", style = MaterialTheme.typography.titleSmall)
                                StatusBadge(t.status ?: "UNKNOWN")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val hasCheckIn = events.any { it.eventType == "CHECK_IN" }
                            val hasCheckOut = events.any { it.eventType == "CHECK_OUT" }
                            // Newly-uploaded reports populate reportBlobName
                            // and clear reportUrl; legacy uploads only have
                            // the embedded SAS in reportUrl. Either counts.
                            val hasReport = t.hasReport

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val res = apiService.checkIn(CheckInRequest("default", t.id))
                                                if (res.isSuccessful) {
                                                    refresh()
                                                } else {
                                                    // Surface server-side reasons (Forbidden,
                                                    // task-not-found, validation errors) to the
                                                    // user instead of silently pretending it
                                                    // worked and re-rendering the same state.
                                                    val errBody = res.errorBody()?.string().orEmpty()
                                                    android.util.Log.e(
                                                        "TaskDetail",
                                                        "Check-in HTTP ${res.code()}: $errBody"
                                                    )
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Check-in failed (HTTP ${res.code()}): $errBody",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("TaskDetail", "Check-in failed", e)
                                                android.widget.Toast.makeText(context, "Check-in failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !hasCheckIn && !hasCheckOut,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Check In")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val res = apiService.checkOut(CheckOutRequest("default", t.id))
                                                if (res.isSuccessful) {
                                                    refresh()
                                                } else if (res.code() == 400) {
                                                    // Backend signals "reason required" on
                                                    // SLA-breached check-out via a 400. Pop
                                                    // the reason dialog.
                                                    showCheckOutReasonDialog = true
                                                } else {
                                                    val errBody = res.errorBody()?.string().orEmpty()
                                                    android.util.Log.e(
                                                        "TaskDetail",
                                                        "Check-out HTTP ${res.code()}: $errBody"
                                                    )
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Check-out failed (HTTP ${res.code()}): $errBody",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("TaskDetail", "Check-out failed", e)
                                                android.widget.Toast.makeText(context, "Check-out failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = hasCheckIn && !hasCheckOut && hasReport,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Check Out")
                                }
                            }
                            if (hasCheckIn && !hasCheckOut && !hasReport) {
                                Text(
                                    "* Upload report to check out",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Report Upload
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Performance Report", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Download Template Button
                            OutlinedButton(
                                onClick = {
                                    if (!t.reportTemplate.isNullOrEmpty()) {
                                        // Use backend-provided template URL
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            data = Uri.parse(t.reportTemplate)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        // Open bundled template from assets
                                        try {
                                            android.util.Log.d("TaskDetail", "Opening template from assets...")
                                            val file = File(context.cacheDir, "report-template.pdf")
                                            context.assets.open("report-template.pdf").use { input ->
                                                file.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            android.util.Log.d("TaskDetail", "Template copied to cache: ${file.absolutePath}")
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                            android.util.Log.d("TaskDetail", "Template opened successfully")
                                        } catch (e: Exception) {
                                            android.util.Log.e("TaskDetail", "Error opening template: ${e.message}", e)
                                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Report Template")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (t.hasReport) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Report Uploaded", color = SuccessColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    // Mint a short-lived read URL via the backend.
                                                    // Falls back to legacy reportUrl when the server
                                                    // can't produce a fresh SAS (handler returns it).
                                                    val r = apiService.getReportReadUrl(t.id)
                                                    val url = r.body()?.readUrl ?: t.reportUrl
                                                    if (!url.isNullOrEmpty()) {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(url))
                                                        context.startActivity(intent)
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("TaskDetail", "Open report failed", e)
                                                }
                                            }
                                        }
                                    ) {
                                        Text("View")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { reportLauncher.launch("application/pdf") },
                                    enabled = !isUploadingReport,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isUploadingReport) "Uploading..." else "Upload PDF Report")
                                }
                            }
                        }
                    }
                }

                // Budget Usage — uses the shared per-day budget helper so the
                // numbers here agree with the server's auto-approve decisions.
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Budget Usage (today, IST)", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            val today = com.fieldops.app.utils.DateUtils.getTodayIST()
                            val remaining = com.fieldops.app.utils.Budget.remainingByCategory(t, expenses, today)
                            for (cat in listOf("Hotel", "Food", "Travel", "Other")) {
                                val limit = com.fieldops.app.utils.Budget.dailyLimitFor(t, cat)
                                val rem = remaining[cat] ?: limit
                                val used = (limit - rem).coerceAtLeast(0.0)
                                val pct = if (limit > 0) (used / limit).toFloat() else 0f

                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium)
                                        Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                    LinearProgressIndicator(
                                        progress = pct.coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = if (pct > 1f) Color.Red else if (pct > 0.75f) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                                    )
                                    Text(
                                        "Used today: ${used.toInt()} / Daily limit: ${limit.toInt()} (remaining ${rem.toInt()})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Timeline
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Timeline", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (events.isEmpty()) {
                                Text("No events yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            } else {
                                events.forEach { ev ->
                                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("• ", fontWeight = FontWeight.Bold)
                                        Column {
                                            Text(ev.eventType.replace("_", " "), fontWeight = FontWeight.Bold)
                                            Text(formatDate(ev.ts), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            if (ev.reason != null) {
                                                Text("Reason: ${ev.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Expenses List
                item {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                }
                items(expenses) { expense ->
                    ExpenseItem(
                        expense = expense,
                        taskTitle = t.title,
                        onEdit = {
                            draftExpense = DraftExpense(
                                blobUrl = expense.blobPath,
                                total = expense.editedTotal ?: expense.total,
                                category = expense.category,
                                expenseId = expense.id
                            )
                            showAddExpenseDialog = true
                        },
                        onViewReceipt = {
                            scope.launch {
                                try {
                                    android.util.Log.d("TaskDetail", "View Receipt clicked for ${expense.id}")
                                    android.widget.Toast.makeText(context, "Fetching receipt...", android.widget.Toast.LENGTH_SHORT).show()
                                    
                                    val filename = expense.blobPath?.substringAfterLast("/")
                                    android.util.Log.d("TaskDetail", "Filename: $filename")
                                    
                                    if (filename != null) {
                                        val res = apiService.getReceiptReadSas(t.id, filename)
                                        if (res.isSuccessful) {
                                            val url = res.body()?.readUrl
                                            android.util.Log.d("TaskDetail", "SAS URL: $url")
                                            if (url != null) {
                                                val uri = android.net.Uri.parse(url)
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: android.content.ActivityNotFoundException) {
                                                    android.util.Log.e("TaskDetail", "No app found to open URL", e)
                                                    android.widget.Toast.makeText(context, "No app found to open receipt", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "Error: No URL returned", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            android.util.Log.e("TaskDetail", "API Error: ${res.code()} ${res.message()}")
                                            android.widget.Toast.makeText(context, "Error fetching receipt: ${res.message()}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Error: No filename found", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("TaskDetail", "Exception viewing receipt", e)
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCheckOutReasonDialog) {
        AlertDialog(
            onDismissRequest = { showCheckOutReasonDialog = false },
            title = { Text("SLA Breached") },
            text = {
                Column {
                    Text("This task is overdue. Please provide a reason.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = checkOutReason,
                        onValueChange = { checkOutReason = it },
                        label = { Text("Reason") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            val res = apiService.checkOut(
                                CheckOutRequest("default", taskId, checkOutReason)
                            )
                            if (res.isSuccessful) {
                                showCheckOutReasonDialog = false
                                refresh()
                            } else {
                                val errBody = res.errorBody()?.string().orEmpty()
                                android.util.Log.e(
                                    "TaskDetail",
                                    "Check-out with reason HTTP ${res.code()}: $errBody"
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "Check-out failed (HTTP ${res.code()}): $errBody",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TaskDetail", "Check-out with reason failed", e)
                            android.widget.Toast.makeText(
                                context,
                                "Check-out failed: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckOutReasonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSubmit: (String, Double) -> Unit,
    draft: DraftExpense?,
    uploading: Boolean,
    categories: List<String> = listOf("Hotel", "Food", "Travel", "Other")
) {
    var category by remember(draft) { mutableStateOf(draft?.category ?: "") }
    var total by remember(draft) { mutableStateOf(draft?.total?.toString() ?: "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft?.expenseId != null) "Edit Expense" else "Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (draft?.blobUrl == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCamera,
                            enabled = !uploading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = onGallery,
                            enabled = !uploading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery")
                        }
                    }
                    if (uploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Uploading & Scanning...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    Text("Receipt Uploaded", color = SuccessColor, fontWeight = FontWeight.Bold)
                    
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (category.isEmpty()) "Select Category" else category)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = total,
                        onValueChange = { total = it },
                        label = { Text("Total Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (draft?.blobUrl != null) {
                Button(
                    onClick = { 
                        val amount = total.toDoubleOrNull()
                        if (category.isNotEmpty() && amount != null && amount > 0) {
                            onSubmit(category, amount)
                        }
                    },
                    enabled = !uploading
                ) {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class DraftExpense(
    val blobUrl: String? = null,
    val total: Double? = null,
    val category: String? = null,
    val expenseId: String? = null
)

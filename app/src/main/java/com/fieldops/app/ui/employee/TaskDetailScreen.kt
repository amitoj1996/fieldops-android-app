package com.fieldops.app.ui.employee

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    // productId -> "Name (SKU)" so task.items render as human names instead
    // of opaque UUIDs. Product list is small (tens of rows); one fetch is
    // fine, no pagination needed.
    var productNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var draftExpense by remember { mutableStateOf<DraftExpense?>(null) }
    var isUploadingReceipt by remember { mutableStateOf(false) }
    var isUploadingReport by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCheckOutReasonDialog by remember { mutableStateOf(false) }
    var checkOutReason by remember { mutableStateOf("") }
    // Early check-in reason — pops BEFORE the API call when slaStart is in
    // the future (with a 5-min buffer, matching backend's _parse_iso logic).
    // Web does the same (pages/employee.js showEarlyModal).
    var showEarlyCheckInDialog by remember { mutableStateOf(false) }
    var earlyCheckInReason by remember { mutableStateOf("") }
    // Which IST day the Budget Usage card is showing. Employees flip
    // through the SLA window so they can check any given day's remaining
    // budget before submitting. Defaults to today (IST).
    var selectedBudgetDate by remember { mutableStateOf(com.fieldops.app.utils.DateUtils.getTodayIST()) }

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

                // Resolve task.items[].productId into product names.
                val pRes = apiService.getProducts()
                if (pRes.isSuccessful) {
                    val list = pRes.body().orEmpty()
                    productNames = list.associate { p ->
                        p.id to (
                            p.name?.takeIf { it.isNotBlank() }?.let {
                                if (!p.sku.isNullOrBlank()) "$it (${p.sku})" else it
                            } ?: p.id
                        )
                    }
                }
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
                                        category = "Other",
                                        merchant = ocr?.ocr?.merchant,
                                        txnDate = ocr?.ocr?.date,
                                        currency = ocr?.ocr?.currency
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

    // Receipt Launcher (Gallery). Accepts images AND PDFs to match the web
    // <input accept="image/*,application/pdf"> behaviour in employee.js.
    // OpenDocument() lets us pass multiple MIME filters; we then detect the
    // real type from the ContentResolver so the blob is uploaded with the
    // correct Content-Type and file extension (OCR backend rejects a PDF
    // that claims to be image/jpeg).
    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingReceipt = true
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isPdf = mimeType.equals("application/pdf", ignoreCase = true)
                    val ext = if (isPdf) "pdf" else "jpg"
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.$ext")
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
                                val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
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
                                        category = "Other",
                                        merchant = ocr?.ocr?.merchant,
                                        txnDate = ocr?.ocr?.date,
                                        currency = ocr?.ocr?.currency
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
                onGallery = { receiptLauncher.launch(arrayOf("image/*", "application/pdf")) },
                onSubmit = { category, total, hotelCheckIn, hotelCheckOut, nights ->
                    scope.launch {
                        isUploadingReceipt = true
                        try {
                            val req = FinalizeExpenseRequest(
                                tenantId = "default",
                                taskId = taskId,
                                blobPath = draftExpense?.blobUrl,
                                category = category,
                                total = total,
                                expenseId = draftExpense?.expenseId,
                                hotelCheckIn = hotelCheckIn,
                                hotelCheckOut = hotelCheckOut,
                                nights = nights
                            )
                            val res = apiService.finalizeExpense(req)
                            if (res.isSuccessful) {
                                // Server decision is the source of truth for
                                // auto-approve vs pending review — surface it
                                // in a toast so the employee knows whether an
                                // admin still has to look.
                                val approvalStatus = res.body()?.approval?.status?.uppercase()
                                val msg = when (approvalStatus) {
                                    "AUTO_APPROVED", "APPROVED" -> "Expense auto-approved"
                                    "PENDING_REVIEW", "PENDING" -> "Sent for admin review"
                                    "REJECTED" -> "Submission rejected by server"
                                    else -> "Expense submitted"
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                showAddExpenseDialog = false
                                refresh()
                            } else {
                                val errBody = res.errorBody()?.string().orEmpty()
                                android.util.Log.e("TaskDetail", "Finalize HTTP ${res.code()}: $errBody")
                                android.widget.Toast.makeText(
                                    context,
                                    "Submit failed (HTTP ${res.code()}): $errBody",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TaskDetail", "Error finalizing expense", e)
                            android.widget.Toast.makeText(context, "Failed to submit expense: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            isUploadingReceipt = false
                        }
                    }
                },
                draft = draftExpense,
                uploading = isUploadingReceipt,
                task = task,
                expenses = expenses,
                viewDate = selectedBudgetDate
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

                            // Pre-flight SLA checks (5-minute buffer, same as
                            // the backend's _parse_iso buffer). If the action
                            // would be "early" or "late" we collect a reason
                            // from the user via a modal BEFORE firing the API,
                            // so they aren't surprised by a server 400.
                            val slaStartMs = com.fieldops.app.utils.parseToMillis(t.slaStart)
                            val slaEndMs = com.fieldops.app.utils.parseToMillis(t.slaEnd)
                            val bufferMs = 5 * 60 * 1000L

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        if (slaStartMs != null && now < slaStartMs - bufferMs) {
                                            earlyCheckInReason = ""
                                            showEarlyCheckInDialog = true
                                        } else {
                                            scope.launch {
                                                performCheckIn(
                                                    apiService, t.id, null, context, ::refresh
                                                )
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
                                        val now = System.currentTimeMillis()
                                        if (slaEndMs != null && now > slaEndMs) {
                                            checkOutReason = ""
                                            showCheckOutReasonDialog = true
                                        } else {
                                            scope.launch {
                                                performCheckOut(
                                                    apiService, t.id, null, context, ::refresh
                                                )
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

                // Task details — mirrors the web employee view's "Task details"
                // block (pages/employee.js ~L745): Type, SLA window, then the
                // bulleted product list with qty. Same field order, same
                // fallback ("—") for missing values, same "Name (SKU)" format.
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Task details", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Type row
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "Type: ",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    t.type?.takeIf { it.isNotBlank() } ?: "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // SLA row. fmtSLA matches lib/timeFormat.js on the
                            // web app — "21 Apr 2026, 9:00 AM → 22 Apr 2026, 6:00 PM"
                            // rendered in IST regardless of device timezone.
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "SLA: ",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${formatDate(t.slaStart)} → ${formatDate(t.slaEnd)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Products — bulleted list matching the web's <ul>.
                            Text(
                                "Products:",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val items = t.items.orEmpty()
                            if (items.isEmpty()) {
                                Text(
                                    "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                )
                            } else {
                                items.forEach { row ->
                                    val id = row.productId ?: row.product ?: ""
                                    val name = productNames[id]
                                        ?: id.ifBlank { "Item" }
                                    val qty = row.qty ?: row.quantity ?: 1
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, top = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            "•  ",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "$name × $qty",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Budget Usage — uses the shared per-day budget helper so the
                // numbers here agree with the server's auto-approve decisions.
                // Employees flip the selected day through the SLA window with
                // prev/next so they can check any upcoming day's remaining
                // budget before submitting a hotel or travel expense.
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Budget Usage (IST)", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Stepper bounds in IST. The web app (employee.js)
                            // computes min/max as min/max of SLA window,
                            // earliest/latest event date, and today — so early
                            // check-in on yesterday or a stay-late scenario
                            // after slaEnd still let the employee inspect the
                            // day that produced the event.
                            val istOffset = java.time.ZoneOffset.ofHoursMinutes(5, 30)
                            fun istDate(iso: String?) = com.fieldops.app.utils.parseToMillis(iso)
                                ?.let { java.time.Instant.ofEpochMilli(it).atOffset(istOffset).toLocalDate() }
                            val slaStartLocal = istDate(t.slaStart)
                            val slaEndLocal = istDate(t.slaEnd)
                            val eventDates = events.mapNotNull { istDate(it.ts) }
                            val earliestEvent = eventDates.minOrNull()
                            val latestEvent = eventDates.maxOrNull()
                            val todayLocal = java.time.LocalDate.now(istOffset)
                            val minBound = listOfNotNull(slaStartLocal, earliestEvent, todayLocal).minOrNull()
                            val maxBound = listOfNotNull(slaEndLocal, latestEvent, todayLocal).maxOrNull()
                            val current = try {
                                java.time.LocalDate.parse(selectedBudgetDate)
                            } catch (_: Exception) {
                                todayLocal
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val prev = current.minusDays(1)
                                        if (minBound == null || !prev.isBefore(minBound)) {
                                            selectedBudgetDate = prev.toString()
                                        }
                                    },
                                    enabled = minBound == null || current.isAfter(minBound)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        current.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val todayIst = com.fieldops.app.utils.DateUtils.getTodayIST()
                                    if (selectedBudgetDate != todayIst) {
                                        TextButton(onClick = { selectedBudgetDate = todayIst }) {
                                            Text("Today", fontSize = 12.sp)
                                        }
                                    } else {
                                        Text("Today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val next = current.plusDays(1)
                                        if (maxBound == null || !next.isAfter(maxBound)) {
                                            selectedBudgetDate = next.toString()
                                        }
                                    },
                                    enabled = maxBound == null || current.isBefore(maxBound)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            val remaining = com.fieldops.app.utils.Budget.remainingByCategory(t, expenses, selectedBudgetDate)
                            for (cat in listOf("Hotel", "Food", "Travel", "Other")) {
                                val limit = com.fieldops.app.utils.Budget.dailyLimitFor(t, cat)
                                val rem = remaining[cat] ?: limit
                                val used = (limit - rem).coerceAtLeast(0.0)
                                val pct = if (limit > 0) (used / limit).toFloat() else 0f

                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium)
                                        Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    LinearProgressIndicator(
                                        progress = pct.coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = if (pct > 1f) ErrorColor else if (pct > 0.75f) WarningColor else PrimaryColor,
                                    )
                                    Text(
                                        "Used: ${used.toInt()} / Daily limit: ${limit.toInt()} (remaining ${rem.toInt()})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            // (late) suffix matches the web
                                            // timeline (employee.js ev.late
                                            // renders "(late)"). Server-side
                                            // `late` is set when an event
                                            // fires outside the SLA window.
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(ev.eventType.replace("_", " "), fontWeight = FontWeight.Bold)
                                                if (ev.late == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "(late)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = ErrorColor,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            Text(formatDate(ev.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                
                // Expenses List — show every expense for this task. The
                // Budget Usage card above has a day picker but it drives
                // only the budget numbers; filtering the expense list by
                // the same date hid expenses whose txnDate (often OCR-
                // derived from the receipt's printed date) fell outside
                // the picker's reachable range, making them feel "lost".
                item {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                }
                if (expenses.isEmpty()) {
                    item {
                        Text(
                            "No expenses yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                Button(
                    onClick = {
                        val reason = checkOutReason
                        showCheckOutReasonDialog = false
                        scope.launch {
                            performCheckOut(apiService, taskId, reason, context, ::refresh)
                        }
                    },
                    enabled = checkOutReason.trim().isNotEmpty()
                ) {
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

    // Early check-in dialog (pre-flight): pops when the employee taps Check
    // In before SLA start. Mirrors the late-checkout dialog's structure.
    if (showEarlyCheckInDialog) {
        AlertDialog(
            onDismissRequest = { showEarlyCheckInDialog = false },
            title = { Text("Check in early") },
            text = {
                Column {
                    Text("You are checking in before the SLA start time. Please add a brief reason so we can record it.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = earlyCheckInReason,
                        onValueChange = { earlyCheckInReason = it },
                        label = { Text("Reason") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = earlyCheckInReason
                        showEarlyCheckInDialog = false
                        scope.launch {
                            performCheckIn(apiService, taskId, reason, context, ::refresh)
                        }
                    },
                    enabled = earlyCheckInReason.trim().isNotEmpty()
                ) {
                    Text("Submit & check in")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEarlyCheckInDialog = false }) {
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
    onSubmit: (String, Double, String?, String?, Int?) -> Unit,
    draft: DraftExpense?,
    uploading: Boolean,
    task: Task?,
    expenses: List<Expense>,
    viewDate: String,
    categories: List<String> = listOf("Hotel", "Food", "Travel", "Other")
) {
    var category by remember(draft) { mutableStateOf(draft?.category ?: "") }
    var total by remember(draft) { mutableStateOf(draft?.total?.toString() ?: "") }
    var hotelCheckIn by remember(draft) { mutableStateOf(draft?.hotelCheckIn ?: "") }
    var hotelCheckOut by remember(draft) { mutableStateOf(draft?.hotelCheckOut ?: "") }
    var expanded by remember { mutableStateOf(false) }

    // Derive nights from the two YYYY-MM-DD inputs. Matches the server's
    // budget helper (Budget.applicableDates) which treats checkout as the
    // first non-consumed night.
    val nightsComputed: Int? = remember(hotelCheckIn, hotelCheckOut) {
        try {
            if (hotelCheckIn.length >= 10 && hotelCheckOut.length >= 10) {
                val ci = java.time.LocalDate.parse(hotelCheckIn.substring(0, 10))
                val co = java.time.LocalDate.parse(hotelCheckOut.substring(0, 10))
                val d = java.time.temporal.ChronoUnit.DAYS.between(ci, co).toInt()
                if (d > 0) d else null
            } else null
        } catch (_: Exception) { null }
    }

    // Remaining budget for the *selected* IST day (the same day the Budget
    // Usage card is showing, driven by the parent's day picker). Matches
    // the web app where the viewDate controls both the budget card and the
    // per-category remaining preview in the dialog. For hotel multi-night
    // spreads the server's per-date allocation still applies at finalize
    // time, but this preview warns on obvious overspend for that day.
    val remaining = remember(task, expenses, viewDate) {
        com.fieldops.app.utils.Budget.remainingByCategory(task, expenses, viewDate)
    }
    val dailyLimit = remember(task, category) {
        if (category.isNotEmpty()) com.fieldops.app.utils.Budget.dailyLimitFor(task, category) else 0.0
    }
    val remainingForCat = if (category.isNotEmpty()) remaining[category] ?: dailyLimit else null
    val viewDateLabel = remember(viewDate) {
        try {
            java.time.LocalDate.parse(viewDate)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
        } catch (_: Exception) { viewDate }
    }

    val parsedTotal = total.toDoubleOrNull()
    val ocrTotal = draft?.total
    val totalEdited = parsedTotal != null && ocrTotal != null && kotlin.math.abs(parsedTotal - ocrTotal) > 0.009

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft?.expenseId != null) "Edit Expense" else "Add Expense") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                        Text("Uploading & Scanning...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Receipt Uploaded", color = SuccessColor, fontWeight = FontWeight.Bold)

                    // OCR summary block. Same fields the web employee view
                    // renders above the amount field so the user can sanity
                    // check what the scanner picked up before they commit.
                    val hasOcr = !draft.merchant.isNullOrBlank() ||
                        !draft.txnDate.isNullOrBlank() ||
                        !draft.currency.isNullOrBlank()
                    if (hasOcr) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                "Extracted from receipt",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!draft.merchant.isNullOrBlank()) {
                                Text(
                                    "Merchant: ${draft.merchant}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!draft.txnDate.isNullOrBlank()) {
                                Text(
                                    "Date: ${draft.txnDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!draft.currency.isNullOrBlank()) {
                                Text(
                                    "Currency: ${draft.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

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

                    // Warn when the employee's typed total differs from what
                    // OCR picked up — they might have mis-keyed a digit, or
                    // they are deliberately overriding (in which case the
                    // admin sees both values and a note). Either way, make
                    // it visible.
                    if (totalEdited) {
                        Text(
                            "Amount differs from scanned receipt (₹${ocrTotal}). Admin will see this as an override.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningColor
                        )
                    }

                    // Hotel-only fields. The backend's per-day budget helper
                    // needs hotelCheckIn/hotelCheckOut (YYYY-MM-DD) so a
                    // multi-night stay is spread across nights instead of
                    // crashing into a single-day limit.
                    if (category.equals("Hotel", ignoreCase = true)) {
                        Text(
                            "Hotel stay",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = hotelCheckIn,
                            onValueChange = { hotelCheckIn = it },
                            label = { Text("Check-in (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = hotelCheckOut,
                            onValueChange = { hotelCheckOut = it },
                            label = { Text("Check-out (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (nightsComputed != null) {
                            Text(
                                "Nights: $nightsComputed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (hotelCheckIn.isNotEmpty() || hotelCheckOut.isNotEmpty()) {
                            Text(
                                "Enter valid dates with check-out after check-in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningColor
                            )
                        }
                    }

                    // Remaining budget preview. Shown only after a category
                    // is picked so the number is meaningful.
                    if (remainingForCat != null) {
                        val overLimit = parsedTotal != null && parsedTotal > remainingForCat + 0.01
                        Text(
                            "Remaining on $viewDateLabel ($category, IST): ₹${remainingForCat.toInt()} of ₹${dailyLimit.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overLimit) ErrorColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (overLimit) {
                            Text(
                                "This exceeds the remaining budget for $viewDateLabel — will need admin review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (draft?.blobUrl != null) {
                val amount = total.toDoubleOrNull()
                val isHotel = category.equals("Hotel", ignoreCase = true)
                // Hotel submissions without a valid night count would force
                // the server's helper to treat the whole amount as one-day
                // spend and send to PENDING_REVIEW unnecessarily — block it
                // here so the employee fixes the dates first.
                val hotelValid = !isHotel || nightsComputed != null
                val ready = category.isNotEmpty() && amount != null && amount > 0 && hotelValid
                Button(
                    onClick = {
                        if (ready && amount != null) {
                            onSubmit(
                                category,
                                amount,
                                if (isHotel) hotelCheckIn.takeIf { it.isNotBlank() } else null,
                                if (isHotel) hotelCheckOut.takeIf { it.isNotBlank() } else null,
                                if (isHotel) nightsComputed else null
                            )
                        }
                    },
                    enabled = !uploading && ready
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

/** Fire /api/tasks/checkin, handle response, refresh on success, toast on
 *  failure. Shared between the normal check-in button and the
 *  early-check-in-reason dialog submit button. */
private suspend fun performCheckIn(
    apiService: ApiService,
    taskId: String,
    reason: String?,
    context: android.content.Context,
    refresh: () -> Unit
) {
    try {
        val res = apiService.checkIn(CheckInRequest("default", taskId, reason))
        if (res.isSuccessful) {
            refresh()
        } else {
            val errBody = res.errorBody()?.string().orEmpty()
            android.util.Log.e("TaskDetail", "Check-in HTTP ${res.code()}: $errBody")
            android.widget.Toast.makeText(
                context,
                "Check-in failed (HTTP ${res.code()}): $errBody",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("TaskDetail", "Check-in failed", e)
        android.widget.Toast.makeText(
            context,
            "Check-in failed: ${e.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

/** Same contract as performCheckIn but for /api/tasks/checkout. */
private suspend fun performCheckOut(
    apiService: ApiService,
    taskId: String,
    reason: String?,
    context: android.content.Context,
    refresh: () -> Unit
) {
    try {
        val res = apiService.checkOut(CheckOutRequest("default", taskId, reason))
        if (res.isSuccessful) {
            refresh()
        } else {
            val errBody = res.errorBody()?.string().orEmpty()
            android.util.Log.e("TaskDetail", "Check-out HTTP ${res.code()}: $errBody")
            android.widget.Toast.makeText(
                context,
                "Check-out failed (HTTP ${res.code()}): $errBody",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("TaskDetail", "Check-out failed", e)
        android.widget.Toast.makeText(
            context,
            "Check-out failed: ${e.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

data class DraftExpense(
    val blobUrl: String? = null,
    val total: Double? = null,
    val category: String? = null,
    val expenseId: String? = null,
    // OCR extraction surfaced to the user in the Add Expense dialog so they
    // can see what the scanner picked up before accepting the suggested total.
    val merchant: String? = null,
    val txnDate: String? = null,
    val currency: String? = null,
    // Hotel-only fields when the employee is submitting a multi-night stay.
    val hotelCheckIn: String? = null,
    val hotelCheckOut: String? = null,
    val nights: Int? = null
)

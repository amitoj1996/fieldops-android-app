package com.fieldops.app.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Employee-facing slice of the FieldOps API.
 *
 * Admin endpoints (task CRUD, product catalog, user directory, expense
 * approval, report CSV) live on the web console and are intentionally
 * NOT declared here — adding them would let a rogue client script call
 * them from the app regardless of UI affordances. The backend still
 * enforces admin-only access via _ensure_admin.
 */
interface ApiService {
    @GET("/.auth/me")
    suspend fun getMe(): Response<AuthResponse>

    // --- Tasks ---
    // Paged response since web commit 153091b. Use `drainPaged` to fetch
    // the full list across continuation tokens.
    @GET("/api/tasks")
    suspend fun getTasks(
        @Query("tenantId") tenantId: String = "default",
        @Query("continuationToken") continuationToken: String? = null
    ): Response<PagedResponse<Task>>

    @POST("/api/tasks/checkin")
    suspend fun checkIn(@Body body: CheckInRequest): Response<Any>

    @POST("/api/tasks/checkout")
    suspend fun checkOut(@Body body: CheckOutRequest): Response<Any>

    @GET("/api/tasks/events")
    suspend fun getTaskEvents(
        @Query("taskId") taskId: String,
        @Query("tenantId") tenantId: String = "default"
    ): Response<List<TaskEvent>>

    @GET("/api/tasks/report/sas")
    suspend fun getReportSas(
        @Query("taskId") taskId: String,
        @Query("filename") filename: String
    ): Response<SasResponse>

    @POST("/api/tasks/report/uploaded")
    suspend fun reportUploaded(@Body body: ReportUploadedRequest): Response<Task>

    // Short-lived read SAS for a task's performance report.
    @GET("/api/tasks/report/read")
    suspend fun getReportReadUrl(
        @Query("taskId") taskId: String,
        @Query("tenantId") tenantId: String = "default"
    ): Response<ReportReadResponse>

    // --- Expenses ---
    @GET("/api/expenses/byTask")
    suspend fun getExpensesByTask(
        @Query("taskId") taskId: String,
        @Query("tenantId") tenantId: String = "default"
    ): Response<List<Expense>>

    @POST("/api/expenses/finalize")
    suspend fun finalizeExpense(@Body body: FinalizeExpenseRequest): Response<FinalizeExpenseResponse>

    @GET("/api/receipts/readSas")
    suspend fun getReceiptReadSas(
        @Query("taskId") taskId: String,
        @Query("filename") filename: String,
        @Query("minutes") minutes: Int = 5
    ): Response<ReadSasResponse>

    @GET("/api/receipts/sas")
    suspend fun getReceiptSas(
        @Query("taskId") taskId: String,
        @Query("filename") filename: String
    ): Response<SasResponse>

    @POST("/api/receipts/ocr")
    suspend fun ocrReceipt(@Body body: OcrRequest): Response<OcrResponse>

    @POST("/api/tasks/report/uploaded")
    suspend fun finalizeReport(@Body body: Map<String, String>): Response<Any>

    // --- Generic Upload (Azure Blob via SAS) ---
    @PUT
    @Headers("x-ms-blob-type: BlockBlob")
    suspend fun uploadBlob(@Url url: String, @Body file: RequestBody): Response<Unit>
}

// --- Data Classes ---
data class ReadSasResponse(val readUrl: String, val blobUrl: String?)

data class ReportReadResponse(
    val readUrl: String?,
    val expiresInSeconds: Int?,
    val legacy: Boolean? = null,
    val error: String? = null
)

// Cosmos pager envelope. When continuationToken is non-null, there's at
// least one more page available via the same endpoint.
data class PagedResponse<T>(
    val items: List<T>?,
    val continuationToken: String?
)

data class AuthResponse(val clientPrincipal: ClientPrincipal?)
data class ClientPrincipal(
    val userDetails: String,
    val userRoles: List<String>?
)

data class Task(
    val id: String,
    val title: String?,
    val status: String?,
    val slaStart: String?,
    val slaEnd: String?,
    val assignee: String?,
    val items: List<TaskItem>?,
    val slaBreached: Boolean?,
    // Legacy field: pre-PR 3 uploads embedded a 1-year SAS directly on the
    // task. New uploads populate reportBlobName and clear reportUrl. Treat
    // either as "report uploaded" and mint the read URL through
    // /api/tasks/report/read.
    val reportUrl: String?,
    val reportBlobName: String?,
    val reportBlobContainer: String?,
    val reportUploadedAt: String?,
    val reportTemplate: String?,
    val expenseLimits: Map<String, Double>?,
    val createdAt: String? = null
) {
    /** True when either the new blob field or the legacy SAS URL is present. */
    val hasReport: Boolean
        get() = !reportBlobName.isNullOrEmpty() || !reportUrl.isNullOrEmpty()
}

data class TaskItem(
    val productId: String?,
    val product: String?,
    val qty: Int?,
    val quantity: Int?
)

data class CheckInRequest(val tenantId: String, val taskId: String)
data class CheckOutRequest(val tenantId: String, val taskId: String, val reason: String? = null)

data class TaskEvent(
    val id: String,
    val eventType: String,
    val ts: String,
    val late: Boolean?,
    val reason: String?
)

data class SasResponse(val uploadUrl: String, val blobUrl: String)
data class ReportUploadedRequest(val tenantId: String, val taskId: String, val blobUrl: String)

data class Expense(
    val id: String,
    val taskId: String?,
    val category: String?,
    val total: Double?,
    val editedTotal: Double?,
    val approval: ApprovalStatus?,
    val createdAt: String?,
    val blobPath: String?,
    val submittedBy: String?,
    // Populated by the server budget helper. Needed for per-day allocation
    // math so the mobile UI agrees with the server's auto-approve decision.
    val txnDate: String? = null,
    val hotelCheckIn: String? = null,
    val hotelCheckOut: String? = null,
    val nights: Int? = null,
    // Server-returned payment status; "PAID" after a payout run.
    val paymentStatus: String? = null,
    val merchant: String? = null
)

data class ApprovalStatus(val status: String?)

data class FinalizeExpenseRequest(
    val tenantId: String,
    val taskId: String,
    val blobPath: String? = null,
    val category: String,
    val total: Double,
    val expenseId: String? = null
)

data class FinalizeExpenseResponse(val approval: ApprovalStatus?)

data class OcrRequest(val tenantId: String, val taskId: String, val filename: String, val save: Boolean)
data class OcrResponse(val blobPath: String?, val ocr: OcrData?)
data class OcrData(val merchant: String?, val total: Double?, val date: String?, val currency: String?)

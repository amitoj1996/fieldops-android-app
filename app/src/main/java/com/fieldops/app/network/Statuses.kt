package com.fieldops.app.network

/**
 * Canonical set of status strings the backend emits. Mirrors the values
 * used in api/routes/expenses.py and api/routes/tasks.py. Keep in sync
 * whenever the server vocabulary changes — otherwise UI falls through to
 * the generic badge branch and loses its intended styling.
 */
object Statuses {
    // Expense approval
    const val AUTO_APPROVED = "AUTO_APPROVED"
    const val APPROVED = "APPROVED"
    const val PENDING_REVIEW = "PENDING_REVIEW"
    const val REJECTED = "REJECTED"
    const val PAID = "PAID"

    // Task status
    const val ASSIGNED = "ASSIGNED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"

    /** True when the status represents realized (approved) spend. */
    fun isApproved(s: String?): Boolean =
        s == APPROVED || s == AUTO_APPROVED

    /** True when the status represents consumed budget (approved OR pending,
     *  which is how the server's budget helper reserves capacity). */
    fun reservesBudget(s: String?): Boolean =
        s == APPROVED || s == AUTO_APPROVED || s == PENDING_REVIEW
}

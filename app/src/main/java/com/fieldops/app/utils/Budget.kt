package com.fieldops.app.utils

import com.fieldops.app.network.Expense
import com.fieldops.app.network.Task
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Mirrors api/shared/budget.py and lib/budget.js. Keep in sync with the
 * server — the UI's "remaining" display must agree with the server's
 * auto-approve decision, or employees see confusing PENDING_REVIEW
 * outcomes on amounts the UI said were within budget.
 *
 * India-only app: fixed +5:30 IST, no DST.
 */
object Budget {

    private val IST: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)

    /** Parse a leading YYYY-MM-DD; null on anything else. */
    private fun parseYmd(s: String?): LocalDate? {
        if (s.isNullOrEmpty() || s.length < 10) return null
        return try {
            LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    /** UTC ISO timestamp -> IST calendar date. */
    private fun istDateOfIso(iso: String?): LocalDate? {
        if (iso.isNullOrEmpty()) return null
        return try {
            // Handle both "Z" and "+00:00" suffixes.
            val normalized = if (iso.endsWith("Z")) iso.substring(0, iso.length - 1) + "+00:00" else iso
            OffsetDateTime.parse(normalized).toInstant().atOffset(IST).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    /** Ordered list of IST dates this expense consumes budget on. */
    fun applicableDates(e: Expense): List<LocalDate> {
        if (e.category.equals("Hotel", ignoreCase = true)) {
            val ci: LocalDate? = parseYmd(e.hotelCheckIn)
            val co: LocalDate? = parseYmd(e.hotelCheckOut)
            if (ci != null && co != null && co.isAfter(ci)) {
                val days = mutableListOf<LocalDate>()
                var d: LocalDate = ci
                while (d.isBefore(co)) {
                    days.add(d)
                    d = d.plusDays(1)
                }
                return days
            }
        }
        val d: LocalDate? = parseYmd(e.txnDate) ?: istDateOfIso(e.createdAt)
        return if (d != null) listOf(d) else emptyList()
    }

    private fun amountOf(e: Expense): Double =
        (e.editedTotal ?: e.total ?: 0.0)

    /** Spread the expense's amount across its applicable dates. */
    fun allocationByDate(e: Expense, dailyLimit: Double): Map<LocalDate, Double> {
        val dates = applicableDates(e)
        if (dates.isEmpty()) return emptyMap()
        val amount = amountOf(e)
        if (dates.size == 1) return mapOf(dates[0] to amount)
        val result = mutableMapOf<LocalDate, Double>()
        var remaining = amount
        val limit = max(0.0, dailyLimit)
        for ((i, d) in dates.withIndex()) {
            val isLast = i == dates.size - 1
            if (isLast) {
                result[d] = max(0.0, remaining)
            } else {
                val use = if (limit > 0) min(limit, remaining) else remaining
                result[d] = use
                remaining -= use
            }
        }
        return result
    }

    fun dailyLimitFor(task: Task?, category: String): Double {
        val limits = task?.expenseLimits ?: return 1000.0
        return limits[category] ?: limits["Other"] ?: 1000.0
    }

    /**
     * Remaining budget for a new expense in each category on the given IST
     * date. Non-rejected peers count against the limit. Matches the server's
     * per-day consumption model for single-day categories and greedy hotel
     * allocation for multi-night stays.
     */
    fun remainingByCategory(
        task: Task?,
        expenses: List<Expense>,
        viewDate: String
    ): Map<String, Double> {
        val vd = parseYmd(viewDate)
        val result = mutableMapOf<String, Double>()
        for (cat in listOf("Hotel", "Food", "Travel", "Other")) {
            val limit = dailyLimitFor(task, cat)
            var consumed = 0.0
            for (e in expenses) {
                if (!(e.category ?: "Other").equals(cat, ignoreCase = true)) continue
                val status = e.approval?.status
                if (status == "REJECTED") continue
                val alloc = allocationByDate(e, limit)
                consumed += alloc[vd] ?: 0.0
            }
            result[cat] = max(0.0, limit - consumed)
        }
        return result
    }
}

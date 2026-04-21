package com.fieldops.app.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDate(dateStr: String?): String {
    if (dateStr == null) return "N/A"
    return try {
        val instant = Instant.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .withLocale(Locale.getDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        dateStr
    }
}

object DateUtils {
    private val IST: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)
    private val YMD = DateTimeFormatter.ISO_LOCAL_DATE

    /** Today's IST calendar date as a "YYYY-MM-DD" string. */
    fun getTodayIST(): String =
        LocalDate.now(IST).format(YMD)
}

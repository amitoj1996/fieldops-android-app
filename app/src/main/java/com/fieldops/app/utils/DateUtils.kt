package com.fieldops.app.utils

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * India-only app: all timestamps from the backend are UTC ISO strings;
 * everything the user sees should render in Asia/Kolkata (+5:30, no DST).
 * These helpers normalise both ends — parse and render — through the
 * fixed IST offset so a mis-set device clock or a traveling user never
 * makes the SLA appear shifted.
 */
private val IST: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)
private val YMD = DateTimeFormatter.ISO_LOCAL_DATE
private val IST_DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
private val IST_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Parse a server UTC ISO timestamp. Accepts both "Z" and offset suffixes.
 *  Returns null on malformed input. */
private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (_: Exception) {
            null
        }
    }
}

/** "21 Apr 2026, 2:30 PM" in IST. Falls back to "—" on null/parse failure. */
fun formatDate(dateStr: String?): String {
    val instant = parseInstant(dateStr) ?: return "—"
    return IST_DATE_TIME.format(instant.atOffset(IST))
}

/** "21 Apr 2026" in IST. Used for date-only columns. */
fun formatDateOnly(dateStr: String?): String {
    val instant = parseInstant(dateStr) ?: return "—"
    return IST_DATE.format(instant.atOffset(IST))
}

/** Milliseconds-since-epoch for a server UTC ISO timestamp, or null. Used
 *  for SLA proximity arithmetic (startsSoon / endsSoon / overdue). */
fun parseToMillis(iso: String?): Long? = parseInstant(iso)?.toEpochMilli()

object DateUtils {
    /** Today's IST calendar date as a "YYYY-MM-DD" string. */
    fun getTodayIST(): String = LocalDate.now(IST).format(YMD)
}

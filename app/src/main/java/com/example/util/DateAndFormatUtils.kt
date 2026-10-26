package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateAndFormatUtils {
  private val arabicMonthNames =
    arrayOf(
      "يناير",
      "فبراير",
      "مارس",
      "أبريل",
      "مايو",
      "يونيو",
      "يوليو",
      "أغسطس",
      "سبتمبر",
      "أكتوبر",
      "نوفمبر",
      "ديسمبر",
    )

  fun todayEpochMillis(): Long = System.currentTimeMillis()

  fun epochMillisToIsoDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMillis))
  }

  fun isoDateToBillingMonth(isoDate: String): String {
    val sanitized = normalizeIsoDate(isoDate)
    return sanitized.substring(0, 7)
  }

  fun normalizeIsoDate(raw: String?): String {
    if (raw.isNullOrBlank()) return epochMillisToIsoDate(todayEpochMillis())
    // Convert any Eastern Arabic digits to ASCII digits first
    val ascii =
      raw
        .trim()
        .map { ch ->
          when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            '/' -> '-'
            else -> ch
          }
        }
        .joinToString("")

    val regexYmd = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
    val matchYmd = regexYmd.find(ascii)
    if (matchYmd != null) {
      val y = matchYmd.groupValues[1].toIntOrNull()?.coerceIn(2000, 2100) ?: 2026
      val m = matchYmd.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 1
      val d = matchYmd.groupValues[3].toIntOrNull()?.coerceIn(1, 31) ?: 1
      return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    val regexDmy = Regex("""^(\d{1,2})-(\d{1,2})-(\d{4})""")
    val matchDmy = regexDmy.find(ascii)
    if (matchDmy != null) {
      val d = matchDmy.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: 1
      val m = matchDmy.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 1
      val y = matchDmy.groupValues[3].toIntOrNull()?.coerceIn(2000, 2100) ?: 2026
      return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    return epochMillisToIsoDate(todayEpochMillis())
  }

  fun isoDateToEpochMillis(isoDate: String): Long {
    val normalized = normalizeIsoDate(isoDate)
    return try {
      val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      sdf.timeZone = TimeZone.getTimeZone("UTC")
      sdf.parse(normalized)?.time ?: todayEpochMillis()
    } catch (_: Exception) {
      todayEpochMillis()
    }
  }

  fun formatBillingMonthArabic(yearMonth: String, shortYear: Boolean = false): String {
    val parts = yearMonth.split("-")
    if (parts.size < 2) return yearMonth
    val year = parts[0]
    val monthIdx = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
    val monthName = arabicMonthNames[monthIdx]
    val yearSuffix = if (shortYear && year.length == 4) year.takeLast(2) else year
    return "$monthName $yearSuffix"
  }

  fun formatIsoDateArabic(isoDate: String): String {
    val parts = normalizeIsoDate(isoDate).split("-")
    if (parts.size < 3) return isoDate
    val year = parts[0]
    val monthIdx = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
    val day = parts[2].toIntOrNull() ?: 1
    return "$day ${arabicMonthNames[monthIdx]} $year"
  }

  fun currentBillingMonth(): String {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return String.format(Locale.US, "%04d-%02d", year, month)
  }

  fun formatMoney(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
      String.format(Locale.US, "%,.0f", amount)
    } else {
      String.format(Locale.US, "%,.2f", amount)
    }
  }

  fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
      String.format(Locale.US, "%.0f", value)
    } else {
      String.format(Locale.US, "%.1f", value)
    }
  }

  fun parseFlexibleDouble(input: String): Double? {
    val normalized =
      input
        .trim()
        .map { ch ->
          when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            '٫', ',' -> '.'
            else -> ch
          }
        }
        .joinToString("")
    return normalized.toDoubleOrNull()
  }
}

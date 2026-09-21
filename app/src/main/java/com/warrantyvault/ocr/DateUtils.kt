package com.warrantyvault.ocr

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Calendar-safe date helpers. Central place for date math so no screen ever does
 * `now + months * 30 days` again.
 */
object DateUtils {

    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun fromEpochMillis(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Adds calendar months, clamping to the last valid day (Jan 31 + 1 month = Feb 28/29). */
    fun addMonths(date: LocalDate, months: Int): LocalDate {
        if (months == 0) return date
        val base = date.plusMonths(months.toLong())
        val lastDay = YearMonth.from(base).lengthOfMonth()
        return if (date.dayOfMonth > lastDay) base.withDayOfMonth(lastDay) else base
    }

    fun addYears(date: LocalDate, years: Int): LocalDate = addMonths(date, years * 12)

    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(from, to)
}

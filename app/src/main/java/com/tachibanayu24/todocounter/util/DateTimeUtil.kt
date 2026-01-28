package com.tachibanayu24.todocounter.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtil {

    private val RFC3339_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"))

    private val RFC3339_MILLIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneId.of("UTC"))

    fun parseRfc3339ToDate(dateString: String): Date? {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            isoFormat.parse(dateString)
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(dateString)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun parseRfc3339ToLocalDate(dateString: String): LocalDate? {
        return try {
            val instant = Instant.parse(dateString)
            instant.atZone(ZoneId.of("UTC")).toLocalDate()
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateString.substring(0, 10))
            } catch (e: Exception) {
                null
            }
        }
    }

    fun parseCompletedDate(completedString: String): Pair<LocalDate, Long>? {
        return try {
            val instant = Instant.parse(completedString)
            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            Pair(localDate, instant.toEpochMilli())
        } catch (e: Exception) {
            try {
                val localDate = LocalDate.parse(completedString.substring(0, 10))
                val timestamp = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                Pair(localDate, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun LocalDate.toRfc3339(): String {
        return this.atStartOfDay(ZoneId.of("UTC"))
            .format(RFC3339_MILLIS_FORMAT)
    }

    fun formatRfc3339(instant: Instant): String {
        return RFC3339_FORMAT.format(instant)
    }
}

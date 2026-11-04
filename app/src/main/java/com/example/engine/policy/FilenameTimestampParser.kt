package com.example.engine.policy

import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.MetadataGroup
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import java.util.regex.Pattern

object FilenameTimestampParser {

    // Matches Samsung format: 20261014_170334 with optional prefix/suffix
    // e.g., 20261014_170334.heic, Screenshot_20261014_170334.png, IMG_20261014_170334_001.jpg
    private val SAMSUNG_PATTERN = Pattern.compile(
        "(?:^|[^0-9])((?:19|20)\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[_-]([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)(?:[^0-9]|$)"
    )

    // Matches formatted ISO-like: 2026-10-14_17-03-34 or 2026-10-14 17.03.34
    private val ISO_FILENAME_PATTERN = Pattern.compile(
        "(?:^|[^0-9])((?:19|20)\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])[ _-]([01]\\d|2[0-3])[.:-]([0-5]\\d)[.:-]([0-5]\\d)(?:[^0-9]|$)"
    )

    // WhatsApp style: IMG-20261014-WA0001 (date only)
    private val WA_PATTERN = Pattern.compile(
        "IMG-((?:19|20)\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])-WA"
    )

    fun parse(fileName: String, targetTimeZone: TimeZone = TimeZone.getDefault()): DateCandidate? {
        // Strip extension
        val nameWithoutExt = fileName.substringBeforeLast('.')

        // 1. Try Samsung / Android standard pattern (YYYYMMDD_HHMMSS)
        val samsungMatcher = SAMSUNG_PATTERN.matcher(nameWithoutExt)
        if (samsungMatcher.find()) {
            val year = samsungMatcher.group(1)?.toIntOrNull() ?: return null
            val month = samsungMatcher.group(2)?.toIntOrNull() ?: return null
            val day = samsungMatcher.group(3)?.toIntOrNull() ?: return null
            val hour = samsungMatcher.group(4)?.toIntOrNull() ?: return null
            val minute = samsungMatcher.group(5)?.toIntOrNull() ?: return null
            val second = samsungMatcher.group(6)?.toIntOrNull() ?: return null

            if (!isValidCalendarDate(year, month, day, hour, minute, second)) {
                return null
            }

            val calendar = Calendar.getInstance(targetTimeZone).apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
            }
            val epoch = calendar.timeInMillis
            val rawMatched = "${samsungMatcher.group(1)}${samsungMatcher.group(2)}${samsungMatcher.group(3)}_${samsungMatcher.group(4)}${samsungMatcher.group(5)}${samsungMatcher.group(6)}"

            val isSamsungFormat = fileName.matches(Regex("^[0-9]{8}_[0-9]{6}.*"))
            val label = if (isSamsungFormat) "Samsung Camera Filename" else "Android Camera Filename"

            return DateCandidate(
                id = UUID.randomUUID().toString(),
                tagName = "$label (YYYYMMDD_HHMMSS)",
                metadataGroup = MetadataGroup.FILENAME,
                originalRawValue = rawMatched,
                parsedEpochMillis = epoch,
                formattedParsedDate = DateCandidate.formatEpoch(epoch, targetTimeZone),
                timeZoneInfo = "Device Local (${targetTimeZone.displayName})",
                hasExplicitTimeZone = false,
                confidence = if (isSamsungFormat) 75 else 70,
                suitabilityReason = "Filename matches camera capture pattern. Validated calendar constraints ($year-$month-$day $hour:$minute:$second). Interpreted in local device time zone without unverified shifts.",
                status = CandidateStatus.ALTERNATIVE
            )
        }

        // 2. Try ISO Filename pattern
        val isoMatcher = ISO_FILENAME_PATTERN.matcher(nameWithoutExt)
        if (isoMatcher.find()) {
            val year = isoMatcher.group(1)?.toIntOrNull() ?: return null
            val month = isoMatcher.group(2)?.toIntOrNull() ?: return null
            val day = isoMatcher.group(3)?.toIntOrNull() ?: return null
            val hour = isoMatcher.group(4)?.toIntOrNull() ?: return null
            val minute = isoMatcher.group(5)?.toIntOrNull() ?: return null
            val second = isoMatcher.group(6)?.toIntOrNull() ?: return null

            if (!isValidCalendarDate(year, month, day, hour, minute, second)) {
                return null
            }

            val calendar = Calendar.getInstance(targetTimeZone).apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
            }
            val epoch = calendar.timeInMillis

            return DateCandidate(
                id = UUID.randomUUID().toString(),
                tagName = "ISO Structured Filename",
                metadataGroup = MetadataGroup.FILENAME,
                originalRawValue = isoMatcher.group(0)?.trim() ?: "",
                parsedEpochMillis = epoch,
                formattedParsedDate = DateCandidate.formatEpoch(epoch, targetTimeZone),
                timeZoneInfo = "Device Local (${targetTimeZone.displayName})",
                hasExplicitTimeZone = false,
                confidence = 68,
                suitabilityReason = "Filename contains structured date-time string. Validated calendar date ($year-$month-$day $hour:$minute:$second).",
                status = CandidateStatus.ALTERNATIVE
            )
        }

        // 3. Try WhatsApp date pattern (Date only, default 12:00:00 noon)
        val waMatcher = WA_PATTERN.matcher(nameWithoutExt)
        if (waMatcher.find()) {
            val year = waMatcher.group(1)?.toIntOrNull() ?: return null
            val month = waMatcher.group(2)?.toIntOrNull() ?: return null
            val day = waMatcher.group(3)?.toIntOrNull() ?: return null

            if (!isValidCalendarDate(year, month, day, 12, 0, 0)) {
                return null
            }

            val calendar = Calendar.getInstance(targetTimeZone).apply {
                clear()
                set(year, month - 1, day, 12, 0, 0)
            }
            val epoch = calendar.timeInMillis

            return DateCandidate(
                id = UUID.randomUUID().toString(),
                tagName = "WhatsApp Image Filename (Date Only)",
                metadataGroup = MetadataGroup.FILENAME,
                originalRawValue = waMatcher.group(0)?.trim() ?: "",
                parsedEpochMillis = epoch,
                formattedParsedDate = DateCandidate.formatEpoch(epoch, targetTimeZone),
                timeZoneInfo = "Device Local (Time approximated to 12:00:00)",
                hasExplicitTimeZone = false,
                confidence = 50,
                suitabilityReason = "Extracted date from WhatsApp image naming format. Lacks exact hours/seconds; defaulted to noon.",
                status = CandidateStatus.ALTERNATIVE
            )
        }

        return null
    }

    fun isValidCalendarDate(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Boolean {
        if (year !in 1990..2050) return false
        if (month !in 1..12) return false
        if (hour !in 0..23) return false
        if (minute !in 0..59) return false
        if (second !in 0..59) return false

        val maxDays = getDaysInMonth(year, month)
        return day in 1..maxDays
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }
    }
}

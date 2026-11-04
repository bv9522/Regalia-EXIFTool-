package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DateCandidate(
    val id: String,
    val tagName: String,
    val metadataGroup: MetadataGroup,
    val originalRawValue: String,
    val parsedEpochMillis: Long?,
    val formattedParsedDate: String,
    val timeZoneInfo: String,
    val hasExplicitTimeZone: Boolean,
    val confidence: Int, // 0 to 100
    val suitabilityReason: String,
    val status: CandidateStatus = CandidateStatus.ALTERNATIVE
) {
    companion object {
        fun formatEpoch(epochMillis: Long?, timeZone: TimeZone = TimeZone.getDefault()): String {
            if (epochMillis == null || epochMillis <= 0) return "Invalid date"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                this.timeZone = timeZone
            }
            return sdf.format(Date(epochMillis))
        }

        fun formatCompact(epochMillis: Long?): String {
            if (epochMillis == null || epochMillis <= 0) return "N/A"
            val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(epochMillis))
        }

        fun formatDate(epochMillis: Long?): String {
            if (epochMillis == null || epochMillis <= 0) return "N/A"
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date(epochMillis))
        }

        fun formatTime(epochMillis: Long?): String {
            if (epochMillis == null || epochMillis <= 0) return "N/A"
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(epochMillis))
        }
    }
}

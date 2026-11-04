package com.example.data.model

enum class OperationStatus(val label: String) {
    IDLE("Idle"),
    SCANNED("Scanned"),
    PREVIEW_READY("Ready to Update"),
    NO_USABLE_DATE("No Usable Date"),
    UP_TO_DATE("Already Matches"),
    PROCESSING("Applying..."),
    SUCCESS("Verified Success"),
    UNSUPPORTED_PROVIDER("Provider Unsupported"),
    FAILED("Operation Failed"),
    ROLLED_BACK("Rolled Back")
}

data class TimestampTask(
    val id: String,
    val fileItem: FileItem,
    val currentTimestamp: Long,
    val proposedTimestamp: Long?,
    val selectedCandidate: DateCandidate?,
    val allCandidates: List<DateCandidate> = emptyList(),
    val status: OperationStatus = OperationStatus.IDLE,
    val diagnosticMessage: String? = null,
    val verifiedTimestamp: Long? = null
) {
    val differenceMillis: Long?
        get() = proposedTimestamp?.let { it - currentTimestamp }

    fun formattedDifference(): String {
        val diff = differenceMillis ?: return "No change"
        val absDiff = kotlin.math.abs(diff)
        if (absDiff < 2000) return "Identical (±2s)"
        val seconds = (absDiff / 1000) % 60
        val minutes = (absDiff / (1000 * 60)) % 60
        val hours = (absDiff / (1000 * 60 * 60)) % 24
        val days = absDiff / (1000 * 60 * 60 * 24)

        val prefix = if (diff > 0) "+ " else "- "
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (parts.isEmpty()) parts.add("${seconds}s")
        return prefix + parts.joinToString(" ")
    }
}

data class StorageCapability(
    val canModifyTimestamp: Boolean,
    val methodUsed: String,
    val diagnosticNotes: String,
    val providerAuthority: String? = null
)

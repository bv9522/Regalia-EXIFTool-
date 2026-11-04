package com.example.engine.metadata.providers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.MediaCategory
import com.example.data.model.MetadataGroup
import com.example.data.model.NormalizedMetadata
import com.example.engine.metadata.MetadataProvider
import com.example.engine.policy.DateCandidateSelector
import com.example.engine.policy.FilenameTimestampParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class VideoMetadataProvider(
    private val candidateSelector: DateCandidateSelector = DateCandidateSelector()
) : MetadataProvider {

    override val providerId: String = "video_quicktime_provider"
    override val displayName: String = "QuickTime & MP4 Video Metadata Reader"

    private val supportedExtensions = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi", "ts")

    override fun canHandle(mimeType: String, extension: String): Boolean {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        return extension.lowercase() in supportedExtensions
    }

    override suspend fun extract(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        lastModified: Long
    ): NormalizedMetadata = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<DateCandidate>()
        val technical = mutableMapOf<String, String>()
        val rawTags = mutableMapOf<String, String>()
        var hasGps = false
        var gpsCoords: String? = null

        val extension = fileName.substringAfterLast('.', "").lowercase()

        // 1. Use MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)

            if (!dateStr.isNullOrBlank()) {
                rawTags["MediaMetadata:Date"] = dateStr
                val parsed = parseVideoDate(dateStr)
                if (parsed != null) {
                    candidates.add(
                        DateCandidate(
                            id = UUID.randomUUID().toString(),
                            tagName = "QuickTime / MP4 MediaCreateDate",
                            metadataGroup = MetadataGroup.QUICKTIME,
                            originalRawValue = dateStr,
                            parsedEpochMillis = parsed.first,
                            formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                            timeZoneInfo = parsed.second,
                            hasExplicitTimeZone = parsed.second.contains("UTC") || parsed.second.contains("Offset"),
                            confidence = 85,
                            suitabilityReason = "QuickTime/ISO container creation date. Note: QuickTime specification defines dates in UTC (1904 epoch); some smartphone cameras write local wall-clock time instead of true UTC.",
                            status = CandidateStatus.ALTERNATIVE
                        )
                    )
                }
            }

            if (durationMs != null && durationMs > 0) {
                val sec = (durationMs / 1000) % 60
                val min = (durationMs / (1000 * 60)) % 60
                val hrs = durationMs / (1000 * 60 * 60)
                technical["Duration"] = if (hrs > 0) {
                    String.format(Locale.US, "%02d:%02d:%02d", hrs, min, sec)
                } else {
                    String.format(Locale.US, "%02d:%02d", min, sec)
                }
            }

            if (width != null && height != null) {
                val rot = if (rotation != null && rotation != "0") " ($rotation°)" else ""
                technical["Resolution"] = "${width} x ${height} px$rot"
            }

            if (bitrate != null && bitrate > 0) {
                technical["Bitrate"] = "${bitrate / 1000} kbps"
            }

            if (!mime.isNullOrBlank()) {
                technical["Codec / Stream"] = mime
            }

            if (!location.isNullOrBlank()) {
                hasGps = true
                gpsCoords = location.trim()
                technical["Location"] = gpsCoords
            }

            retriever.release()
        } catch (e: Exception) {
            technical["Retriever Notice"] = "Video container read: ${e.message ?: "Retriever unavailable"}"
        }

        // 2. Direct MP4 mvhd Atom parse fallback if needed
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val mvhdEpoch = extractMvhdCreationTime(stream)
                if (mvhdEpoch != null && mvhdEpoch > 0) {
                    rawTags["MP4:mvhd_creation_time"] = mvhdEpoch.toString()
                    // Only add if not already present from retriever
                    if (candidates.none { it.metadataGroup == MetadataGroup.QUICKTIME }) {
                        candidates.add(
                            DateCandidate(
                                id = UUID.randomUUID().toString(),
                                tagName = "ISO/IEC 14496-12 mvhd Creation Date",
                                metadataGroup = MetadataGroup.QUICKTIME,
                                originalRawValue = "Epoch 1904 Seconds: $mvhdEpoch",
                                parsedEpochMillis = mvhdEpoch,
                                formattedParsedDate = DateCandidate.formatEpoch(mvhdEpoch),
                                timeZoneInfo = "QuickTime 1904 Epoch (UTC standard)",
                                hasExplicitTimeZone = true,
                                confidence = 82,
                                suitabilityReason = "Parsed from MP4 Movie Header (mvhd) box directly.",
                                status = CandidateStatus.ALTERNATIVE
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Samsung / Camera Filename Fallback
        val filenameCandidate = FilenameTimestampParser.parse(fileName)
        if (filenameCandidate != null) {
            candidates.add(filenameCandidate)
            rawTags["Filename:Pattern"] = filenameCandidate.originalRawValue
        }

        // 4. Filesystem Last Modified
        if (lastModified > 0) {
            candidates.add(
                DateCandidate(
                    id = UUID.randomUUID().toString(),
                    tagName = "Filesystem Last Modified",
                    metadataGroup = MetadataGroup.FILESYSTEM,
                    originalRawValue = lastModified.toString(),
                    parsedEpochMillis = lastModified,
                    formattedParsedDate = DateCandidate.formatEpoch(lastModified),
                    timeZoneInfo = "Filesystem Timestamp",
                    hasExplicitTimeZone = false,
                    confidence = 25,
                    suitabilityReason = "Current filesystem modification date. Frequently updated during downloads or file copies.",
                    status = CandidateStatus.ALTERNATIVE
                )
            )
        }

        // 5. Select best candidate per policy
        val (recommended, evaluatedCandidates) = candidateSelector.evaluate(candidates, MediaCategory.VIDEO)

        NormalizedMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = "video/$extension",
            fileExtension = extension,
            filesystemLastModified = lastModified,
            formattedFilesystemDate = DateCandidate.formatEpoch(lastModified),
            mediaCategory = MediaCategory.VIDEO,
            dateCandidates = evaluatedCandidates,
            recommendedCandidate = recommended,
            technicalDetails = technical,
            rawTags = rawTags,
            hasGps = hasGps,
            gpsCoordinates = gpsCoords
        )
    }

    private fun parseVideoDate(raw: String): Pair<Long, String>? {
        val trimmed = raw.trim()
        val formatsWithUtc = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        for (fmt in formatsWithUtc) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                val date = sdf.parse(trimmed)
                if (date != null) {
                    return date.time to "Explicit: UTC (QuickTime container spec)"
                }
            } catch (_: Exception) {}
        }

        val localFormats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyyMMdd HHmmss"
        )
        for (fmt in localFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
                val date = sdf.parse(trimmed)
                if (date != null) {
                    return date.time to "Device Local Time (No explicit zone in video tag)"
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Scans MP4 stream for 'mvhd' atom and extracts creation_time (1904 epoch)
     */
    private fun extractMvhdCreationTime(stream: InputStream): Long? {
        val buffer = ByteArray(4096)
        var bytesRead = stream.read(buffer)
        if (bytesRead <= 0) return null

        val mvhdTag = byteArrayOf('m'.code.toByte(), 'v'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte())
        for (i in 0 until bytesRead - 16) {
            if (buffer[i] == mvhdTag[0] && buffer[i + 1] == mvhdTag[1] && buffer[i + 2] == mvhdTag[2] && buffer[i + 3] == mvhdTag[3]) {
                val version = buffer[i + 4].toInt() and 0xFF
                val creationTimeSeconds: Long = if (version == 1) {
                    // 64-bit creation time (version 1)
                    var value: Long = 0
                    for (b in 8 until 16) {
                        value = (value shl 8) or ((buffer[i + b].toInt() and 0xFF).toLong())
                    }
                    value
                } else {
                    // 32-bit creation time (version 0)
                    var value: Long = 0
                    for (b in 8 until 12) {
                        value = (value shl 8) or ((buffer[i + b].toInt() and 0xFF).toLong())
                    }
                    value
                }

                // QuickTime epoch is 1904-01-01 00:00:00 UTC
                // Unix epoch is 1970-01-01 00:00:00 UTC
                // Difference is 2,082,844,800 seconds
                val macToUnixDeltaSeconds = 2082844800L
                val unixSeconds = creationTimeSeconds - macToUnixDeltaSeconds
                if (unixSeconds > 0) {
                    return unixSeconds * 1000L
                }
            }
        }
        return null
    }
}

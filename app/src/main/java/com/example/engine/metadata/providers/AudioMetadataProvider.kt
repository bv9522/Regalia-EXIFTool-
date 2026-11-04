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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class AudioMetadataProvider(
    private val candidateSelector: DateCandidateSelector = DateCandidateSelector()
) : MetadataProvider {

    override val providerId: String = "audio_retriever_provider"
    override val displayName: String = "Audio ID3 & Vorbis Metadata Reader"

    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "opus")

    override fun canHandle(mimeType: String, extension: String): Boolean {
        if (mimeType.startsWith("audio/", ignoreCase = true)) return true
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

        val extension = fileName.substringAfterLast('.', "").lowercase()

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()

            if (!title.isNullOrBlank()) technical["Title"] = title.trim()
            if (!artist.isNullOrBlank()) technical["Artist"] = artist.trim()
            if (!album.isNullOrBlank()) technical["Album"] = album.trim()

            if (durationMs != null && durationMs > 0) {
                val sec = (durationMs / 1000) % 60
                val min = durationMs / (1000 * 60)
                technical["Duration"] = String.format(Locale.US, "%02d:%02d", min, sec)
            }

            if (bitrate != null && bitrate > 0) {
                technical["Bitrate"] = "${bitrate / 1000} kbps"
            }

            if (!date.isNullOrBlank()) {
                rawTags["Audio:Date"] = date
                val parsed = parseAudioDate(date)
                if (parsed != null) {
                    candidates.add(
                        DateCandidate(
                            id = UUID.randomUUID().toString(),
                            tagName = "Audio Tag Date (Year/Date)",
                            metadataGroup = MetadataGroup.AUDIO_ID3,
                            originalRawValue = date,
                            parsedEpochMillis = parsed.first,
                            formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                            timeZoneInfo = parsed.second,
                            hasExplicitTimeZone = false,
                            confidence = 70,
                            suitabilityReason = "Recorded in audio ID3 or Vorbis container tags.",
                            status = CandidateStatus.ALTERNATIVE
                        )
                    )
                }
            }

            retriever.release()
        } catch (e: Exception) {
            technical["Audio Note"] = e.message ?: "Unable to read audio metadata"
        }

        // Filename fallback
        val filenameCandidate = FilenameTimestampParser.parse(fileName)
        if (filenameCandidate != null) {
            candidates.add(filenameCandidate)
            rawTags["Filename:Pattern"] = filenameCandidate.originalRawValue
        }

        // Filesystem Last Modified
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
                    suitabilityReason = "Filesystem last modified date.",
                    status = CandidateStatus.ALTERNATIVE
                )
            )
        }

        val (recommended, evaluatedCandidates) = candidateSelector.evaluate(candidates, MediaCategory.AUDIO)

        NormalizedMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = "audio/$extension",
            fileExtension = extension,
            filesystemLastModified = lastModified,
            formattedFilesystemDate = DateCandidate.formatEpoch(lastModified),
            mediaCategory = MediaCategory.AUDIO,
            dateCandidates = evaluatedCandidates,
            recommendedCandidate = recommended,
            technicalDetails = technical,
            rawTags = rawTags
        )
    }

    private fun parseAudioDate(dateRaw: String): Pair<Long, String>? {
        val trimmed = dateRaw.trim()
        // If 4-digit year e.g. "2023"
        if (trimmed.matches(Regex("^[12][0-9]{3}$"))) {
            val year = trimmed.toInt()
            val cal = java.util.Calendar.getInstance().apply {
                clear()
                set(year, 0, 1, 12, 0, 0)
            }
            return cal.timeInMillis to "Year only (approximated to Jan 1)"
        }

        val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
                val date = sdf.parse(trimmed)
                if (date != null) return date.time to "Device Local Time"
            } catch (_: Exception) {}
        }
        return null
    }
}

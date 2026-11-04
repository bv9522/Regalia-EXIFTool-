package com.example.engine.metadata

import android.content.Context
import android.net.Uri
import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.data.model.MediaCategory
import com.example.data.model.MetadataGroup
import com.example.data.model.NormalizedMetadata
import com.example.engine.metadata.providers.AudioMetadataProvider
import com.example.engine.metadata.providers.DocumentMetadataProvider
import com.example.engine.metadata.providers.ImageMetadataProvider
import com.example.engine.metadata.providers.VideoMetadataProvider
import com.example.engine.policy.DateCandidateSelector
import com.example.engine.policy.FilenameTimestampParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MetadataEngine(
    private val candidateSelector: DateCandidateSelector = DateCandidateSelector()
) {

    private val providers: List<MetadataProvider> = listOf(
        ImageMetadataProvider(candidateSelector),
        VideoMetadataProvider(candidateSelector),
        DocumentMetadataProvider(candidateSelector),
        AudioMetadataProvider(candidateSelector)
    )

    suspend fun extractMetadata(context: Context, fileItem: FileItem): NormalizedMetadata = withContext(Dispatchers.IO) {
        val extension = fileItem.name.substringAfterLast('.', "").lowercase()
        val provider = providers.firstOrNull { it.canHandle(fileItem.mimeType, extension) }

        val result = if (provider != null) {
            provider.extract(
                context = context,
                uri = fileItem.uri,
                fileName = fileItem.name,
                fileSize = fileItem.size,
                lastModified = fileItem.lastModified
            )
        } else {
            // Generic fallback for unrecognized file types
            extractGenericFallback(fileItem)
        }
        val path = fileItem.displayPath.ifBlank { fileItem.folderLocation() }
        result.copy(displayPath = path)
    }

    private fun extractGenericFallback(fileItem: FileItem): NormalizedMetadata {
        val candidates = mutableListOf<DateCandidate>()
        val rawTags = mutableMapOf<String, String>()

        val filenameCandidate = FilenameTimestampParser.parse(fileItem.name)
        if (filenameCandidate != null) {
            candidates.add(filenameCandidate)
            rawTags["Filename:Pattern"] = filenameCandidate.originalRawValue
        }

        if (fileItem.lastModified > 0) {
            candidates.add(
                DateCandidate(
                    id = UUID.randomUUID().toString(),
                    tagName = "Filesystem Last Modified",
                    metadataGroup = MetadataGroup.FILESYSTEM,
                    originalRawValue = fileItem.lastModified.toString(),
                    parsedEpochMillis = fileItem.lastModified,
                    formattedParsedDate = DateCandidate.formatEpoch(fileItem.lastModified),
                    timeZoneInfo = "Filesystem",
                    hasExplicitTimeZone = false,
                    confidence = 25,
                    suitabilityReason = "Filesystem last modified date.",
                    status = CandidateStatus.ALTERNATIVE
                )
            )
        }

        val (recommended, evaluated) = candidateSelector.evaluate(candidates, MediaCategory.OTHER)

        return NormalizedMetadata(
            fileName = fileItem.name,
            fileSize = fileItem.size,
            mimeType = fileItem.mimeType,
            fileExtension = fileItem.name.substringAfterLast('.', "").lowercase(),
            filesystemLastModified = fileItem.lastModified,
            formattedFilesystemDate = DateCandidate.formatEpoch(fileItem.lastModified),
            mediaCategory = MediaCategory.OTHER,
            dateCandidates = evaluated,
            recommendedCandidate = recommended,
            technicalDetails = mapOf("Format" to "Generic Binary / Unknown"),
            rawTags = rawTags
        )
    }
}

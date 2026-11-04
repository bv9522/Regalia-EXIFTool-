package com.example.engine.policy

import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.MediaCategory
import com.example.data.model.MetadataGroup

interface DateSelectionPolicy {
    fun selectBestCandidate(
        candidates: List<DateCandidate>,
        mediaCategory: MediaCategory
    ): Pair<DateCandidate?, List<DateCandidate>>
}

class PhotoDatePolicy : DateSelectionPolicy {
    override fun selectBestCandidate(
        candidates: List<DateCandidate>,
        mediaCategory: MediaCategory
    ): Pair<DateCandidate?, List<DateCandidate>> {
        if (candidates.isEmpty()) return null to emptyList()

        // Filter valid parsed candidates
        val validCandidates = candidates.filter { it.parsedEpochMillis != null && it.parsedEpochMillis > 0 }
        if (validCandidates.isEmpty()) {
            val evaluated = candidates.map {
                it.copy(
                    status = CandidateStatus.PARSE_ERROR,
                    suitabilityReason = "Date string could not be parsed into a valid timestamp."
                )
            }
            return null to evaluated
        }

        // Rank by photo suitability:
        // 1. EXIF DateTimeOriginal (the actual shutter click)
        // 2. EXIF DateTimeDigitized (when image was converted to digital)
        // 3. EXIF GPS DateStamp/TimeStamp (atomic clock time from GPS)
        // 4. EXIF DateTime / ModifyDate
        // 5. Samsung / Camera Filename Fallback
        // 6. Filesystem Last Modified
        val ranked = validCandidates.sortedWith(
            compareByDescending<DateCandidate> { candidate ->
                when {
                    candidate.metadataGroup == MetadataGroup.EXIF && candidate.tagName.contains("DateTimeOriginal", ignoreCase = true) -> 100
                    candidate.metadataGroup == MetadataGroup.EXIF && candidate.tagName.contains("DateTimeDigitized", ignoreCase = true) -> 90
                    candidate.metadataGroup == MetadataGroup.EXIF && candidate.tagName.contains("GPS", ignoreCase = true) -> 85
                    candidate.metadataGroup == MetadataGroup.EXIF && candidate.tagName.contains("Modify", ignoreCase = true) -> 75
                    candidate.metadataGroup == MetadataGroup.EXIF -> 70
                    candidate.metadataGroup == MetadataGroup.FILENAME && candidate.tagName.contains("Samsung", ignoreCase = true) -> 65
                    candidate.metadataGroup == MetadataGroup.FILENAME -> 60
                    candidate.metadataGroup == MetadataGroup.FILESYSTEM -> 20
                    else -> 40
                }
            }.thenByDescending { it.confidence }
        )

        val best = ranked.first()
        val annotated = candidates.map { candidate ->
            when {
                candidate.parsedEpochMillis == null -> {
                    candidate.copy(status = CandidateStatus.PARSE_ERROR)
                }
                candidate.id == best.id -> {
                    candidate.copy(
                        status = CandidateStatus.RECOMMENDED,
                        suitabilityReason = "Selected as primary capture timestamp per Photo Date Policy: ${best.suitabilityReason}"
                    )
                }
                candidate.metadataGroup == MetadataGroup.FILESYSTEM -> {
                    candidate.copy(
                        status = CandidateStatus.REJECTED,
                        suitabilityReason = "Rejected: Filesystem modified date is volatile and typically altered by copy, sync, or messaging apps."
                    )
                }
                else -> {
                    candidate.copy(
                        status = CandidateStatus.ALTERNATIVE,
                        suitabilityReason = "Valid candidate superseded by higher-priority photo metadata."
                    )
                }
            }
        }

        val recommended = annotated.firstOrNull { it.id == best.id }
        return recommended to annotated
    }
}

class VideoDatePolicy : DateSelectionPolicy {
    override fun selectBestCandidate(
        candidates: List<DateCandidate>,
        mediaCategory: MediaCategory
    ): Pair<DateCandidate?, List<DateCandidate>> {
        if (candidates.isEmpty()) return null to emptyList()

        val validCandidates = candidates.filter { it.parsedEpochMillis != null && it.parsedEpochMillis > 0 }
        if (validCandidates.isEmpty()) return null to candidates

        // Video ranking:
        // QuickTime mvhd MediaCreateDate > MediaStore/Retriever Date > Camera Filename > Filesystem
        // Note: QuickTime timestamps historically use 1904 UTC epoch. If camera wrote local time as UTC,
        // camera filename can sometimes be more accurate if available.
        val ranked = validCandidates.sortedWith(
            compareByDescending<DateCandidate> { candidate ->
                when {
                    candidate.metadataGroup == MetadataGroup.QUICKTIME && candidate.tagName.contains("CreationDate", ignoreCase = true) -> 95
                    candidate.metadataGroup == MetadataGroup.QUICKTIME -> 85
                    candidate.metadataGroup == MetadataGroup.FILENAME && candidate.tagName.contains("Samsung", ignoreCase = true) -> 80
                    candidate.metadataGroup == MetadataGroup.FILENAME -> 75
                    candidate.metadataGroup == MetadataGroup.MEDIASTORE -> 70
                    candidate.metadataGroup == MetadataGroup.FILESYSTEM -> 20
                    else -> 40
                }
            }.thenByDescending { it.confidence }
        )

        val best = ranked.first()
        val annotated = candidates.map { candidate ->
            when {
                candidate.parsedEpochMillis == null -> candidate.copy(status = CandidateStatus.PARSE_ERROR)
                candidate.id == best.id -> candidate.copy(
                    status = CandidateStatus.RECOMMENDED,
                    suitabilityReason = "Selected per Video Date Policy: ${best.suitabilityReason}"
                )
                candidate.metadataGroup == MetadataGroup.FILESYSTEM -> candidate.copy(
                    status = CandidateStatus.REJECTED,
                    suitabilityReason = "Rejected: Volatile filesystem date."
                )
                else -> candidate.copy(
                    status = CandidateStatus.ALTERNATIVE,
                    suitabilityReason = "Alternative video candidate available."
                )
            }
        }

        return annotated.firstOrNull { it.id == best.id } to annotated
    }
}

class DocumentDatePolicy : DateSelectionPolicy {
    override fun selectBestCandidate(
        candidates: List<DateCandidate>,
        mediaCategory: MediaCategory
    ): Pair<DateCandidate?, List<DateCandidate>> {
        if (candidates.isEmpty()) return null to emptyList()

        val validCandidates = candidates.filter { it.parsedEpochMillis != null && it.parsedEpochMillis > 0 }
        if (validCandidates.isEmpty()) return null to candidates

        // Document ranking:
        // dcterms:created > PDF CreationDate > dcterms:modified > Filename > Filesystem
        val ranked = validCandidates.sortedWith(
            compareByDescending<DateCandidate> { candidate ->
                when {
                    candidate.metadataGroup == MetadataGroup.DOCUMENT && candidate.tagName.contains("created", ignoreCase = true) -> 95
                    candidate.metadataGroup == MetadataGroup.DOCUMENT && candidate.tagName.contains("CreationDate", ignoreCase = true) -> 90
                    candidate.metadataGroup == MetadataGroup.DOCUMENT && candidate.tagName.contains("modified", ignoreCase = true) -> 80
                    candidate.metadataGroup == MetadataGroup.DOCUMENT -> 75
                    candidate.metadataGroup == MetadataGroup.FILENAME -> 60
                    candidate.metadataGroup == MetadataGroup.FILESYSTEM -> 20
                    else -> 30
                }
            }.thenByDescending { it.confidence }
        )

        val best = ranked.first()
        val annotated = candidates.map { candidate ->
            when {
                candidate.parsedEpochMillis == null -> candidate.copy(status = CandidateStatus.PARSE_ERROR)
                candidate.id == best.id -> candidate.copy(
                    status = CandidateStatus.RECOMMENDED,
                    suitabilityReason = "Selected per Document Policy: ${best.suitabilityReason}"
                )
                candidate.metadataGroup == MetadataGroup.FILESYSTEM -> candidate.copy(
                    status = CandidateStatus.REJECTED,
                    suitabilityReason = "Rejected: Volatile filesystem date."
                )
                else -> candidate.copy(
                    status = CandidateStatus.ALTERNATIVE,
                    suitabilityReason = "Secondary document metadata timestamp."
                )
            }
        }

        return annotated.firstOrNull { it.id == best.id } to annotated
    }
}

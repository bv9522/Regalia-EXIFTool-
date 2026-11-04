package com.example.data.model

data class NormalizedMetadata(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val fileExtension: String,
    val filesystemLastModified: Long,
    val formattedFilesystemDate: String,
    val mediaCategory: MediaCategory,
    val dateCandidates: List<DateCandidate>,
    val recommendedCandidate: DateCandidate?,
    val technicalDetails: Map<String, String> = emptyMap(),
    val rawTags: Map<String, String> = emptyMap(),
    val hasGps: Boolean = false,
    val gpsCoordinates: String? = null,
    val displayPath: String = ""
)

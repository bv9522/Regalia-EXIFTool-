package com.example.data.model

enum class MetadataGroup(val displayName: String) {
    EXIF("EXIF"),
    QUICKTIME("QuickTime / MP4"),
    DOCUMENT("Document Core"),
    AUDIO_ID3("Audio Tags"),
    FILENAME("Filename Fallback"),
    FILESYSTEM("Filesystem"),
    MEDIASTORE("MediaStore")
}

enum class CandidateStatus {
    RECOMMENDED,
    ALTERNATIVE,
    REJECTED,
    PARSE_ERROR
}

enum class MediaCategory(val label: String) {
    PHOTO("Photo / Image"),
    VIDEO("Video"),
    DOCUMENT("Document"),
    AUDIO("Audio"),
    OTHER("Other")
}

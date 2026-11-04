package com.example.engine.metadata

import android.content.Context
import android.net.Uri
import com.example.data.model.NormalizedMetadata

interface MetadataProvider {
    val providerId: String
    val displayName: String

    fun canHandle(mimeType: String, extension: String): Boolean

    suspend fun extract(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        lastModified: Long
    ): NormalizedMetadata
}

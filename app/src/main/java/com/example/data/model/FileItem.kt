package com.example.data.model

import android.net.Uri

data class FileItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val pathOrTree: String = "",
    val extension: String = "",
    val isSample: Boolean = false,
    val directFilePath: String? = null,
    val displayPath: String = ""
) {
    fun formattedSize(): String {
        if (isDirectory) return "--"
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var s = size.toDouble()
        var unitIdx = 0
        while (s >= 1024 && unitIdx < units.lastIndex) {
            s /= 1024
            unitIdx++
        }
        return String.format(java.util.Locale.US, "%.1f %s", s, units[unitIdx])
    }

    fun folderLocation(): String {
        if (displayPath.isNotBlank()) {
            val parent = displayPath.substringBeforeLast('/', "")
            if (parent.isNotBlank()) return parent
            return displayPath
        }
        if (directFilePath != null) {
            return directFilePath.substringBeforeLast('/', "")
        }
        return if (pathOrTree.isNotBlank()) pathOrTree else "Storage"
    }
}

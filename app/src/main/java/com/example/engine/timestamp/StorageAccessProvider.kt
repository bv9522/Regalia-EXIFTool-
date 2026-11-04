package com.example.engine.timestamp

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.FileItem
import com.example.data.model.StorageCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageAccessProvider(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Persists SAF read/write permission across app restarts
     */
    fun takePersistablePermission(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
    }

    /**
     * Inspects capabilities of a given URI's storage provider
     */
    fun probeStorageCapability(uri: Uri): StorageCapability {
        val authority = uri.authority ?: "unknown"

        if (uri.scheme == "file") {
            return StorageCapability(
                canModifyTimestamp = true,
                methodUsed = "java.io.File.setLastModified",
                diagnosticNotes = "Direct filesystem path access. Full timestamp write support supported by OS.",
                providerAuthority = authority
            )
        }

        var flags = 0
        var supportsWrite = false

        try {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val flagsCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    if (flagsCol >= 0) {
                        flags = cursor.getInt(flagsCol)
                        supportsWrite = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
                    }
                }
            }
        } catch (_: Exception) {}

        val notes = buildString {
            append("Storage Provider: $authority. ")
            if (supportsWrite) {
                append("FLAG_SUPPORTS_WRITE is flagged by provider. ")
            } else {
                append("FLAG_SUPPORTS_WRITE not reported. Provider may enforce read-only semantics. ")
            }
            append("Strict read-back verification will test whether the filesystem modified date is actually updated.")
        }

        return StorageCapability(
            canModifyTimestamp = supportsWrite || uri.scheme == "file",
            methodUsed = "SAF ParcelFileDescriptor (futimes/fstat) + ContentResolver update",
            diagnosticNotes = notes,
            providerAuthority = authority
        )
    }

    /**
     * Read actual verified last-modified timestamp from the storage provider or file
     * Accurately handles milliseconds vs seconds (MediaStore uses seconds, DocumentsContract uses millis).
     * Also uses Linux kernel Os.fstat on file descriptor when available.
     */
    fun readCurrentTimestamp(uri: Uri, directPath: String? = null): Long {
        if (directPath != null) {
            val f = File(directPath)
            if (f.exists()) return f.lastModified()
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val f = File(path)
                if (f.exists()) return f.lastModified()
            }
        }

        // 1. Try Linux kernel fstat on open file descriptor (True OS filesystem modification time)
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val stat = Os.fstat(pfd.fileDescriptor)
                // st_mtime is in seconds since Unix epoch
                if (stat.st_mtime > 0) {
                    val mtimeMs = stat.st_mtime * 1000L
                    // Validate reasonable year (1990 to 2050)
                    if (mtimeMs in 631152000000L..2524608000000L) {
                        return mtimeMs
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Query DocumentsContract COLUMN_LAST_MODIFIED
        try {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (col >= 0) {
                        val raw = cursor.getLong(col)
                        val normalized = normalizeTimestamp(raw)
                        if (normalized > 0) return normalized
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Query MediaStore DATE_MODIFIED (which is stored in seconds)
        try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (col >= 0) {
                        val raw = cursor.getLong(col)
                        val normalized = normalizeTimestamp(raw)
                        if (normalized > 0) return normalized
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback DocumentFile
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val lm = doc?.lastModified() ?: 0L
            val normalized = normalizeTimestamp(lm)
            if (normalized > 0) return normalized
        } catch (_: Exception) {}

        return 0L
    }

    /**
     * Resolves metadata for a single Uri
     */
    suspend fun resolveFileItem(uri: Uri): FileItem? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return@withContext null)
            return@withContext FileItem(
                uri = uri,
                name = file.name,
                mimeType = getMimeType(file.name),
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                pathOrTree = file.parent ?: "",
                extension = file.name.substringAfterLast('.', "").lowercase(),
                directFilePath = file.absolutePath
            )
        }

        var name = "Unknown"
        var size = 0L
        var lastModified = readCurrentTimestamp(uri)
        var mimeType = "application/octet-stream"

        try {
            contentResolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getStringOrNull(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: name
                    size = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_SIZE) ?: size
                    mimeType = cursor.getStringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE) ?: mimeType

                    if (lastModified <= 0) {
                        val rawLm = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L
                        lastModified = normalizeTimestamp(rawLm)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to MediaStore columns if name is still unknown or lastModified <= 0
        if (name == "Unknown" || lastModified <= 0) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (name == "Unknown") {
                            name = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME) ?: name
                        }
                        if (size <= 0) {
                            size = cursor.getLongOrNull(MediaStore.MediaColumns.SIZE) ?: size
                        }
                        if (lastModified <= 0) {
                            val rawLm = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L
                            lastModified = normalizeTimestamp(rawLm)
                        }
                        if (mimeType == "application/octet-stream") {
                            mimeType = cursor.getStringOrNull(MediaStore.MediaColumns.MIME_TYPE) ?: mimeType
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If mimeType is generic, infer from name
        if (mimeType == "application/octet-stream" && name != "Unknown") {
            mimeType = getMimeType(name)
        }

        val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val displayPath = resolveDisplayPath(uri, name)

        FileItem(
            uri = uri,
            name = name,
            mimeType = mimeType,
            size = size,
            lastModified = lastModified,
            isDirectory = isDir,
            pathOrTree = uri.toString(),
            extension = name.substringAfterLast('.', "").lowercase(),
            displayPath = displayPath
        )
    }

    /**
     * Lists children of a folder tree URI
     */
    suspend fun listChildren(treeUri: Uri, folderDocId: String? = null): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        try {
            val childrenUri = if (folderDocId == null) {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            } else {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
            }

            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getStringOrNull(DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                    val name = cursor.getStringOrNull(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: "Unknown"
                    val size = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_SIZE) ?: 0L
                    val rawLastModified = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L
                    val lastModified = normalizeTimestamp(rawLastModified)
                    val mimeType = cursor.getStringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE) ?: getMimeType(name)
                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val displayPath = resolveDisplayPath(itemUri, name, null, docId)

                    results.add(
                        FileItem(
                            uri = itemUri,
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            lastModified = lastModified,
                            isDirectory = isDir,
                            pathOrTree = docId,
                            extension = name.substringAfterLast('.', "").lowercase(),
                            displayPath = displayPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    /**
     * Resolves human-readable storage path for any SAF or filesystem URI
     */
    fun resolveDisplayPath(
        uri: Uri,
        fileName: String,
        directPath: String? = null,
        docIdParam: String? = null
    ): String {
        if (directPath != null) return directPath
        if (uri.scheme == "file") return uri.path ?: fileName

        val docId = docIdParam ?: try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                null
            }
        } catch (_: Exception) { null }

        if (!docId.isNullOrBlank()) {
            if (docId.startsWith("primary:", ignoreCase = true)) {
                val sub = docId.substringAfter("primary:")
                return "/Internal Storage/$sub"
            } else if (docId.contains(":")) {
                val storageId = docId.substringBefore(":")
                val sub = docId.substringAfter(":")
                return "/$storageId/$sub"
            } else if (docId.startsWith("/")) {
                return docId
            }
        }

        // Query MediaStore DATA or RELATIVE_PATH
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.RELATIVE_PATH
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrBlank()) return path
                    }
                    val relCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (relCol >= 0 && !cursor.isNull(relCol)) {
                        val rel = cursor.getString(relCol)
                        if (!rel.isNullOrBlank()) return "/Internal Storage/$rel$fileName"
                    }
                }
            }
        } catch (_: Exception) {}

        val auth = uri.authority ?: ""
        return when {
            auth.contains("downloads", ignoreCase = true) -> "/Downloads/$fileName"
            auth.contains("media", ignoreCase = true) -> "/Media Storage/$fileName"
            auth.contains("photos", ignoreCase = true) -> "/Google Photos Cloud/$fileName"
            else -> "/$auth/$fileName"
        }
    }

    /**
     * Normalizes seconds vs milliseconds timestamps
     */
    fun normalizeTimestamp(raw: Long): Long {
        if (raw <= 0) return 0L
        // If raw is in seconds (less than 10 billion: year 2286 in seconds is ~10,000,000,000)
        // Milliseconds for year 2024 is ~1,715,000,000,000 (13 digits)
        return if (raw < 10_000_000_000L) {
            raw * 1000L
        } else {
            raw
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
    }
}

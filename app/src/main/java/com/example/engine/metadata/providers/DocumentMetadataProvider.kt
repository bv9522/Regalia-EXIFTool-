package com.example.engine.metadata.providers

import android.content.Context
import android.net.Uri
import android.util.Xml
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
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipInputStream

class DocumentMetadataProvider(
    private val candidateSelector: DateCandidateSelector = DateCandidateSelector()
) : MetadataProvider {

    override val providerId: String = "document_ooxml_pdf_provider"
    override val displayName: String = "Office Open XML & PDF Document Metadata Reader"

    private val supportedExtensions = setOf("docx", "xlsx", "pptx", "pdf", "odt", "ods", "odp")

    override fun canHandle(mimeType: String, extension: String): Boolean {
        if (mimeType.contains("pdf", ignoreCase = true) ||
            mimeType.contains("word", ignoreCase = true) ||
            mimeType.contains("document", ignoreCase = true) ||
            mimeType.contains("sheet", ignoreCase = true) ||
            mimeType.contains("presentation", ignoreCase = true)
        ) return true

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

        when (extension) {
            "docx", "xlsx", "pptx", "odt", "ods", "odp" -> {
                extractOoxmlMetadata(context, uri, candidates, technical, rawTags)
            }
            "pdf" -> {
                extractPdfMetadata(context, uri, candidates, technical, rawTags)
            }
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
                    suitabilityReason = "Filesystem last modified attribute.",
                    status = CandidateStatus.ALTERNATIVE
                )
            )
        }

        val (recommended, evaluatedCandidates) = candidateSelector.evaluate(candidates, MediaCategory.DOCUMENT)

        NormalizedMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/octet-stream"
            },
            fileExtension = extension,
            filesystemLastModified = lastModified,
            formattedFilesystemDate = DateCandidate.formatEpoch(lastModified),
            mediaCategory = MediaCategory.DOCUMENT,
            dateCandidates = evaluatedCandidates,
            recommendedCandidate = recommended,
            technicalDetails = technical,
            rawTags = rawTags
        )
    }

    private fun extractOoxmlMetadata(
        context: Context,
        uri: Uri,
        candidates: MutableList<DateCandidate>,
        technical: MutableMap<String, String>,
        rawTags: MutableMap<String, String>
    ) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "docProps/core.xml" || entry.name.endsWith("core.xml")) {
                            parseOoxmlCoreXml(zip, candidates, technical, rawTags)
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            technical["Document Read Error"] = "OOXML core read: ${e.message}"
        }
    }

    private fun parseOoxmlCoreXml(
        stream: InputStream,
        candidates: MutableList<DateCandidate>,
        technical: MutableMap<String, String>,
        rawTags: MutableMap<String, String>
    ) {
        try {
            val parser = Xml.newPullParser().apply {
                setInput(stream, "UTF-8")
            }
            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrBlank()) {
                            when (currentTag) {
                                "created" -> {
                                    rawTags["dcterms:created"] = text
                                    val parsed = parseIso8601(text)
                                    if (parsed != null) {
                                        candidates.add(
                                            DateCandidate(
                                                id = UUID.randomUUID().toString(),
                                                tagName = "Document Created (dcterms:created)",
                                                metadataGroup = MetadataGroup.DOCUMENT,
                                                originalRawValue = text,
                                                parsedEpochMillis = parsed.first,
                                                formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                                                timeZoneInfo = parsed.second,
                                                hasExplicitTimeZone = true,
                                                confidence = 95,
                                                suitabilityReason = "Original document authoring creation timestamp from Open XML docProps/core.xml.",
                                                status = CandidateStatus.ALTERNATIVE
                                            )
                                        )
                                    }
                                }
                                "modified" -> {
                                    rawTags["dcterms:modified"] = text
                                    val parsed = parseIso8601(text)
                                    if (parsed != null) {
                                        candidates.add(
                                            DateCandidate(
                                                id = UUID.randomUUID().toString(),
                                                tagName = "Document Modified (dcterms:modified)",
                                                metadataGroup = MetadataGroup.DOCUMENT,
                                                originalRawValue = text,
                                                parsedEpochMillis = parsed.first,
                                                formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                                                timeZoneInfo = parsed.second,
                                                hasExplicitTimeZone = true,
                                                confidence = 80,
                                                suitabilityReason = "Last document revision timestamp recorded by the authoring application.",
                                                status = CandidateStatus.ALTERNATIVE
                                            )
                                        )
                                    }
                                }
                                "creator" -> technical["Author / Creator"] = text
                                "lastModifiedBy" -> technical["Last Modified By"] = text
                                "title" -> technical["Document Title"] = text
                                "revision" -> technical["Revision"] = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
    }

    private fun extractPdfMetadata(
        context: Context,
        uri: Uri,
        candidates: MutableList<DateCandidate>,
        technical: MutableMap<String, String>,
        rawTags: MutableMap<String, String>
    ) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Read up to 64KB from header/trailer
                val buffer = ByteArray(65536)
                val read = stream.read(buffer)
                if (read > 0) {
                    val content = String(buffer, 0, read, Charsets.ISO_8859_1)

                    // Match /CreationDate (D:YYYYMMDDHHmmSS...)
                    val creationMatcher = Regex("/CreationDate\\s*\\((D:[^\\)]+)\\)").find(content)
                    if (creationMatcher != null) {
                        val rawDate = creationMatcher.groupValues[1]
                        rawTags["PDF:CreationDate"] = rawDate
                        val parsed = parsePdfDate(rawDate)
                        if (parsed != null) {
                            candidates.add(
                                DateCandidate(
                                    id = UUID.randomUUID().toString(),
                                    tagName = "PDF CreationDate",
                                    metadataGroup = MetadataGroup.DOCUMENT,
                                    originalRawValue = rawDate,
                                    parsedEpochMillis = parsed.first,
                                    formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                                    timeZoneInfo = parsed.second,
                                    hasExplicitTimeZone = parsed.second.contains("Explicit"),
                                    confidence = 90,
                                    suitabilityReason = "Extracted from PDF document info dictionary /CreationDate.",
                                    status = CandidateStatus.ALTERNATIVE
                                )
                            )
                        }
                    }

                    // Match /ModDate (D:YYYYMMDDHHmmSS...)
                    val modMatcher = Regex("/ModDate\\s*\\((D:[^\\)]+)\\)").find(content)
                    if (modMatcher != null) {
                        val rawDate = modMatcher.groupValues[1]
                        rawTags["PDF:ModDate"] = rawDate
                        val parsed = parsePdfDate(rawDate)
                        if (parsed != null) {
                            candidates.add(
                                DateCandidate(
                                    id = UUID.randomUUID().toString(),
                                    tagName = "PDF ModDate",
                                    metadataGroup = MetadataGroup.DOCUMENT,
                                    originalRawValue = rawDate,
                                    parsedEpochMillis = parsed.first,
                                    formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                                    timeZoneInfo = parsed.second,
                                    hasExplicitTimeZone = parsed.second.contains("Explicit"),
                                    confidence = 78,
                                    suitabilityReason = "Extracted from PDF document info dictionary /ModDate.",
                                    status = CandidateStatus.ALTERNATIVE
                                )
                            )
                        }
                    }

                    // Title & Producer
                    Regex("/Title\\s*\\(([^\\)]+)\\)").find(content)?.groupValues?.get(1)?.let {
                        technical["PDF Title"] = it.trim()
                    }
                    Regex("/Producer\\s*\\(([^\\)]+)\\)").find(content)?.groupValues?.get(1)?.let {
                        technical["PDF Producer"] = it.trim()
                    }
                }
            }
        } catch (e: Exception) {
            technical["PDF Read Note"] = e.message ?: "Unable to parse PDF stream"
        }
    }

    private fun parseIso8601(isoString: String): Pair<Long, String>? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = if (isoString.endsWith("Z")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                    isLenient = false
                }
                val date = sdf.parse(isoString.trim())
                if (date != null) {
                    val tz = if (isoString.endsWith("Z")) "Explicit: UTC" else "ISO Offset / Local Time"
                    return date.time to tz
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parsePdfDate(pdfDateStr: String): Pair<Long, String>? {
        // e.g. D:20240315123000+02'00' or D:20240315123000Z
        val clean = pdfDateStr.removePrefix("D:").replace("'", "")
        try {
            val year = clean.substring(0, 4).toInt()
            val month = clean.substring(4, 6).toInt()
            val day = clean.substring(6, 8).toInt()
            val hour = if (clean.length >= 10) clean.substring(8, 10).toInt() else 0
            val min = if (clean.length >= 12) clean.substring(10, 12).toInt() else 0
            val sec = if (clean.length >= 14) clean.substring(12, 14).toInt() else 0

            var tz = TimeZone.getDefault()
            var tzInfo = "Local Device Time (Unspecified in PDF)"

            if (clean.contains("Z")) {
                tz = TimeZone.getTimeZone("UTC")
                tzInfo = "Explicit: UTC"
            } else if (clean.contains("+") || clean.contains("-")) {
                val signIdx = clean.indexOfAny(charArrayOf('+', '-'), 14)
                if (signIdx != -1) {
                    val offsetPart = clean.substring(signIdx)
                    tz = TimeZone.getTimeZone("GMT$offsetPart")
                    tzInfo = "Explicit: GMT$offsetPart"
                }
            }

            val cal = java.util.Calendar.getInstance(tz).apply {
                clear()
                set(year, month - 1, day, hour, min, sec)
            }
            return cal.timeInMillis to tzInfo
        } catch (_: Exception) {
            return null
        }
    }
}

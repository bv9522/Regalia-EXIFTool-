package com.example.engine.metadata.providers

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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

class ImageMetadataProvider(
    private val candidateSelector: DateCandidateSelector = DateCandidateSelector()
) : MetadataProvider {

    override val providerId: String = "image_exif_provider"
    override val displayName: String = "EXIF Image Metadata Reader"

    private val supportedExtensions = setOf("jpg", "jpeg", "heic", "heif", "png", "webp", "tiff", "tif", "dng", "cr2", "nef", "arw")

    override fun canHandle(mimeType: String, extension: String): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) return true
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

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                // 1. Read EXIF dates and timezone offsets
                val offsetOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                val offsetDigitized = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)
                val offsetStandard = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)

                val dtOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                val dtDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                val dtModified = exif.getAttribute(ExifInterface.TAG_DATETIME)

                // GPS Date and Time
                val gpsDateStamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
                val gpsTimeStamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)

                // Subseconds
                val subsecOriginal = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)

                // Process DateTimeOriginal
                if (!dtOriginal.isNullOrBlank()) {
                    rawTags["Exif:DateTimeOriginal"] = dtOriginal
                    offsetOriginal?.let { rawTags["Exif:OffsetTimeOriginal"] = it }
                    val parsed = parseExifDate(dtOriginal, offsetOriginal)
                    candidates.add(
                        DateCandidate(
                            id = UUID.randomUUID().toString(),
                            tagName = "DateTimeOriginal",
                            metadataGroup = MetadataGroup.EXIF,
                            originalRawValue = "$dtOriginal ${offsetOriginal ?: ""}".trim(),
                            parsedEpochMillis = parsed.first,
                            formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                            timeZoneInfo = parsed.second,
                            hasExplicitTimeZone = offsetOriginal != null,
                            confidence = 95,
                            suitabilityReason = "Primary EXIF capture timestamp recorded by camera shutter sensor at point of creation.",
                            status = CandidateStatus.ALTERNATIVE
                        )
                    )
                }

                // Process DateTimeDigitized
                if (!dtDigitized.isNullOrBlank()) {
                    rawTags["Exif:DateTimeDigitized"] = dtDigitized
                    val parsed = parseExifDate(dtDigitized, offsetDigitized ?: offsetOriginal)
                    candidates.add(
                        DateCandidate(
                            id = UUID.randomUUID().toString(),
                            tagName = "DateTimeDigitized",
                            metadataGroup = MetadataGroup.EXIF,
                            originalRawValue = "$dtDigitized ${offsetDigitized ?: ""}".trim(),
                            parsedEpochMillis = parsed.first,
                            formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                            timeZoneInfo = parsed.second,
                            hasExplicitTimeZone = offsetDigitized != null,
                            confidence = 88,
                            suitabilityReason = "Timestamp when the image was stored as digital data.",
                            status = CandidateStatus.ALTERNATIVE
                        )
                    )
                }

                // Process ModifyDate (TAG_DATETIME)
                if (!dtModified.isNullOrBlank()) {
                    rawTags["Exif:DateTime"] = dtModified
                    val parsed = parseExifDate(dtModified, offsetStandard ?: offsetOriginal)
                    candidates.add(
                        DateCandidate(
                            id = UUID.randomUUID().toString(),
                            tagName = "ModifyDate (Exif:DateTime)",
                            metadataGroup = MetadataGroup.EXIF,
                            originalRawValue = "$dtModified ${offsetStandard ?: ""}".trim(),
                            parsedEpochMillis = parsed.first,
                            formattedParsedDate = DateCandidate.formatEpoch(parsed.first),
                            timeZoneInfo = parsed.second,
                            hasExplicitTimeZone = offsetStandard != null,
                            confidence = 72,
                            suitabilityReason = "EXIF image modification date (may be modified by photo editors).",
                            status = CandidateStatus.ALTERNATIVE
                        )
                    )
                }

                // Process GPS Timestamp (atomic UTC)
                if (!gpsDateStamp.isNullOrBlank() && !gpsTimeStamp.isNullOrBlank()) {
                    rawTags["Exif:GPSDate"] = "$gpsDateStamp $gpsTimeStamp UTC"
                    val parsedGps = parseGpsDateTime(gpsDateStamp, gpsTimeStamp)
                    if (parsedGps != null) {
                        candidates.add(
                            DateCandidate(
                                id = UUID.randomUUID().toString(),
                                tagName = "GPS DateTimeStamp",
                                metadataGroup = MetadataGroup.EXIF,
                                originalRawValue = "$gpsDateStamp $gpsTimeStamp (UTC)",
                                parsedEpochMillis = parsedGps,
                                formattedParsedDate = DateCandidate.formatEpoch(parsedGps),
                                timeZoneInfo = "Explicit: UTC (GPS Atomic Clock)",
                                hasExplicitTimeZone = true,
                                confidence = 85,
                                suitabilityReason = "Atomic UTC clock timestamp synchronized via satellite GPS receiver.",
                                status = CandidateStatus.ALTERNATIVE
                            )
                        )
                    }
                }

                // Technical details
                val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)
                val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION)
                if (width != null && height != null) {
                    technical["Dimensions"] = "${width} x ${height} px"
                }

                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
                    technical["Camera"] = listOfNotNull(make?.trim(), model?.trim()).joinToString(" ")
                }

                val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
                if (!lens.isNullOrBlank()) technical["Lens"] = lens.trim()

                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                if (!iso.isNullOrBlank()) technical["ISO"] = iso

                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                if (!fNumber.isNullOrBlank()) technical["Aperture"] = "f/$fNumber"

                val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                if (!exposureTime.isNullOrBlank()) technical["Exposure"] = "${exposureTime}s"

                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                if (!software.isNullOrBlank()) technical["Software"] = software.trim()

                // GPS Coordinates
                val latLong = exif.latLong
                if (latLong != null) {
                    hasGps = true
                    gpsCoords = String.format(Locale.US, "%.5f, %.5f", latLong[0], latLong[1])
                    technical["GPS Coordinates"] = gpsCoords
                }
            }
        } catch (e: Exception) {
            technical["Read Note"] = "EXIF read notice: ${e.message ?: "Unable to stream EXIF table"}"
        }

        // 2. Check Samsung / Camera Filename Fallback
        val filenameCandidate = FilenameTimestampParser.parse(fileName)
        if (filenameCandidate != null) {
            candidates.add(filenameCandidate)
            rawTags["Filename:Pattern"] = filenameCandidate.originalRawValue
        }

        // 3. Add Filesystem Last Modified
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
                    suitabilityReason = "Current filesystem modification date. Known to be volatile and modified during downloads, syncs, or file moves.",
                    status = CandidateStatus.ALTERNATIVE
                )
            )
        }

        // 4. Select best candidate per policy
        val (recommended, evaluatedCandidates) = candidateSelector.evaluate(candidates, MediaCategory.PHOTO)

        NormalizedMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = if (extension in listOf("heic", "heif")) "image/heic" else "image/$extension",
            fileExtension = extension,
            filesystemLastModified = lastModified,
            formattedFilesystemDate = DateCandidate.formatEpoch(lastModified),
            mediaCategory = MediaCategory.PHOTO,
            dateCandidates = evaluatedCandidates,
            recommendedCandidate = recommended,
            technicalDetails = technical,
            rawTags = rawTags,
            hasGps = hasGps,
            gpsCoordinates = gpsCoords
        )
    }

    private fun parseExifDate(dateStr: String, offsetStr: String?): Pair<Long?, String> {
        val cleanDate = dateStr.trim()
        val formats = arrayOf(
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        )

        for (format in formats) {
            try {
                val tz = if (!offsetStr.isNullOrBlank()) {
                    TimeZone.getTimeZone("GMT$offsetStr")
                } else {
                    TimeZone.getDefault()
                }

                val sdf = SimpleDateFormat(format, Locale.US).apply {
                    timeZone = tz
                    isLenient = false
                }
                val parsed = sdf.parse(cleanDate)
                if (parsed != null) {
                    val tzInfo = if (!offsetStr.isNullOrBlank()) {
                        "Explicit Offset: $offsetStr"
                    } else {
                        "Unspecified / Camera Local (${TimeZone.getDefault().displayName})"
                    }
                    return parsed.time to tzInfo
                }
            } catch (_: Exception) {}
        }
        return null to "Failed to parse EXIF date format"
    }

    private fun parseGpsDateTime(dateStamp: String, timeStamp: String): Long? {
        try {
            val combined = "${dateStamp.trim()} ${timeStamp.trim()}"
            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }
            return sdf.parse(combined)?.time
        } catch (_: Exception) {
            return null
        }
    }
}

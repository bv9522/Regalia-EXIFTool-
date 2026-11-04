package com.example

import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.data.model.MediaCategory
import com.example.data.model.MetadataGroup
import com.example.data.model.OperationStatus
import com.example.data.model.TimestampTask
import com.example.engine.policy.DateCandidateSelector
import com.example.engine.policy.FilenameTimestampParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegaliaUnitTest {

    @Test
    fun testSamsungCameraFilenameParsing() {
        val candidate = FilenameTimestampParser.parse("20261014_170334.heic")
        assertNotNull(candidate)
        assertEquals(MetadataGroup.FILENAME, candidate!!.metadataGroup)
        assertTrue(candidate.tagName.contains("Samsung Camera Filename"))
        assertEquals("20261014_170334", candidate.originalRawValue)
        assertTrue(candidate.parsedEpochMillis != null && candidate.parsedEpochMillis!! > 0)
        assertFalse(candidate.hasExplicitTimeZone)
    }

    @Test
    fun testSamsungScreenshotFilenameParsing() {
        val candidate = FilenameTimestampParser.parse("Screenshot_20261014_170334.png")
        assertNotNull(candidate)
        assertEquals("20261014_170334", candidate!!.originalRawValue)
    }

    @Test
    fun testAndroidStandardCameraFilenameParsing() {
        val candidate = FilenameTimestampParser.parse("IMG_20240510_142030.jpg")
        assertNotNull(candidate)
        assertEquals("20240510_142030", candidate!!.originalRawValue)
    }

    @Test
    fun testCalendarValidation() {
        // Valid regular date
        assertTrue(FilenameTimestampParser.isValidCalendarDate(2024, 5, 10, 14, 20, 30))

        // Leap year: 2024 is leap year, Feb 29 is valid
        assertTrue(FilenameTimestampParser.isLeapYear(2024))
        assertTrue(FilenameTimestampParser.isValidCalendarDate(2024, 2, 29, 12, 0, 0))

        // Non-leap year: 2023 Feb 29 must be rejected
        assertFalse(FilenameTimestampParser.isLeapYear(2023))
        assertFalse(FilenameTimestampParser.isValidCalendarDate(2023, 2, 29, 12, 0, 0))

        // Impossible date: Month 13
        assertFalse(FilenameTimestampParser.isValidCalendarDate(2024, 13, 1, 10, 0, 0))

        // Impossible date: April 31
        assertFalse(FilenameTimestampParser.isValidCalendarDate(2024, 4, 31, 10, 0, 0))

        // Invalid hour: 24
        assertFalse(FilenameTimestampParser.isValidCalendarDate(2024, 6, 15, 24, 0, 0))

        // Invalid year: 1850 (out of bounds)
        assertFalse(FilenameTimestampParser.isValidCalendarDate(1850, 1, 1, 0, 0, 0))
    }

    @Test
    fun testPhotoPolicyPrioritizesDateTimeOriginal() {
        val selector = DateCandidateSelector()

        val exifOriginal = DateCandidate(
            id = "1",
            tagName = "DateTimeOriginal",
            metadataGroup = MetadataGroup.EXIF,
            originalRawValue = "2024:05:10 14:20:30",
            parsedEpochMillis = 1715350830000L,
            formattedParsedDate = "2024-05-10 14:20:30",
            timeZoneInfo = "Camera Local",
            hasExplicitTimeZone = false,
            confidence = 95,
            suitabilityReason = "Primary shutter capture time."
        )

        val exifModify = DateCandidate(
            id = "2",
            tagName = "ModifyDate",
            metadataGroup = MetadataGroup.EXIF,
            originalRawValue = "2024:05:10 16:00:00",
            parsedEpochMillis = 1715356800000L,
            formattedParsedDate = "2024-05-10 16:00:00",
            timeZoneInfo = "Camera Local",
            hasExplicitTimeZone = false,
            confidence = 72,
            suitabilityReason = "Modified date."
        )

        val filesystem = DateCandidate(
            id = "3",
            tagName = "Filesystem Last Modified",
            metadataGroup = MetadataGroup.FILESYSTEM,
            originalRawValue = "1720000000000",
            parsedEpochMillis = 1720000000000L,
            formattedParsedDate = "2024-07-03 10:00:00",
            timeZoneInfo = "Filesystem",
            hasExplicitTimeZone = false,
            confidence = 25,
            suitabilityReason = "Filesystem date."
        )

        val (recommended, evaluated) = selector.evaluate(
            listOf(filesystem, exifModify, exifOriginal),
            MediaCategory.PHOTO
        )

        assertNotNull(recommended)
        assertEquals("1", recommended!!.id)
        assertEquals("DateTimeOriginal", recommended.tagName)
        assertEquals(CandidateStatus.RECOMMENDED, recommended.status)

        val fsEval = evaluated.find { it.id == "3" }
        assertEquals(CandidateStatus.REJECTED, fsEval?.status)
    }

    @Test
    fun testVideoPolicyPrioritizesQuickTimeCreation() {
        val selector = DateCandidateSelector()

        val qtCandidate = DateCandidate(
            id = "qt",
            tagName = "QuickTime CreationDate",
            metadataGroup = MetadataGroup.QUICKTIME,
            originalRawValue = "2024-06-01T12:00:00Z",
            parsedEpochMillis = 1717243200000L,
            formattedParsedDate = "2024-06-01 12:00:00 (UTC)",
            timeZoneInfo = "Explicit: UTC",
            hasExplicitTimeZone = true,
            confidence = 85,
            suitabilityReason = "QuickTime container header."
        )

        val filenameCandidate = DateCandidate(
            id = "fn",
            tagName = "Samsung Camera Filename",
            metadataGroup = MetadataGroup.FILENAME,
            originalRawValue = "20240601_140000",
            parsedEpochMillis = 1717250400000L,
            formattedParsedDate = "2024-06-01 14:00:00",
            timeZoneInfo = "Device Local",
            hasExplicitTimeZone = false,
            confidence = 75,
            suitabilityReason = "Camera filename."
        )

        val (recommended, _) = selector.evaluate(
            listOf(filenameCandidate, qtCandidate),
            MediaCategory.VIDEO
        )

        assertNotNull(recommended)
        assertEquals("qt", recommended!!.id)
    }

    @Test
    fun testDocumentPolicyPrioritizesCreatedOverModified() {
        val selector = DateCandidateSelector()

        val created = DateCandidate(
            id = "c",
            tagName = "Document Created (dcterms:created)",
            metadataGroup = MetadataGroup.DOCUMENT,
            originalRawValue = "2024-03-01T09:00:00Z",
            parsedEpochMillis = 1709283600000L,
            formattedParsedDate = "2024-03-01 09:00:00",
            timeZoneInfo = "Explicit: UTC",
            hasExplicitTimeZone = true,
            confidence = 95,
            suitabilityReason = "Created date."
        )

        val modified = DateCandidate(
            id = "m",
            tagName = "Document Modified (dcterms:modified)",
            metadataGroup = MetadataGroup.DOCUMENT,
            originalRawValue = "2024-03-05T14:00:00Z",
            parsedEpochMillis = 1709647200000L,
            formattedParsedDate = "2024-03-05 14:00:00",
            timeZoneInfo = "Explicit: UTC",
            hasExplicitTimeZone = true,
            confidence = 80,
            suitabilityReason = "Modified date."
        )

        val (recommended, _) = selector.evaluate(
            listOf(modified, created),
            MediaCategory.DOCUMENT
        )

        assertNotNull(recommended)
        assertEquals("c", recommended!!.id)
    }

    @Test
    fun testTimestampDifferenceCalculation() {
        val fileItem = FileItem(
            uri = android.net.Uri.parse("content://dummy/1"),
            name = "photo.jpg",
            mimeType = "image/jpeg",
            size = 1024L,
            lastModified = 1700000000000L
        )

        val taskIdentical = TimestampTask(
            id = "1",
            fileItem = fileItem,
            currentTimestamp = 1700000000000L,
            proposedTimestamp = 1700000001000L, // 1 second difference
            selectedCandidate = null
        )
        assertEquals("Identical (±2s)", taskIdentical.formattedDifference())

        val taskChanged = TimestampTask(
            id = "2",
            fileItem = fileItem,
            currentTimestamp = 1700000000000L,
            proposedTimestamp = 1700000000000L + (2 * 86400 * 1000L) + (3 * 3600 * 1000L),
            selectedCandidate = null
        )
        assertTrue(taskChanged.formattedDifference().startsWith("+ 2d 3h"))
    }
}

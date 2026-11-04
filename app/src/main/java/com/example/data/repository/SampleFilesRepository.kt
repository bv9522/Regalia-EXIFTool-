package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SampleFilesRepository(private val context: Context) {

    private val samplesDir: File
        get() = File(context.filesDir, "sample_media").apply { mkdirs() }

    suspend fun getOrCreateSampleFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()

        // 1. Samsung Galaxy S21 Photo with EXIF DateTimeOriginal
        val photoFile = File(samplesDir, "20261014_170334.jpg")
        if (!photoFile.exists()) {
            createSampleJpegWithExif(
                targetFile = photoFile,
                exifDate = "2026:10:14 17:03:34",
                offset = "+01:00",
                cameraModel = "Galaxy S21 5G (SM-G991B)"
            )
            // Intentionally set filesystem modified time to something different (e.g. today or 2 weeks ago)
            photoFile.setLastModified(System.currentTimeMillis() - (14 * 86400 * 1000L))
        }
        files.add(photoFile)

        // 2. Camera photo with older EXIF date (2024)
        val photoExifFile = File(samplesDir, "IMG_20240510_142030.jpg")
        if (!photoExifFile.exists()) {
            createSampleJpegWithExif(
                targetFile = photoExifFile,
                exifDate = "2024:05:10 14:20:30",
                offset = null,
                cameraModel = "Pixel 8 Pro"
            )
            photoExifFile.setLastModified(System.currentTimeMillis())
        }
        files.add(photoExifFile)

        // 3. Samsung Screenshot file (filename timestamp only, no EXIF)
        val screenshotFile = File(samplesDir, "Screenshot_20261102_084512.png")
        if (!screenshotFile.exists()) {
            createMinimalPng(screenshotFile)
            screenshotFile.setLastModified(System.currentTimeMillis() - (5 * 86400 * 1000L))
        }
        files.add(screenshotFile)

        // 4. DOCX Document with docProps/core.xml dcterms:created
        val docxFile = File(samplesDir, "Quarterly_Audit_2024.docx")
        if (!docxFile.exists()) {
            createSampleDocx(
                targetFile = docxFile,
                createdIso = "2024-03-01T09:00:00Z",
                modifiedIso = "2024-03-02T16:45:00Z",
                author = "Chief Registrar",
                title = "Quarterly Metadata Audit"
            )
            docxFile.setLastModified(System.currentTimeMillis() - (30 * 86400 * 1000L))
        }
        files.add(docxFile)

        // 5. PDF Document with /CreationDate
        val pdfFile = File(samplesDir, "System_Specification.pdf")
        if (!pdfFile.exists()) {
            createSamplePdf(
                targetFile = pdfFile,
                creationDate = "D:20240215103000Z",
                title = "Regalia Architecture Spec"
            )
            pdfFile.setLastModified(System.currentTimeMillis())
        }
        files.add(pdfFile)

        // 6. Video with Samsung video filename
        val videoFile = File(samplesDir, "20261014_171500.mp4")
        if (!videoFile.exists()) {
            createSampleMp4(videoFile)
            videoFile.setLastModified(System.currentTimeMillis() - (20 * 86400 * 1000L))
        }
        files.add(videoFile)

        files.map { file ->
            FileItem(
                uri = Uri.fromFile(file),
                name = file.name,
                mimeType = when (file.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "pdf" -> "application/pdf"
                    "mp4" -> "video/mp4"
                    else -> "application/octet-stream"
                },
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                pathOrTree = file.parent ?: "",
                extension = file.extension.lowercase(),
                isSample = true,
                directFilePath = file.absolutePath,
                displayPath = "Demo Suite: /sample_media/${file.name}"
            )
        }
    }

    private fun createSampleJpegWithExif(
        targetFile: File,
        exifDate: String,
        offset: String?,
        cameraModel: String
    ) {
        // Minimal valid 1x1 JPEG
        val minimalJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(), // JFIF header
            0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x43.toByte(), 0x00.toByte(), // DQT
            *ByteArray(64) { 0x10.toByte() },
            0xFF.toByte(), 0xC0.toByte(), 0x00.toByte(), 0x0B.toByte(), 0x08.toByte(), // SOF0
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x11.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x00.toByte(), // DHT
            0x00.toByte(), 0x01.toByte(), 0x05.toByte(), 0x01.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(),
            0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(),
            0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(),
            0xFF.toByte(), 0xDA.toByte(), 0x00.toByte(), 0x08.toByte(), 0x01.toByte(), // SOS
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x00.toByte(),
            0xBF.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xD9.toByte() // EOI
        )

        FileOutputStream(targetFile).use { it.write(minimalJpeg) }

        // Inject EXIF metadata tags
        try {
            val exif = ExifInterface(targetFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
            if (offset != null) {
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            }
            exif.setAttribute(ExifInterface.TAG_MAKE, "Samsung")
            exif.setAttribute(ExifInterface.TAG_MODEL, cameraModel)
            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, "4032")
            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, "3024")
            exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "50")
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            exif.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun createMinimalPng(targetFile: File) {
        val minimalPng = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
            0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(), 0xB4.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(),
            0x42.toByte(), 0x60.toByte(), 0x82.toByte()
        )
        FileOutputStream(targetFile).use { it.write(minimalPng) }
    }

    private fun createSampleDocx(
        targetFile: File,
        createdIso: String,
        modifiedIso: String,
        author: String,
        title: String
    ) {
        val coreXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:dcterms="http://purl.org/dc/terms/"
                xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
                <cp:lastModifiedBy>$author</cp:lastModifiedBy>
                <cp:revision>3</cp:revision>
                <dcterms:created xsi:type="dcterms:W3CDTF">$createdIso</dcterms:created>
                <dcterms:modified xsi:type="dcterms:W3CDTF">$modifiedIso</dcterms:modified>
            </cp:coreProperties>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(targetFile)).use { zip ->
            val entry = ZipEntry("docProps/core.xml")
            zip.putNextEntry(entry)
            zip.write(coreXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun createSamplePdf(targetFile: File, creationDate: String, title: String) {
        val pdfContent = """
            %PDF-1.4
            1 0 obj
            << /Title ($title) /CreationDate ($creationDate) /ModDate ($creationDate) >>
            endobj
            xref
            0 2
            0000000000 65535 f 
            0000000009 00000 n 
            trailer
            << /Size 2 /Info 1 0 R >>
            startxref
            100
            %%EOF
        """.trimIndent()
        FileOutputStream(targetFile).use { it.write(pdfContent.toByteArray(Charsets.ISO_8859_1)) }
    }

    private fun createSampleMp4(targetFile: File) {
        // Minimal ftyp + moov container bytes with mvhd atom
        val mp4Bytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            0x66, 0x74, 0x79, 0x70, // ftyp
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31,
            0x6D, 0x70, 0x34, 0x31,
            // moov box
            0x00, 0x00, 0x00, 0x30,
            0x6D, 0x6F, 0x6F, 0x76,
            // mvhd box
            0x00, 0x00, 0x00, 0x28,
            0x6D, 0x76, 0x68, 0x64,
            0x00, // version 0
            0x00, 0x00, 0x00, // flags
            // creation_time (seconds since 1904 UTC: e.g. 3,874,800,000)
            0x00, 0x00, 0x00, 0x00,
            // modification_time
            0x00, 0x00, 0x00, 0x00
        )
        FileOutputStream(targetFile).use { it.write(mp4Bytes) }
    }
}

package com.example.sdk

import com.payme.sdk.ui.miniapp.source.DownloadResult
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.SourceDownloadException
import com.payme.sdk.ui.miniapp.source.SourceDownloadFailureReason
import com.payme.sdk.ui.miniapp.source.SourceDownloader
import com.payme.sdk.ui.miniapp.source.SourceInstallFailureReason
import com.payme.sdk.ui.miniapp.source.SourceInstaller
import com.payme.sdk.ui.miniapp.source.StorageSpaceChecker
import com.payme.sdk.ui.miniapp.source.VersionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SourceDownloaderTest {
    private var responseUrl = ""
    private fun downloader(
        filesDir: File,
        storageSpaceChecker: StorageSpaceChecker = StorageSpaceChecker.unbounded(),
        maxDownloadBytes: Long = com.payme.sdk.utils.ArchiveUtils.MAX_ARCHIVE_BYTES
    ) = SourceDownloader(
        filesDir,
        storageSpaceChecker,
        connectionFactory = { java.net.URL(responseUrl).openConnection() as java.net.HttpURLConnection },
        maxDownloadBytes = maxDownloadBytes
    )

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloaderDownloadsHttpSuccessAndReturnsBytes() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir)
        val destination = downloader.resetDestination()
        val body = "zip bytes".toByteArray()
        lateinit var result: DownloadResult

        withRawHttpResponse(
            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n" + String(body)
        ) { url ->
            responseUrl = url
            result = downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
        }

        assertEquals(body.size.toLong(), result.bytesCopied)
        assertEquals(body.size.toLong(), result.contentLength)
        assertEquals("zip bytes", destination.readText())
    }

    @Test
    fun downloaderHttpErrorDoesNotWriteDestination() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n") { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertEquals(SourceDownloadFailureReason.HTTP_ERROR, e.reason)
                assertEquals(404, e.statusCode)
            }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun downloaderEmptyBodyDoesNotWriteDestination() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n") { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertEquals(SourceDownloadFailureReason.EMPTY_BODY, e.reason)
            }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun downloaderIncompleteBodyDoesNotPersistFile() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n12345") { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertTrue(
                    e.reason == SourceDownloadFailureReason.INCOMPLETE_BODY ||
                        e.reason == SourceDownloadFailureReason.IO_ERROR
                )
            }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun downloaderAllowsUnknownContentLength() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n12345") { url ->
            responseUrl = url
            val result = downloader.download("https://static.payme.vn/test.zip", destination) { _, length, _ ->
                assertEquals(-1L, length)
            }

            assertEquals(5L, result.bytesCopied)
            assertEquals(-1L, result.contentLength)
        }

        assertEquals("12345", destination.readText())
    }

    @Test
    fun downloaderAbortsStreamAboveCapWhenContentLengthIsAbsent() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir, maxDownloadBytes = 10L)
        val destination = downloader.resetDestination()
        var largestReported = 0L

        withRawHttpResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n" + "x".repeat(64 * 1024)) { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { copied, _, _ -> largestReported = copied }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertEquals(SourceDownloadFailureReason.IO_ERROR, e.reason)
            }
        }

        assertTrue(largestReported <= 10L)
        assertFalse(destination.exists())
        assertFalse(File(File(filesDir, MiniAppSourceConstants.UPDATE_DIR), MiniAppSourceConstants.SOURCE_TEMP_ZIP).exists())
    }

    @Test
    fun downloaderRejectsDeclaredContentLengthAboveCap() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(filesDir, maxDownloadBytes = 4L)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n12345") { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertEquals(SourceDownloadFailureReason.IO_ERROR, e.reason)
            }
        }

        assertFalse(destination.exists())
    }

    private fun manifestRepository(maxManifestBytes: Long) = VersionRepository(
        connectionFactory = { java.net.URL(responseUrl).openConnection() as java.net.HttpURLConnection },
        maxManifestBytes = maxManifestBytes
    )

    @Test
    fun manifestDownloadWithinCapIsWritten() {
        val versionFile = File(temporaryFolder.newFolder("files"), "version.json")
        withRawHttpResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n[]") { url ->
            responseUrl = url
            manifestRepository(maxManifestBytes = 2L).downloadManifest(versionFile, "https://static.payme.vn/manifest.json")
        }
        assertEquals("[]", versionFile.readText())
        assertEquals(1024L * 1024, VersionRepository.MAX_MANIFEST_BYTES)
    }

    @Test
    fun manifestAbortsStreamAboveCapWhenContentLengthIsAbsent() {
        val versionFile = File(temporaryFolder.newFolder("files"), "version.json")
        withRawHttpResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n" + "[" + "0,".repeat(32 * 1024) + "0]") { url ->
            responseUrl = url
            try {
                manifestRepository(maxManifestBytes = 10L).downloadManifest(versionFile, "https://static.payme.vn/manifest.json")
                fail("Expected IOException")
            } catch (_: IOException) { }
        }
        assertFalse(versionFile.exists())
    }

    @Test
    fun manifestRejectsDeclaredContentLengthAboveCap() {
        val versionFile = File(temporaryFolder.newFolder("files"), "version.json")
        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n[1,2]") { url ->
            responseUrl = url
            try {
                manifestRepository(maxManifestBytes = 4L).downloadManifest(versionFile, "https://static.payme.vn/manifest.json")
                fail("Expected IOException")
            } catch (_: IOException) { }
        }
        assertFalse(versionFile.exists())
    }

    @Test
    fun downloaderRejectsKnownContentLengthWhenStorageIsInsufficient() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = downloader(
            filesDir = filesDir,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )
        val destination = downloader.resetDestination()
        val body = "zip bytes".toByteArray()

        withRawHttpResponse(
            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n" + String(body)
        ) { url ->
            responseUrl = url
            try {
                downloader.download("https://static.payme.vn/test.zip", destination) { _, _, _ -> }
                fail("Expected SourceDownloadException")
            } catch (e: SourceDownloadException) {
                assertEquals(SourceDownloadFailureReason.INSUFFICIENT_STORAGE, e.reason)
            }
        }

        assertFalse(destination.exists())
        assertFalse(
            File(
                File(filesDir, MiniAppSourceConstants.UPDATE_DIR),
                MiniAppSourceConstants.SOURCE_TEMP_ZIP
            ).exists()
        )
    }

    @Test
    fun unknownContentLengthDownloadsThenInstallStorageFailureKeepsOldSource() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val downloader = downloader(
            filesDir = filesDir,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )
        val destination = downloader.resetDestination()
        val zipBytes = zipBytes("build/index.html" to "new source")

        withRawHttpResponseBytes(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray() + zipBytes
        ) { url ->
            responseUrl = url
            val result = downloader.download("https://static.payme.vn/test.zip", destination) { _, length, _ ->
                assertEquals(-1L, length)
            }

            assertEquals(zipBytes.size.toLong(), result.bytesCopied)
            assertEquals(-1L, result.contentLength)
        }

        val installer = SourceInstaller(
            filesDir = filesDir,
            unzipFile = ::unzipForTest,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )
        val installResult = installer.installUpdatedSourceDetailed()

        assertFalse(installResult.success)
        assertEquals(SourceInstallFailureReason.INSUFFICIENT_STORAGE, installResult.failureReason)
        assertEquals("old source", File(www, "index.html").readText())
        assertFalse(installer.updateZip.exists())
        assertFalse(installer.updateTempZip.exists())
        assertFalse(File(filesDir, SourceInstaller.STAGING_DIR).exists())
        assertFalse(File(filesDir, SourceInstaller.BACKUP_DIR).exists())
    }
}

package com.example.sdk

import com.payme.sdk.ui.miniapp.source.DownloadResult
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.SourceDownloadException
import com.payme.sdk.ui.miniapp.source.SourceDownloadFailureReason
import com.payme.sdk.ui.miniapp.source.SourceDownloader
import com.payme.sdk.ui.miniapp.source.SourceInstallFailureReason
import com.payme.sdk.ui.miniapp.source.SourceInstaller
import com.payme.sdk.ui.miniapp.source.StorageSpaceChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloaderDownloadsHttpSuccessAndReturnsBytes() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = SourceDownloader(filesDir)
        val destination = downloader.resetDestination()
        val body = "zip bytes".toByteArray()
        lateinit var result: DownloadResult

        withRawHttpResponse(
            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n" + String(body)
        ) { url ->
            result = downloader.download(url, destination) { _, _, _ -> }
        }

        assertEquals(body.size.toLong(), result.bytesCopied)
        assertEquals(body.size.toLong(), result.contentLength)
        assertEquals("zip bytes", destination.readText())
    }

    @Test
    fun downloaderHttpErrorDoesNotWriteDestination() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = SourceDownloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n") { url ->
            try {
                downloader.download(url, destination) { _, _, _ -> }
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
        val downloader = SourceDownloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n") { url ->
            try {
                downloader.download(url, destination) { _, _, _ -> }
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
        val downloader = SourceDownloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n12345") { url ->
            try {
                downloader.download(url, destination) { _, _, _ -> }
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
        val downloader = SourceDownloader(filesDir)
        val destination = downloader.resetDestination()

        withRawHttpResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n12345") { url ->
            val result = downloader.download(url, destination) { _, length, _ ->
                assertEquals(-1L, length)
            }

            assertEquals(5L, result.bytesCopied)
            assertEquals(-1L, result.contentLength)
        }

        assertEquals("12345", destination.readText())
    }

    @Test
    fun downloaderRejectsKnownContentLengthWhenStorageIsInsufficient() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = SourceDownloader(
            filesDir = filesDir,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )
        val destination = downloader.resetDestination()
        val body = "zip bytes".toByteArray()

        withRawHttpResponse(
            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n" + String(body)
        ) { url ->
            try {
                downloader.download(url, destination) { _, _, _ -> }
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
        val downloader = SourceDownloader(
            filesDir = filesDir,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )
        val destination = downloader.resetDestination()
        val zipBytes = zipBytes("build/index.html" to "new source")

        withRawHttpResponseBytes(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray() + zipBytes
        ) { url ->
            val result = downloader.download(url, destination) { _, length, _ ->
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

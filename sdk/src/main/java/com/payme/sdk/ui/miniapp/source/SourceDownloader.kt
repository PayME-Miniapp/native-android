package com.payme.sdk.ui.miniapp.source

import android.content.Context
import com.payme.sdk.utils.ArchiveUtils
import com.payme.sdk.utils.copyTo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

internal data class DownloadResult(
    val bytesCopied: Long,
    val contentLength: Long,
    val destination: File
)

internal enum class SourceDownloadFailureReason {
    INVALID_CONNECTION,
    HTTP_ERROR,
    INSUFFICIENT_STORAGE,
    EMPTY_BODY,
    INCOMPLETE_BODY,
    INTEGRITY_MISMATCH,
    IO_ERROR
}

internal class SourceDownloadException(
    val reason: SourceDownloadFailureReason,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(
    buildMessage(reason, statusCode, cause),
    cause
) {
    companion object {
        private fun buildMessage(
            reason: SourceDownloadFailureReason,
            statusCode: Int?,
            cause: Throwable?
        ): String {
            return when (reason) {
                SourceDownloadFailureReason.INVALID_CONNECTION -> "Download URL is not an HTTP connection"
                SourceDownloadFailureReason.HTTP_ERROR -> "HTTP download failed with status $statusCode"
                SourceDownloadFailureReason.INSUFFICIENT_STORAGE -> "Not enough storage to download source"
                SourceDownloadFailureReason.EMPTY_BODY -> "Downloaded file is empty"
                SourceDownloadFailureReason.INCOMPLETE_BODY -> "Downloaded file is incomplete"
                SourceDownloadFailureReason.INTEGRITY_MISMATCH -> "Downloaded file does not match the signed manifest"
                SourceDownloadFailureReason.IO_ERROR -> cause?.message ?: "Download I/O error"
            }
        }
    }
}

internal class SourceDownloader private constructor(
    private val installerProvider: () -> SourceInstaller,
    private val storageSpaceChecker: StorageSpaceChecker,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val maxDownloadBytes: Long = ArchiveUtils.MAX_ARCHIVE_BYTES
) {
    constructor(context: Context) : this(
        installerProvider = { SourceInstaller(context) },
        storageSpaceChecker = StorageSpaceChecker(context)
    )

    internal constructor(
        filesDir: File,
        storageSpaceChecker: StorageSpaceChecker = StorageSpaceChecker.unbounded(),
        connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
        maxDownloadBytes: Long = ArchiveUtils.MAX_ARCHIVE_BYTES
    ) : this(
        installerProvider = {
            SourceInstaller(filesDir, unzipFile = { _, _ -> false })
        },
        storageSpaceChecker = storageSpaceChecker,
        connectionFactory = connectionFactory,
        maxDownloadBytes = maxDownloadBytes
    )

    fun resetDestination(): File {
        val installer = installerProvider()
        installer.prepareUpdateDirectory()
        val destination = installer.updateZip
        if (destination.exists()) {
            destination.delete()
        }
        OtaArtifact.metaFile(destination).delete()
        if (installer.updateTempZip.exists()) {
            installer.updateTempZip.delete()
        }
        return destination
    }

    /**
     * Downloads [url] and accepts it only if its byte count and streamed SHA-256 equal the signed
     * [artifact]; the artifact is then stored next to [destination] for the install step.
     */
    fun download(
        url: String,
        destination: File,
        artifact: OtaArtifact,
        onCopy: (totalBytesCopied: Long, length: Long, speed: Long) -> Unit
    ): DownloadResult {
        val installer = installerProvider()
        val sourceTemp = installer.updateTempZip
        var connection: HttpURLConnection? = null
        var lastUpdateTime = System.currentTimeMillis()
        var lastBytes = 0L
        var currentSpeed = 0L

        sourceTemp.parentFile?.mkdirs()
        sourceTemp.delete()
        destination.parentFile?.mkdirs()
        destination.delete()
        OtaArtifact.metaFile(destination).delete()
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            connection = OtaConnection.open(url, connectionFactory)

            val statusCode = connection.responseCode
            if (statusCode !in HTTP_SUCCESS_RANGE) {
                throw SourceDownloadException(
                    reason = SourceDownloadFailureReason.HTTP_ERROR,
                    statusCode = statusCode
                )
            }

            val length = connection.contentLengthLong
            if (length > maxDownloadBytes) throw IOException("OTA download exceeds size limit")
            if (length > 0L && length != artifact.size) {
                throw SourceDownloadException(SourceDownloadFailureReason.INTEGRITY_MISMATCH)
            }
            if (length > 0L && !storageSpaceChecker.hasEnoughSpace(length)) {
                throw SourceDownloadException(SourceDownloadFailureReason.INSUFFICIENT_STORAGE)
            }

            val bytesCopied = DigestInputStream(connection.inputStream, digest).use { input ->
                FileOutputStream(sourceTemp).use { output ->
                    input.copyTo(output, onCopy = { totalBytesCopied ->
                        // Content-Length can be absent or wrong: count what is actually streamed.
                        if (totalBytesCopied > maxDownloadBytes) throw IOException("OTA download exceeds size limit")
                        if (totalBytesCopied > artifact.size) {
                            throw SourceDownloadException(SourceDownloadFailureReason.INTEGRITY_MISMATCH)
                        }
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdateTime

                        if (timeDiff >= MIN_SPEED_SAMPLE_MS) {
                            val bytesDiff = totalBytesCopied - lastBytes
                            val instantSpeed = (bytesDiff * 1000) / timeDiff
                            currentSpeed = if (currentSpeed == 0L) {
                                instantSpeed
                            } else {
                                (currentSpeed * 2 + instantSpeed) / 3
                            }

                            lastBytes = totalBytesCopied
                            lastUpdateTime = currentTime
                        }

                        onCopy(totalBytesCopied, length, currentSpeed)
                    })
                }
            }

            if (bytesCopied <= 0L || sourceTemp.length() <= 0L) {
                throw SourceDownloadException(SourceDownloadFailureReason.EMPTY_BODY)
            }
            if (length >= 0 && bytesCopied < length) {
                throw SourceDownloadException(SourceDownloadFailureReason.INCOMPLETE_BODY)
            }
            val sha256 = OtaArtifact.hex(digest.digest())
            if (bytesCopied != artifact.size || !MessageDigest.isEqual(sha256.toByteArray(), artifact.sha256.toByteArray())) {
                throw SourceDownloadException(SourceDownloadFailureReason.INTEGRITY_MISMATCH)
            }

            sourceTemp.copyTo(destination, overwrite = true)
            artifact.write(destination)
            return DownloadResult(bytesCopied, length, destination)
        } catch (e: SourceDownloadException) {
            destination.delete()
            OtaArtifact.metaFile(destination).delete()
            throw e
        } catch (e: IOException) {
            destination.delete()
            OtaArtifact.metaFile(destination).delete()
            throw SourceDownloadException(SourceDownloadFailureReason.IO_ERROR, cause = e)
        } finally {
            sourceTemp.delete()
            connection?.disconnect()
        }
    }

    private companion object {
        const val MIN_SPEED_SAMPLE_MS = 50L
        val HTTP_SUCCESS_RANGE = 200..299
    }
}

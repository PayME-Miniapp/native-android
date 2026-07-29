package com.payme.sdk.webServer

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

internal object WebServerFileResponder {
    fun serveFile(
        header: Map<String, String>,
        file: File,
        mime: String,
        forbiddenResponse: (String) -> Response
    ): Response {
        return try {
            val etag = (file.absolutePath + file.lastModified() + file.length()).hashCode().toString(16)
            val range = parseRange(header["range"])
            val ifRange = header["if-range"]
            val ifNoneMatch = header["if-none-match"]
            val fileLength = file.length()

            val headerIfRangeMissingOrMatching = ifRange == null || etag == ifRange
            val headerIfNoneMatchPresentAndMatching = ifNoneMatch != null &&
                ("*" == ifNoneMatch || ifNoneMatch == etag)

            when {
                headerIfRangeMissingOrMatching &&
                    range != null &&
                    range.start >= 0 &&
                    range.start < fileLength -> partialContent(file, mime, etag, fileLength, range, headerIfNoneMatchPresentAndMatching)

                headerIfRangeMissingOrMatching &&
                    range != null &&
                    range.start >= fileLength -> rangeNotSatisfiable(etag, fileLength)

                range == null && headerIfNoneMatchPresentAndMatching -> notModified(mime, etag)

                !headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching -> notModified(mime, etag)

                else -> newFixedFileResponse(file, mime).also { response ->
                    response.addHeader("Content-Length", fileLength.toString())
                    response.addHeader("ETag", etag)
                }
            }
        } catch (ioe: IOException) {
            forbiddenResponse("Reading file failed.")
        }
    }

    private fun partialContent(
        file: File,
        mime: String,
        etag: String,
        fileLength: Long,
        range: ByteRange,
        isNotModified: Boolean
    ): Response {
        if (isNotModified) {
            return notModified(mime, etag)
        }

        val endAt = if (range.end < 0) fileLength - 1 else range.end
        val newLength = (endAt - range.start + 1).coerceAtLeast(0)
        val inputStream = FileInputStream(file)
        skipFully(inputStream, range.start)
        return NanoHTTPD.newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            mime,
            inputStream,
            newLength
        ).also { response ->
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", newLength.toString())
            response.addHeader("Content-Range", "bytes ${range.start}-$endAt/$fileLength")
            response.addHeader("ETag", etag)
        }
    }

    private fun rangeNotSatisfiable(etag: String, fileLength: Long): Response {
        return MySimpleWebServer.newFixedLengthResponse(
            Response.Status.RANGE_NOT_SATISFIABLE,
            NanoHTTPD.MIME_PLAINTEXT,
            ""
        ).also { response ->
            response.addHeader("Content-Range", "bytes */$fileLength")
            response.addHeader("ETag", etag)
        }
    }

    private fun notModified(mime: String, etag: String): Response {
        return MySimpleWebServer.newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "")
            .also { response -> response.addHeader("ETag", etag) }
    }

    private fun parseRange(rangeHeader: String?): ByteRange? {
        var range = rangeHeader ?: return null
        if (!range.startsWith("bytes=")) {
            return null
        }
        range = range.substring("bytes=".length)
        val minus = range.indexOf('-')
        var startFrom = 0L
        var endAt = -1L
        if (minus > 0) {
            try {
                startFrom = range.substring(0, minus).toLong()
                endAt = range.substring(minus + 1).toLong()
            } catch (ignored: NumberFormatException) {
            }
        }
        return ByteRange(start = startFrom, end = endAt)
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long) {
        var skipped = 0L
        while (skipped < bytesToSkip) {
            val delta = inputStream.skip(bytesToSkip - skipped)
            if (delta <= 0) {
                break
            }
            skipped += delta
        }
    }

    @Throws(IOException::class)
    private fun newFixedFileResponse(file: File, mime: String): Response {
        val response = NanoHTTPD.newFixedLengthResponse(
            Response.Status.OK,
            mime,
            Files.newInputStream(file.toPath()),
            file.length()
        )
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private data class ByteRange(val start: Long, val end: Long)
}

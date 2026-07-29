package com.example.sdk

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal fun writeZip(file: File, vararg entries: Pair<String, String>) {
    file.parentFile?.mkdirs()
    ZipOutputStream(file.outputStream()).use { zip ->
        entries.forEach { (name, contents) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(contents.toByteArray())
            zip.closeEntry()
        }
    }
}

internal fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, contents) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(contents.toByteArray())
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

internal fun unzipForTest(zip: File, destination: File): Boolean {
    return try {
        var extracted = false
        ZipInputStream(zip.inputStream()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val target = File(destination, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> input.copyTo(output) }
                    extracted = true
                }
                entry = input.nextEntry
            }
        }
        extracted
    } catch (e: Exception) {
        false
    }
}

internal fun withRawHttpResponse(response: String, block: (String) -> Unit) {
    withRawHttpResponseBytes(response.toByteArray(), block)
}

internal fun withRawHttpResponseBytes(response: ByteArray, block: (String) -> Unit) {
    val server = ServerSocket(0)
    val thread = Thread {
        try {
            server.use { socket ->
                socket.accept().use { client ->
                    val input = client.getInputStream()
                    val endOfHeaders = byteArrayOf(
                        '\r'.code.toByte(),
                        '\n'.code.toByte(),
                        '\r'.code.toByte(),
                        '\n'.code.toByte()
                    )
                    var matched = 0
                    while (matched < endOfHeaders.size) {
                        val current = input.read()
                        if (current == -1) {
                            break
                        }
                        val currentByte = current.toByte()
                        matched = if (currentByte == endOfHeaders[matched]) {
                            matched + 1
                        } else if (currentByte == endOfHeaders[0]) {
                            1
                        } else {
                            0
                        }
                    }
                    client.getOutputStream().use { output ->
                        output.write(response)
                        output.flush()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    thread.start()
    try {
        block("http://127.0.0.1:${server.localPort}/source.zip")
    } finally {
        server.close()
        thread.join(1000)
    }
}

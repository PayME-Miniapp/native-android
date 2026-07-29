package com.payme.sdk.webServer

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object WebServerPathResolver {
    fun findIndexFileInDirectory(directory: File?): String? {
        return try {
            if (directory == null || !directory.exists() || !directory.isDirectory) {
                return null
            }

            for (fileName in MySimpleWebServer.INDEX_FILE_NAMES) {
                try {
                    val indexFile = File(directory, fileName)
                    if (indexFile.isFile) {
                        return fileName
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun fileForUri(homeDir: File, uri: String): File? {
        val decodedUri = decodeUri(uri) ?: return null
        if (isUnsafeUri(decodedUri)) {
            return null
        }
        var relativeUri = decodedUri
        while (relativeUri.startsWith("/")) {
            relativeUri = relativeUri.substring(1)
        }
        val file = File(homeDir, relativeUri)
        return if (isWithinRoot(homeDir, file)) file else null
    }

    fun isUnsafeRequestUri(uri: String): Boolean {
        return decodeUri(uri)?.let { isUnsafeUri(it) } != false
    }

    fun appendPath(uri: String, fileName: String): String {
        return if (uri.endsWith("/")) uri + fileName else "$uri/$fileName"
    }

    fun isSpaRoute(uri: String): Boolean {
        var lastPathSegment = uri
        val slashIndex = lastPathSegment.lastIndexOf('/')
        if (slashIndex >= 0) {
            lastPathSegment = lastPathSegment.substring(slashIndex + 1)
        }
        return !lastPathSegment.contains(".")
    }

    @Suppress("DEPRECATION")
    private fun decodeUri(uri: String): String? {
        return try {
            URLDecoder.decode(uri, StandardCharsets.UTF_8.name()).replace(File.separatorChar, '/')
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun isUnsafeUri(uri: String): Boolean {
        if (uri.indexOf('\u0000') >= 0) {
            return true
        }
        return uri.split('/').any { it == ".." }
    }

    private fun isWithinRoot(homeDir: File, file: File): Boolean {
        return try {
            val rootPath = homeDir.canonicalPath
            val filePath = file.canonicalPath
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        } catch (e: IOException) {
            false
        }
    }
}

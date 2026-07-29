package com.payme.sdk.webServer

import android.os.Build
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.StringTokenizer

internal object WebServerDirectoryHandler {
    fun listDirectory(uri: String, directory: File): String {
        val heading = "Directory $uri"
        val message = StringBuilder(
            "<html><head><title>$heading</title><style><!--\n" +
                "span.dirname { font-weight: bold; }\n" +
                "span.filesize { font-size: 75%; }\n" +
                "// -->\n" +
                "</style></head><body><h1>$heading</h1>"
        )

        val up = parentUri(uri)
        val files = requireNotNull(directory.list { dir, name -> File(dir, name).isFile }).sorted()
        val directories = requireNotNull(directory.list { dir, name -> File(dir, name).isDirectory }).sorted()
        if (up != null || directories.size + files.size > 0) {
            message.append("<ul>")
            appendDirectories(message, uri, up, directories)
            appendFiles(message, uri, directory, files)
            message.append("</ul>")
        }
        message.append("</body></html>")
        return message.toString()
    }

    private fun parentUri(uri: String): String? {
        if (uri.length <= 1) {
            return null
        }
        val parentUri = uri.substring(0, uri.length - 1)
        val slash = parentUri.lastIndexOf('/')
        return if (slash >= 0) uri.substring(0, slash + 1) else null
    }

    private fun appendDirectories(
        message: StringBuilder,
        uri: String,
        up: String?,
        directories: List<String>
    ) {
        if (up == null && directories.isEmpty()) {
            return
        }
        message.append("<section class=\"directories\">")
        if (up != null) {
            message
                .append("<li><a rel=\"directory\" href=\"")
                .append(up)
                .append("\"><span class=\"dirname\">..</span></a></li>")
        }
        for (childDirectory in directories) {
            val directoryName = "$childDirectory/"
            message
                .append("<li><a rel=\"directory\" href=\"")
                .append(encodeUri(uri + directoryName))
                .append("\"><span class=\"dirname\">")
                .append(directoryName)
                .append("</span></a></li>")
        }
        message.append("</section>")
    }

    private fun appendFiles(
        message: StringBuilder,
        uri: String,
        directory: File,
        files: List<String>
    ) {
        if (files.isEmpty()) {
            return
        }
        message.append("<section class=\"files\">")
        for (file in files) {
            message
                .append("<li><a href=\"")
                .append(encodeUri(uri + file))
                .append("\"><span class=\"filename\">")
                .append(file)
                .append("</span></a>")
            appendFileSize(message, File(directory, file).length())
            message.append("</li>")
        }
        message.append("</section>")
    }

    private fun appendFileSize(message: StringBuilder, length: Long) {
        message.append("&nbsp;<span class=\"filesize\">(")
        when {
            length < 1024 -> message.append(length).append(" bytes")
            length < 1024 * 1024 -> {
                message
                    .append(length / 1024)
                    .append(".")
                    .append(length % 1024 / 10 % 100)
                    .append(" KB")
            }
            else -> {
                message
                    .append(length / (1024 * 1024))
                    .append(".")
                    .append(length % (1024 * 1024) / 10000 % 100)
                    .append(" MB")
            }
        }
        message.append(")</span>")
    }

    private fun encodeUri(uri: String): String {
        val newUri = StringBuilder()
        val tokenizer = StringTokenizer(uri, "/ ", true)
        while (tokenizer.hasMoreTokens()) {
            when (val token = tokenizer.nextToken()) {
                "/" -> newUri.append("/")
                " " -> newUri.append("%20")
                else -> newUri.append(encodeUriSegment(token))
            }
        }
        return newUri.toString()
    }

    @Suppress("DEPRECATION")
    private fun encodeUriSegment(token: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            URLEncoder.encode(token, StandardCharsets.UTF_8)
        } else {
            URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        }
    }
}

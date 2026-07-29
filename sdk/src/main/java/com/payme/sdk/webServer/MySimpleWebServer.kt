package com.payme.sdk.webServer

import android.os.Build
import fi.iki.elonen.InternalRewrite
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.WebServerPlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Collections

open class MySimpleWebServer : NanoHTTPD {
    private val quiet: Boolean
    private val cors: String?
    protected var rootDirs: MutableList<File>

    constructor(host: String?, port: Int, wwwroot: File?, quiet: Boolean, cors: String?) : this(
        host,
        port,
        wwwroot?.let { listOf(it) }.orEmpty(),
        quiet,
        cors
    )

    constructor(host: String?, port: Int, wwwroot: File?, quiet: Boolean) : this(
        host,
        port,
        wwwroot,
        quiet,
        null
    )

    constructor(host: String?, port: Int, wwwroots: List<File>, quiet: Boolean) : this(
        host,
        port,
        wwwroots,
        quiet,
        null
    )

    constructor(host: String?, port: Int, wwwroots: List<File>, quiet: Boolean, cors: String?) : super(
        host,
        port
    ) {
        this.quiet = quiet
        this.cors = cors
        this.rootDirs = ArrayList(wwwroots)
        init()
    }

    private fun canServeUri(uri: String, homeDir: File?): Boolean {
        return try {
            if (homeDir == null) {
                return false
            }

            val file = WebServerPathResolver.fileForUri(homeDir, uri) ?: return false
            var canServeUri = try {
                file.exists()
            } catch (e: Exception) {
                System.err.println("Error checking if file exists: ${e.message}")
                return false
            }

            if (!canServeUri) {
                try {
                    val plugin = mimeTypeHandlers[getMimeTypeForFile(uri)]
                    if (plugin != null) {
                        canServeUri = plugin.canServeUri(uri, homeDir)
                    }
                } catch (e: Exception) {
                    System.err.println("Error in plugin handler: ${e.message}")
                    return false
                }
            }
            canServeUri
        } catch (e: Exception) {
            System.err.println("Unexpected error in canServeUri: ${e.message}")
            false
        }
    }

    protected open fun getForbiddenResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.FORBIDDEN,
            NanoHTTPD.MIME_PLAINTEXT,
            "FORBIDDEN: $message"
        )
    }

    protected open fun getInternalErrorResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            NanoHTTPD.MIME_PLAINTEXT,
            "INTERNAL ERROR: $message"
        )
    }

    protected open fun getNotFoundResponse(): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            NanoHTTPD.MIME_PLAINTEXT,
            "Error 404, file not found."
        )
    }

    /**
     * Used to initialize and customize the server.
     */
    open fun init() {
    }

    protected open fun listDirectory(uri: String, directory: File): String {
        return WebServerDirectoryHandler.listDirectory(uri, directory)
    }

    private fun respond(headers: Map<String, String>, session: IHTTPSession, uri: String): Response {
        val response = defaultRespond(headers, session, uri)
        return if (cors != null) addCORSHeaders(headers, response, cors) else response
    }

    private fun defaultRespond(headers: Map<String, String>, session: IHTTPSession, requestUri: String): Response {
        val currentDepth = recursionCounter.get() ?: 0
        try {
            if (currentDepth > MAX_RECURSION_DEPTH) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Error: Maximum recursion depth exceeded"
                )
            }

            recursionCounter.set(currentDepth + 1)

            var uri = requestUri.trim().replace(File.separatorChar, '/')
            if (uri.indexOf('?') >= 0) {
                uri = uri.substring(0, uri.indexOf('?'))
            }

            if (WebServerPathResolver.isUnsafeRequestUri(uri)) {
                return getForbiddenResponse("Won't serve ../ for security reasons.")
            }

            try {
                val home = rootDirs.firstOrNull() ?: return getNotFoundResponse()
                val file = WebServerPathResolver.fileForUri(home, uri)
                    ?: return getForbiddenResponse("Won't serve files outside the web root.")
                val fileExists = try {
                    file.exists()
                } catch (e: Exception) {
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        NanoHTTPD.MIME_PLAINTEXT,
                        "Error accessing file system: ${e.message}"
                    )
                }

                if (uri.endsWith("/")) {
                    return try {
                        val indexFile = WebServerPathResolver.findIndexFileInDirectory(file)
                            ?: return getNotFoundResponse()
                        respond(headers, session, WebServerPathResolver.appendPath(uri, indexFile))
                    } catch (e: Exception) {
                        newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "Error finding index file: ${e.message}"
                        )
                    }
                }

                if (!fileExists) {
                    if (WebServerPathResolver.isSpaRoute(uri)) {
                        val indexFile = WebServerPathResolver.findIndexFileInDirectory(home)
                            ?: return getNotFoundResponse()
                        return respond(headers, session, "/$indexFile")
                    }
                    return getNotFoundResponse()
                }
            } catch (e: Exception) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Server error: ${e.message}"
                )
            }

            return try {
                var canServeUri = false
                var homeDir: File? = null
                var directoryIndex = 0
                while (!canServeUri && directoryIndex < rootDirs.size) {
                    try {
                        homeDir = rootDirs[directoryIndex]
                        canServeUri = canServeUri(uri, homeDir)
                    } catch (e: Exception) {
                        System.err.println("Error checking directory $directoryIndex: ${e.message}")
                    }
                    directoryIndex++
                }

                if (!canServeUri) {
                    return getNotFoundResponse()
                }

                val selectedHomeDir = homeDir ?: return getNotFoundResponse()
                val file = WebServerPathResolver.fileForUri(selectedHomeDir, uri)
                    ?: return getForbiddenResponse("Won't serve files outside the web root.")
                if (file.isDirectory && !uri.endsWith("/")) {
                    val redirectUri = "$uri/"
                    val response = newFixedLengthResponse(
                        Response.Status.REDIRECT,
                        NanoHTTPD.MIME_HTML,
                        "<html><body>Redirected: <a href=\"$redirectUri\">$redirectUri</a></body></html>"
                    )
                    response.addHeader("Location", redirectUri)
                    return response
                }

                if (file.isDirectory) {
                    val indexFile = WebServerPathResolver.findIndexFileInDirectory(file)
                    return if (indexFile == null) {
                        if (file.canRead()) {
                            newFixedLengthResponse(
                                Response.Status.OK,
                                NanoHTTPD.MIME_HTML,
                                listDirectory(uri, file)
                            )
                        } else {
                            getForbiddenResponse("No directory listing.")
                        }
                    } else {
                        respond(headers, session, uri + indexFile)
                    }
                }

                val mimeTypeForFile = getMimeTypeForFile(uri)
                val plugin = mimeTypeHandlers[mimeTypeForFile]
                if (plugin != null && plugin.canServeUri(uri, selectedHomeDir)) {
                    try {
                        val pluginResponse = plugin.serveFile(uri, headers, session, file, mimeTypeForFile)
                        if (pluginResponse is InternalRewrite) {
                            return respond(pluginResponse.headers, session, pluginResponse.uri)
                        }
                        pluginResponse
                    } catch (e: Exception) {
                        newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "Plugin error: ${e.message}"
                        )
                    }
                } else {
                    try {
                        serveFile(uri, headers, file, mimeTypeForFile)
                    } catch (e: Exception) {
                        newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "File service error: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Server processing error: ${e.message}"
                )
            }
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                NanoHTTPD.MIME_PLAINTEXT,
                "Critical error: ${e.message}"
            )
        } finally {
            recursionCounter.set(currentDepth)
        }
    }

    @Suppress("DEPRECATION")
    override fun serve(session: IHTTPSession): Response {
        recursionCounter.set(0)

        return try {
            val header = session.headers
            val parameters = session.parms
            val uri = session.uri

            if (!quiet) {
                println("${session.method} '$uri' ")

                for (key in header.keys) {
                    println("  HDR: '$key' = '${header[key]}'")
                }
                for (key in parameters.keys) {
                    println("  PRM: '$key' = '${parameters[key]}'")
                }
            }

            try {
                for (homeDir in rootDirs) {
                    try {
                        if (!homeDir.isDirectory) {
                            return getInternalErrorResponse("given path is not a directory ($homeDir).")
                        }
                    } catch (e: Exception) {
                        return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "Error checking directory: ${e.message}"
                        )
                    }
                }

                respond(Collections.unmodifiableMap(header), session, uri)
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Server error: ${e.message}"
                )
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                NanoHTTPD.MIME_PLAINTEXT,
                "Critical server error: ${t.message}"
            )
        } finally {
            recursionCounter.set(0)
        }
    }

    /**
     * Serves file from homeDir and its subdirectories only. Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    open fun serveFile(uri: String, header: Map<String, String>, file: File, mime: String): Response {
        return WebServerFileResponder.serveFile(header, file, mime, ::getForbiddenResponse)
    }

    protected open fun addCORSHeaders(queryHeaders: Map<String, String>, response: Response, cors: String): Response {
        response.addHeader("Access-Control-Allow-Origin", cors)
        response.addHeader("Access-Control-Allow-Headers", calculateAllowHeaders(queryHeaders))
        response.addHeader("Access-Control-Allow-Credentials", "true")
        response.addHeader("Access-Control-Allow-Methods", ALLOWED_METHODS)
        response.addHeader("Access-Control-Max-Age", MAX_AGE.toString())
        return response
    }

    @Suppress("UNUSED_PARAMETER")
    private fun calculateAllowHeaders(queryHeaders: Map<String, String>): String {
        return System.getProperty(ACCESS_CONTROL_ALLOW_HEADER_PROPERTY_NAME) ?: DEFAULT_ALLOWED_HEADERS
    }

    companion object {
        @JvmField
        val INDEX_FILE_NAMES: MutableList<String> = arrayListOf("index.html", "index.htm")

        const val ACCESS_CONTROL_ALLOW_HEADER_PROPERTY_NAME = "AccessControlAllowHeader"

        @JvmField
        val DEFAULT_ALLOWED_HEADERS: String = "origin,accept,content-type"

        private const val ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD"
        private const val MAX_AGE = 42 * 60 * 60
        private const val MAX_RECURSION_DEPTH = 10

        private val mimeTypeHandlers: MutableMap<String, WebServerPlugin> = HashMap()

        private val recursionCounter = object : ThreadLocal<Int>() {
            override fun initialValue(): Int = 0
        }

        private val LICENCE: String = loadLicence()

        init {
            NanoHTTPD.mimeTypes()
        }

        private fun loadLicence(): String {
            var text = ""
            try {
                val stream = MySimpleWebServer::class.java.getResourceAsStream("/LICENSE.txt")
                if (stream != null) {
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var count: Int
                    while (stream.read(buffer).also { count = it } >= 0) {
                        bytes.write(buffer, 0, count)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        text = bytes.toString(StandardCharsets.UTF_8)
                    }
                    stream.close()
                } else {
                    text = "unknown"
                }
            } catch (e: Exception) {
                text = "unknown"
            }
            return text
        }

        @JvmStatic
        fun main(args: Array<String>) {
            WebServerCli.run(args, LICENCE, ::registerPluginForMimeType)
        }

        @JvmStatic
        fun registerPluginForMimeType(
            indexFiles: Array<String>?,
            mimeType: String?,
            plugin: WebServerPlugin?,
            commandLineOptions: Map<String, String>
        ) {
            if (mimeType == null || plugin == null) {
                return
            }

            if (indexFiles != null) {
                for (filename in indexFiles) {
                    val dot = filename.lastIndexOf('.')
                    if (dot >= 0) {
                        val extension = filename.substring(dot + 1).lowercase()
                        NanoHTTPD.mimeTypes()[extension] = mimeType
                    }
                }
                INDEX_FILE_NAMES.addAll(indexFiles)
            }
            mimeTypeHandlers[mimeType] = plugin
            plugin.initialize(commandLineOptions)
        }

        fun newFixedLengthResponse(status: Response.IStatus, mimeType: String?, message: String?): Response {
            val response = NanoHTTPD.newFixedLengthResponse(status, mimeType, message)
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }
    }
}

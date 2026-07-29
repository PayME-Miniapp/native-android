package com.payme.sdk.webServer

import fi.iki.elonen.WebServerPlugin
import fi.iki.elonen.WebServerPluginInfo
import fi.iki.elonen.util.ServerRunner
import java.io.File
import java.io.IOException
import java.util.ServiceLoader

internal object WebServerCli {
    fun run(
        args: Array<String>,
        licence: String,
        registerPlugin: (Array<String>?, String?, WebServerPlugin?, Map<String, String>) -> Unit
    ) {
        val options = parseOptions(args, licence)
        val rootDirs = options.rootDirs.ifEmpty { arrayListOf(File(".").absoluteFile) }
        val commandLineOptions = commandLineOptions(options, rootDirs)

        val serviceLoader = ServiceLoader.load(WebServerPluginInfo::class.java)
        for (info in serviceLoader) {
            val mimeTypes = info.mimeTypes
            for (mime in mimeTypes) {
                val indexFiles = info.getIndexFilesForMimeType(mime)
                if (!options.quiet) {
                    logPlugin(mime, indexFiles)
                }
                registerPlugin(indexFiles, mime, info.getWebServerPlugin(mime), commandLineOptions)
            }
        }
        ServerRunner.executeInstance(
            MySimpleWebServer(options.host, options.port, rootDirs, options.quiet, options.cors)
        )
    }

    private fun parseOptions(args: Array<String>, licence: String): CliOptions {
        val options = CliOptions()
        var index = 0
        while (index < args.size) {
            when {
                "-h".equals(args[index], ignoreCase = true) ||
                    "--host".equals(args[index], ignoreCase = true) -> options.host = args[index + 1]

                "-p".equals(args[index], ignoreCase = true) ||
                    "--port".equals(args[index], ignoreCase = true) -> options.port = args[index + 1].toInt()

                "-q".equals(args[index], ignoreCase = true) ||
                    "--quiet".equals(args[index], ignoreCase = true) -> options.quiet = true

                "-d".equals(args[index], ignoreCase = true) ||
                    "--dir".equals(args[index], ignoreCase = true) -> {
                    options.rootDirs.add(File(args[index + 1]).absoluteFile)
                }

                args[index].startsWith("--cors") -> {
                    options.cors = "*"
                    val equalIndex = args[index].indexOf('=')
                    if (equalIndex > 0) {
                        options.cors = args[index].substring(equalIndex + 1)
                    }
                }

                "--licence".equals(args[index], ignoreCase = true) -> println("$licence\n")

                args[index].startsWith("-X:") -> {
                    val dot = args[index].indexOf('=')
                    if (dot > 0) {
                        val name = args[index].substring(0, dot)
                        val value = args[index].substring(dot + 1)
                        options.extraOptions[name] = value
                    }
                }
            }
            index++
        }
        return options
    }

    private fun commandLineOptions(options: CliOptions, rootDirs: List<File>): Map<String, String> {
        val commandLineOptions = HashMap(options.extraOptions)
        options.host?.let { commandLineOptions["host"] = it }
        commandLineOptions["port"] = options.port.toString()
        commandLineOptions["quiet"] = options.quiet.toString()
        commandLineOptions["home"] = homeOption(rootDirs)
        return commandLineOptions
    }

    private fun homeOption(rootDirs: List<File>): String {
        val home = StringBuilder()
        for (directory in rootDirs) {
            if (home.isNotEmpty()) {
                home.append(":")
            }
            try {
                home.append(directory.canonicalPath)
            } catch (ignored: IOException) {
            }
        }
        return home.toString()
    }

    private fun logPlugin(mime: String, indexFiles: Array<String>?) {
        print("# Found plugin for Mime type: \"$mime\"")
        if (indexFiles != null) {
            print(" (serving index files: ")
            for (indexFile in indexFiles) {
                print("$indexFile ")
            }
        }
        println(").")
    }

    private data class CliOptions(
        var port: Int = 8080,
        var host: String? = null,
        val rootDirs: ArrayList<File> = ArrayList(),
        var quiet: Boolean = false,
        var cors: String? = null,
        val extraOptions: HashMap<String, String> = HashMap()
    )
}

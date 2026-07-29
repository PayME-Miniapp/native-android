package com.payme.sdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.URL

internal object FileDownloadUtils {
    fun findRandomOpenPort(): Int? {
        return try {
            val socket = ServerSocket(0)
            val port = socket.localPort
            Log.d(PayMEMiniApp.TAG, "port:$port")
            socket.close()
            port
        } catch (e: IOException) {
            MiniAppSourceConstants.DEFAULT_PORT
        }
    }

    fun downloadWithoutTemp(link: String, path: String) {
        URL(link).openStream().use { input ->
            FileOutputStream(File(path)).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(PayMEMiniApp.TAG, "done download")
    }

    @SuppressLint("SetTextI18n")
    fun download(
        context: Context,
        link: String,
        path: String,
        onCopy: (totalBytesCopied: Long, length: Int, speed: Long) -> Unit
    ) {
        val destination = File(path)
        val tempFile = File(
            File(context.filesDir, MiniAppSourceConstants.UPDATE_DIR),
            MiniAppSourceConstants.SOURCE_TEMP_ZIP
        )
        tempFile.parentFile?.mkdirs()
        val length = URL(link).openConnection().contentLength
        var lastUpdateTime = System.currentTimeMillis()
        var lastBytes = 0L
        var currentSpeed = 0L

        URL(link).openStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, onCopy = { totalBytesCopied ->
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

        if (tempFile.length() > 0) {
            tempFile.copyTo(destination, overwrite = true)
            Log.d(PayMEMiniApp.TAG, "done download")
            tempFile.delete()
        } else {
            Log.d(PayMEMiniApp.TAG, "download fail")
        }
    }

    private const val MIN_SPEED_SAMPLE_MS = 50L
}

package com.payme.sdk.ui.miniapp.source

import android.content.Context
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import java.io.File

internal class LocalhostServerController(
    private val getLoadUrl: () -> String,
    private val setLoadUrl: (String) -> Unit
) {
    private var webSource: LocalhostWebSource? = null
    var wwwRoot: File? = null
        private set

    fun start(context: Context, root: File): String? {
        if (webSource != null) {
            return getLoadUrl().ifEmpty { null }
        }
        wwwRoot = root
        Log.d(
            PayMEMiniApp.TAG,
            "Miniapp web root=${root.absolutePath}, index=${File(root, "index.html").isFile}"
        )
        val provider = LocalhostWebSource()
        val sourceUrl = try {
            provider.start(context, root)
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Localhost web source failed: ${e.message}")
            return null
        }
        webSource = provider
        Log.d(PayMEMiniApp.TAG, "Miniapp web source url=$sourceUrl")
        if (shouldReplaceLoadUrl(getLoadUrl())) {
            setLoadUrl(sourceUrl)
        }
        return sourceUrl
    }

    fun restart(context: Context, root: File) {
        stop()
        start(context, root)
    }

    fun stop() {
        webSource?.stop()
        webSource = null
    }

    private fun shouldReplaceLoadUrl(currentLoadUrl: String): Boolean {
        return currentLoadUrl.isEmpty() ||
            currentLoadUrl.startsWith("http://localhost") ||
            currentLoadUrl.startsWith("https://localhost")
    }
}

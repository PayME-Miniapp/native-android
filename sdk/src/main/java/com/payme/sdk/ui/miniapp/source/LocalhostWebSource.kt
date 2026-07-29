package com.payme.sdk.ui.miniapp.source

import android.content.Context
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.utils.Utils
import com.payme.sdk.webServer.WebServer
import java.io.File

internal class LocalhostWebSource {
    private var server: com.payme.sdk.webServer.MySimpleWebServer? = null
    private var port = MiniAppSourceConstants.DEFAULT_PORT

    fun start(context: Context, wwwRoot: File): String {
        if (server == null) {
            port = Utils.findRandomOpenPort() ?: MiniAppSourceConstants.DEFAULT_PORT
            server = WebServer("localhost", port, wwwRoot)
            (server as WebServer).start()
            Log.d(PayMEMiniApp.TAG, "start server with port $port")
        }
        return "http://localhost:$port/"
    }

    fun stop() {
        server?.let {
            Log.d(PayMEMiniApp.TAG, "Stopped Server")
            it.stop()
        }
        server = null
    }
}

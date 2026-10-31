package com.payme.sdk.utils

import android.util.Log
import com.payme.sdk.PayMEMiniApp
import java.util.concurrent.Executors

/**
 * WebMessageListener callbacks run on the UI thread. Blocking bridge work (contacts query,
 * QR encode + MediaStore write, log sanitising) runs here; results go back through
 * WebViewJsDispatcher.evaluate, which posts to the UI thread.
 */
internal object BridgeExecutor {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "payme-bridge-io").apply { isDaemon = true }
    }

    fun execute(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                Log.d(PayMEMiniApp.TAG, "bridge task failed: ${e.javaClass.simpleName}")
            }
        }
    }
}

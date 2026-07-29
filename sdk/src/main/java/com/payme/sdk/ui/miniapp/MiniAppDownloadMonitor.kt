package com.payme.sdk.ui.miniapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.Locale
import com.payme.sdk.ui.miniapp.source.DownloadProgressTracker
import com.payme.sdk.ui.miniapp.source.DownloadSpeedState
import com.payme.sdk.utils.Formatters
import com.payme.sdk.utils.LocaleUtils

internal class MiniAppDownloadMonitor(
    private val isDownloadInProgressProvider: () -> Boolean,
    private val currentLocaleProvider: () -> Locale,
    private val onDownloadIssue: (String) -> Unit,
    private val onConnectionStatusChanged: () -> Unit
) {
    private val downloadProgressTracker = DownloadProgressTracker()
    private var downloadSpeedHandler: Handler? = null
    private var timeoutCheckRunnable: Runnable? = null

    val isSlowOrInterrupted: Boolean
        get() = downloadProgressTracker.isSlowOrInterrupted

    fun start() {
        downloadProgressTracker.start()
        stop()
        downloadSpeedHandler = Handler(Looper.getMainLooper())
        timeoutCheckRunnable = Runnable { checkTimeout() }
        timeoutCheckRunnable?.let {
            downloadSpeedHandler?.postDelayed(it, TIMEOUT_CHECK_INTERVAL_MS)
        }
    }

    fun stop() {
        timeoutCheckRunnable?.let { downloadSpeedHandler?.removeCallbacks(it) }
        downloadSpeedHandler = null
        timeoutCheckRunnable = null
    }

    fun checkSpeed(speed: Long) {
        when (val speedState = downloadProgressTracker.trackSpeed(speed, isDownloadInProgressProvider())) {
            DownloadSpeedState.SlowDetected -> {
                val formattedSpeed = Formatters.formatSpeed(speed)
                val slowSpeedMessage = when (currentLocaleProvider()) {
                    Locale.en -> "Slow download speed detected: ${formattedSpeed}/s"
                    Locale.vi -> "Phát hiện tốc độ tải xuống chậm: ${formattedSpeed}/s"
                }
                Log.w(PayMEMiniApp.TAG, slowSpeedMessage)
                onConnectionStatusChanged()
            }

            is DownloadSpeedState.TooSlow -> {
                val formattedSpeed = Formatters.formatSpeed(speed)
                Log.e(
                    PayMEMiniApp.TAG,
                    "Download speed too slow (${formattedSpeed}/s) for ${speedState.durationMs / 1000}s - aborting download"
                )
                onDownloadIssue(LocaleUtils.ErrorMessages.downloadFailed("Tốc độ quá chậm"))
            }

            DownloadSpeedState.Recovered -> onConnectionStatusChanged()
            DownloadSpeedState.Normal -> Unit
        }
    }

    private fun checkTimeout() {
        val elapsedTime = downloadProgressTracker.elapsedMs()
        val remainingTime = downloadProgressTracker.remainingMs()

        if (!isDownloadInProgressProvider()) {
            return
        }

        val elapsedSeconds = elapsedTime / 1000
        val remainingSeconds = remainingTime / 1000
        val timeRemainingMessage = LocaleUtils.DownloadMessages.downloadTimeRemaining(remainingSeconds)

        Log.d(PayMEMiniApp.TAG, "$timeRemainingMessage (${elapsedSeconds}s đã trôi qua)")
        if (remainingTime in 1..29999) {
            Log.w(PayMEMiniApp.TAG, timeRemainingMessage)
        }

        if (downloadProgressTracker.isTimedOut()) {
            val timeoutMessage = when (currentLocaleProvider()) {
                Locale.en -> "Download timeout after ${elapsedTime / 1000}s - Cancelling download"
                Locale.vi -> "Tải xuống quá thời gian sau ${elapsedTime / 1000}s - Hủy tải xuống"
            }
            Log.e(PayMEMiniApp.TAG, timeoutMessage)
            onDownloadIssue(LocaleUtils.ErrorMessages.downloadTimeout())
        } else {
            timeoutCheckRunnable?.let {
                downloadSpeedHandler?.postDelayed(it, TIMEOUT_CHECK_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TIMEOUT_CHECK_INTERVAL_MS = 10_000L
    }
}

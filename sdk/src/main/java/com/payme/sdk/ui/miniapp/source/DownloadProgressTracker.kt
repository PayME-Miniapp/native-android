package com.payme.sdk.ui.miniapp.source

internal sealed class DownloadSpeedState {
    data object Normal : DownloadSpeedState()
    data object SlowDetected : DownloadSpeedState()
    data class TooSlow(val durationMs: Long) : DownloadSpeedState()
    data object Recovered : DownloadSpeedState()
}

internal class DownloadProgressTracker(
    private val slowSpeedThreshold: Long = DEFAULT_SLOW_SPEED_THRESHOLD,
    private val slowSpeedDuration: Long = DEFAULT_SLOW_SPEED_DURATION,
    private val downloadTimeout: Long = DEFAULT_DOWNLOAD_TIMEOUT,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() }
) {
    private var slowSpeedStartTime: Long = NO_SLOW_SPEED
    private var downloadStartTime: Long = 0

    val isSlowOrInterrupted: Boolean
        get() = slowSpeedStartTime != NO_SLOW_SPEED

    fun start() {
        slowSpeedStartTime = NO_SLOW_SPEED
        downloadStartTime = currentTimeMs()
    }

    fun elapsedMs(): Long = currentTimeMs() - downloadStartTime

    fun remainingMs(): Long = downloadTimeout - elapsedMs()

    fun isTimedOut(): Boolean = elapsedMs() > downloadTimeout

    fun trackSpeed(speed: Long, isDownloadInProgress: Boolean): DownloadSpeedState {
        if (speed < slowSpeedThreshold && isDownloadInProgress) {
            val now = currentTimeMs()
            if (slowSpeedStartTime == NO_SLOW_SPEED) {
                slowSpeedStartTime = now
                return DownloadSpeedState.SlowDetected
            }
            val slowDuration = now - slowSpeedStartTime
            if (slowDuration > slowSpeedDuration) {
                return DownloadSpeedState.TooSlow(slowDuration)
            }
            return DownloadSpeedState.Normal
        }

        if (slowSpeedStartTime != NO_SLOW_SPEED) {
            slowSpeedStartTime = NO_SLOW_SPEED
            return DownloadSpeedState.Recovered
        }
        return DownloadSpeedState.Normal
    }

    private companion object {
        const val DEFAULT_SLOW_SPEED_THRESHOLD = 1024L
        const val DEFAULT_SLOW_SPEED_DURATION = 5000L
        const val DEFAULT_DOWNLOAD_TIMEOUT = 60 * 1000L
        const val NO_SLOW_SPEED = -1L
    }
}

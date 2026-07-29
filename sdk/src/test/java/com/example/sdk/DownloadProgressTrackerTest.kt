package com.example.sdk

import com.payme.sdk.ui.miniapp.source.DownloadProgressTracker
import com.payme.sdk.ui.miniapp.source.DownloadSpeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressTrackerTest {
    @Test
    fun downloadProgressTrackerReportsSlowAndRecoveredStates() {
        var now = 0L
        val tracker = DownloadProgressTracker(
            slowSpeedThreshold = 100,
            slowSpeedDuration = 1_000,
            downloadTimeout = 5_000,
            currentTimeMs = { now }
        )

        tracker.start()
        assertEquals(DownloadSpeedState.SlowDetected, tracker.trackSpeed(50, true))

        now = 1_500
        val tooSlow = tracker.trackSpeed(50, true)
        assertTrue(tooSlow is DownloadSpeedState.TooSlow)

        assertEquals(DownloadSpeedState.Recovered, tracker.trackSpeed(500, true))
    }

    @Test
    fun downloadProgressTrackerReportsTimeout() {
        var now = 0L
        val tracker = DownloadProgressTracker(
            slowSpeedThreshold = 100,
            slowSpeedDuration = 1_000,
            downloadTimeout = 5_000,
            currentTimeMs = { now }
        )

        tracker.start()
        now = 5_001

        assertTrue(tracker.isTimedOut())
        assertEquals(5_001, tracker.elapsedMs())
        assertEquals(-1, tracker.remainingMs())
    }
}

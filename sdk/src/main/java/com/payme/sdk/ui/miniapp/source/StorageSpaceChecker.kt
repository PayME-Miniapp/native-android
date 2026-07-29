package com.payme.sdk.ui.miniapp.source

import android.content.Context
import android.os.StatFs
import kotlin.math.max

internal class StorageSpaceChecker internal constructor(
    private val availableBytesProvider: () -> Long
) {
    constructor(context: Context) : this({
        StatFs(context.filesDir.absolutePath).availableBytes
    })

    fun availableBytes(): Long {
        return availableBytesProvider().coerceAtLeast(0L)
    }

    fun hasEnoughSpace(knownSize: Long): Boolean {
        return availableBytes() >= requiredBytesFor(knownSize)
    }

    fun requiredBytesFor(knownSize: Long): Long {
        val safeSize = knownSize.coerceAtLeast(0L)
        val multiplied = if (safeSize > Long.MAX_VALUE / REQUIRED_SPACE_MULTIPLIER) {
            Long.MAX_VALUE
        } else {
            safeSize * REQUIRED_SPACE_MULTIPLIER
        }
        return max(multiplied, MIN_REQUIRED_BYTES)
    }

    companion object {
        const val REQUIRED_SPACE_MULTIPLIER = 3L
        const val MIN_REQUIRED_BYTES = 100L * 1024L * 1024L

        fun unbounded(): StorageSpaceChecker {
            return StorageSpaceChecker { Long.MAX_VALUE }
        }
    }
}

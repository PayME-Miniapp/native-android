package com.payme.sdk.utils

internal object Formatters {
    fun formatStringToValidJsonString(dataRaw: String): String {
        var formatted = dataRaw
        formatted = formatted.replace("\\r", "")
        formatted = formatted.replace("\\n", "")
        formatted = formatted.replace("\\\\\"".toRegex(), "\"")
        @Suppress("RegExpRedundantEscape") run {
            formatted = formatted.replace("\\\\\\\"".toRegex(), "\"")
        }
        formatted = formatted.replace("\\\\", "\\")
        return formatted.substring(1, formatted.length - 1)
    }

    fun formatFileSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L

        return when {
            size >= gb -> String.format("%.1fGB", size.toDouble() / gb)
            size >= mb -> String.format("%.1fMB", size.toDouble() / mb)
            size >= kb -> String.format("%.1fKB", size.toDouble() / kb)
            else -> "${size}B"
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        val speedInBytes = bytesPerSecond
        val speedInKB = speedInBytes.toFloat() / 1024.0f
        val speedInMB = speedInKB / 1024.0f
        val speedInGB = speedInMB / 1024.0f

        return when {
            speedInGB >= 1.0f -> String.format("%.1fGB", speedInGB)
            speedInMB >= 1.0f || speedInKB >= 1000.0f -> String.format("%.1fMB", speedInMB)
            speedInKB >= 1.0f || speedInBytes >= 1000 -> String.format("%.1fKB", speedInKB)
            speedInBytes > 0 -> String.format("%.0fB", speedInBytes.toFloat())
            else -> "0B"
        }
    }
}

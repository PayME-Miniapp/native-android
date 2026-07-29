package com.payme.sdk.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.Locale

internal object DeviceSecurityUtils {
    fun isRooted(context: Context?): Boolean {
        val hasTestKeys = Build.TAGS?.contains("test-keys") == true
        val debuggableBuild = try {
            Runtime.getRuntime().exec("getprop ro.debuggable").inputStream.bufferedReader()
                .use { it.readLine()?.trim() == "1" }
        } catch (t: Throwable) {
            false
        }
        return hasTestKeys ||
            hasSuBinary() ||
            canExecuteSu() ||
            debuggableBuild ||
            hasDangerousPackages(context) ||
            hasWritableSystemDir()
    }

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.lowercase(Locale.ROOT).contains("droid4x") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("vbox86") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator") ||
            Build.BOARD.lowercase(Locale.ROOT).contains("nox") ||
            Build.BOOTLOADER.lowercase(Locale.ROOT).contains("nox") ||
            Build.HARDWARE.lowercase(Locale.ROOT).contains("nox") ||
            Build.PRODUCT.lowercase(Locale.ROOT).contains("nox") ||
            serialContainsNox() ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    }

    private fun hasSuBinary(): Boolean {
        return SU_PATHS.any { File(it).exists() }
    }

    private fun canExecuteSu(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            process.inputStream.bufferedReader().use { reader ->
                reader.readLine() != null
            }
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    private fun hasDangerousPackages(context: Context?): Boolean {
        if (context == null) return false
        return DANGEROUS_PACKAGES.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun hasWritableSystemDir(): Boolean {
        return SYSTEM_PATHS.any { path ->
            try {
                val file = File(path)
                file.exists() && file.canWrite()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun serialContainsNox(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                @Suppress("MissingPermission")
                Build.getSerial().lowercase(Locale.ROOT).contains("nox")
            } catch (e: SecurityException) {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL.lowercase(Locale.ROOT).contains("nox")
        }
    }

    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/su/bin/su"
    )

    private val DANGEROUS_PACKAGES = arrayOf(
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot"
    )

    private val SYSTEM_PATHS = arrayOf(
        "/system",
        "/system/bin",
        "/system/sbin",
        "/system/xbin",
        "/vendor/bin",
        "/sbin",
        "/etc"
    )
}

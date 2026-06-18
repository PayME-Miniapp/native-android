package com.payme.sdk.ui.miniapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import com.payme.sdk.BuildConfig
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.utils.DeviceTypeResolver
import com.payme.sdk.utils.Utils
import org.json.JSONObject

internal object MiniAppDeviceInfoBuilder {
    @SuppressLint("HardwareIds")
    fun build(
        context: Context,
        activity: Activity?,
        rootView: View,
        openType: OpenMiniAppType
    ): JSONObject {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val insets = JSONObject()
        val statusHeight = activity?.let { Utils.getStatusBarHeight(it) }
        if (openType == OpenMiniAppType.screen) {
            insets.put("top", statusHeight?.let { Utils.pxToDp(context, it) })
        }
        val bottom = Utils.getRootWindowInsetsCompat(rootView) ?: 0
        insets.put("bottom", Utils.pxToDp(context, bottom.toInt()))

        val deviceInfo = JSONObject()
        deviceInfo.put("platform", "android")
        deviceInfo.put(
            "deviceId",
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        )
        deviceInfo.put("userAgent", Utils.getUserAgent(context))
        deviceInfo.put("version", packageInfo.versionName)
        deviceInfo.put("buildNumber", buildNumber)
        deviceInfo.put("isEmulator", Utils.isEmulator())
        deviceInfo.put("isRoot", Utils.isDeviceRooted(context))
        deviceInfo.put("brand", Build.BRAND)
        deviceInfo.put("model", Build.MODEL)
        deviceInfo.put("bundleId", context.packageName)
        deviceInfo.put("systemName", "Android")
        deviceInfo.put("systemVersion", Build.VERSION.RELEASE)
        deviceInfo.put("deviceType", DeviceTypeResolver(context).deviceType.value)
        deviceInfo.put("insets", insets)
        deviceInfo.put("miniAppVersion", BuildConfig.SDK_VERSION)

        val biometric = JSONObject()
        biometric.put("isSupport", Utils.isBiometricReady(context))
        biometric.put("type", "UNKNOWN")
        deviceInfo.put("biometric", biometric)

        return deviceInfo
    }
}

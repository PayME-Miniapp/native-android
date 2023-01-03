package com.payme.sdk.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager


enum class DeviceType(val value: String) {
    HANDSET("Handset"), TABLET("Tablet"), TV("Tv"), UNKNOWN("unknown");
}

class DeviceTypeResolver(context: Context) {
    private val context: Context
    val isTablet: Boolean
        get() = deviceType === DeviceType.TABLET

    val deviceType: DeviceType
        get() {
            if (context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")) {
                return DeviceType.TV
            }
            val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                return DeviceType.TV
            }
            val deviceTypeFromConfig: DeviceType = deviceTypeFromResourceConfiguration
            return if (deviceTypeFromConfig !== DeviceType.UNKNOWN) {
                deviceTypeFromConfig
            } else deviceTypeFromPhysicalSize
        }

    private val deviceTypeFromResourceConfiguration: DeviceType
        get() {
            val smallestScreenWidthDp: Int =
                context.resources.configuration.smallestScreenWidthDp
            if (smallestScreenWidthDp == Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
                return DeviceType.UNKNOWN
            }
            return if (smallestScreenWidthDp >= 600) DeviceType.TABLET else DeviceType.HANDSET
        }

    private val deviceTypeFromPhysicalSize: DeviceType
        private get() {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManager.defaultDisplay.getRealMetrics(metrics)
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }

            val widthInches = metrics.widthPixels / metrics.xdpi.toDouble()
            val heightInches = metrics.heightPixels / metrics.ydpi.toDouble()
            val diagonalSizeInches =
                Math.sqrt(Math.pow(widthInches, 2.0) + Math.pow(heightInches, 2.0))
            return if (diagonalSizeInches in 3.0..6.9) {
                DeviceType.HANDSET
            } else if (diagonalSizeInches > 6.9 && diagonalSizeInches <= 18.0) {
                DeviceType.TABLET
            } else {
                DeviceType.UNKNOWN
            }
        }

    init {
        this.context = context
    }
}
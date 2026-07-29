package com.payme.sdk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import kotlin.math.min

internal object WindowMetricsUtils {
    fun getStatusBarHeight(activity: Activity): Int {
        val rectangle = Rect()
        val window: Window = activity.window
        window.decorView.getWindowVisibleDisplayFrame(rectangle)
        return rectangle.top
    }

    @SuppressLint("DiscouragedApi")
    fun getSoftNavigationHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context is Activity) {
            context.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
                ?.bottom ?: 0
        } else {
            val resources: Resources = context.resources
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun pxToDp(context: Context, px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }

    fun getUserAgent(context: Context): String? {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: RuntimeException) {
            System.getProperty("http.agent")
        }
    }

    fun getRootWindowInsetsCompat(rootView: View): Float? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getRootWindowInsetsCompatR(rootView)
            else -> getRootWindowInsetsCompatBase(rootView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getRootWindowInsetsCompatR(rootView: View): Float? {
        val insets = rootView.rootWindowInsets?.getInsets(
            WindowInsets.Type.statusBars() or
                WindowInsets.Type.displayCutout() or
                WindowInsets.Type.navigationBars()
        ) ?: return null
        return insets.bottom.toFloat()
    }

    @Suppress("DEPRECATION")
    private fun getRootWindowInsetsCompatM(rootView: View): Float? {
        val insets = rootView.rootWindowInsets ?: return null
        return min(insets.systemWindowInsetBottom, insets.stableInsetBottom).toFloat()
    }

    private fun getRootWindowInsetsCompatBase(rootView: View): Float {
        val visibleRect = Rect()
        rootView.getWindowVisibleDisplayFrame(visibleRect)
        return (rootView.height - visibleRect.bottom).toFloat()
    }
}

package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.payme.sdk.BuildConfig
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.utils.Formatters

internal class MiniAppUpdateUiRenderer(
    private val contextProvider: () -> Context?,
    private val activityProvider: () -> Activity?,
    private val viewsProvider: () -> MiniAppViews
) {
    fun setLoadingVisible() {
        activityProvider()?.runOnUiThread {
            viewsProvider().loadingView.visibility = View.VISIBLE
        }
    }

    fun setUpdatingUiVisible(visible: Boolean) {
        activityProvider()?.runOnUiThread {
            val views = viewsProvider()
            if (visible) {
                views.loadingView.visibility = View.GONE
                views.updatingView.visibility = View.VISIBLE
            } else {
                views.updatingView.visibility = View.GONE
            }
        }
    }

    fun setUpdateLabel(patch: Int) {
        val context = contextProvider() ?: return
        activityProvider()?.runOnUiThread {
            viewsProvider().updateLabelText.text = context.getString(
                R.string.loading_data,
                BuildConfig.SDK_VERSION,
                patch
            )
        }
    }

    fun hideDownloadErrorAndUpdateConnection(
        error: Exception?,
        isSlowOrInterrupted: Boolean,
        isConnected: Boolean,
        connectionType: String
    ) {
        activityProvider()?.runOnUiThread {
            viewsProvider().errorContainer.visibility = View.GONE
            updateConnectionStatus(error, isSlowOrInterrupted, isConnected, connectionType)
        }
    }

    fun updateConnectionStatus(
        error: Exception?,
        isSlowOrInterrupted: Boolean,
        isConnected: Boolean,
        connectionType: String
    ) {
        val context = contextProvider() ?: return
        val isWarning = error != null || isSlowOrInterrupted || !isConnected
        val colorResId = if (isWarning) R.color.warning else R.color.grey_text
        val iconResId = iconForConnectionType(connectionType)
        val connectionText = MiniAppUpdateErrorMapper.connectionText(
            error = error,
            isSlowOrInterrupted = isSlowOrInterrupted,
            isConnected = isConnected,
            connectionType = connectionType
        )

        val views = viewsProvider()
        views.connectionTypeIcon.setImageResource(iconResId)
        views.connectionTypeIcon.setColorFilter(ContextCompat.getColor(context, colorResId))
        views.connectionTypeText.text = connectionText
        views.connectionTypeText.setTextColor(ContextCompat.getColor(context, colorResId))
        views.connectionStatusContainer.visibility = View.VISIBLE
    }

    fun updateDownloadProgress(
        totalBytesCopied: Long,
        length: Long,
        speed: Long,
        progressValuePercent: Int
    ) {
        val context = contextProvider() ?: return
        activityProvider()?.runOnUiThread {
            val views = viewsProvider()
            views.progressBar.progress = progressValuePercent

            val downloadedSizeFormatted = Formatters.formatFileSize(totalBytesCopied)
            val totalSizeFormatted = if (length > 0) {
                Formatters.formatFileSize(length)
            } else {
                context.getString(R.string.download_unknown_size)
            }
            val speedFormatted = Formatters.formatSpeed(speed)
            val percentFormatted = context.getString(
                R.string.download_decimal_percent,
                progressValuePercent.toFloat()
            )
            views.downloadDetailsText.text = context.getString(
                R.string.download_progress_detail,
                downloadedSizeFormatted,
                totalSizeFormatted,
                speedFormatted,
                percentFormatted
            )

            val layoutParams = views.lottieView.layoutParams as LinearLayout.LayoutParams
            layoutParams.leftMargin =
                progressValuePercent * (views.lottieContainerView.width - views.lottieView.width) / 100
            layoutParams.topMargin = 0
            layoutParams.rightMargin = 0
            layoutParams.bottomMargin = 0
            views.lottieView.requestLayout()
        }
    }

    fun showDownloadError(
        errorMessage: String,
        logPrefix: String,
        hasRetryContext: Boolean,
        canRetry: () -> Boolean,
        onRetry: () -> Unit
    ) {
        val context = contextProvider() ?: return
        val views = viewsProvider()
        views.errorContainer.visibility = View.VISIBLE
        views.errorMessage.text = context.getString(R.string.download_failed_with_reason, errorMessage)

        android.util.Log.d(PayMEMiniApp.TAG, logPrefix)

        views.retryButton.text = context.getString(R.string.retry)
        views.retryButton.setOnClickListener {
            views.errorMessage.text = context.getString(R.string.wait)
            if (!hasRetryContext) {
                android.util.Log.e(PayMEMiniApp.TAG, "Cannot retry - Missing parameters")
                views.errorMessage.text = context.getString(
                    R.string.download_failed_wait,
                    context.getString(R.string.wait)
                )
            } else if (canRetry()) {
                views.errorContainer.visibility = View.GONE
                onRetry()
            } else {
                views.errorMessage.text = context.getString(
                    R.string.download_failed_with_reason,
                    context.getString(R.string.no_network_connection)
                )
            }
        }
    }

    private fun iconForConnectionType(connectionType: String): Int {
        return when (connectionType) {
            CONNECTION_WIFI -> R.drawable.ic_wifi
            CONNECTION_MOBILE -> R.drawable.ic_network_cell
            CONNECTION_ETHERNET -> R.drawable.ic_network
            else -> R.drawable.ic_network_unknown
        }
    }

    private companion object {
        const val CONNECTION_WIFI = "WiFi"
        const val CONNECTION_MOBILE = "Mobile Data"
        const val CONNECTION_ETHERNET = "Ethernet"
    }
}

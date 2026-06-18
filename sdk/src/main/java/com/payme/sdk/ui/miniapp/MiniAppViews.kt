package com.payme.sdk.ui.miniapp

import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.airbnb.lottie.LottieAnimationView
import com.payme.sdk.R

internal data class MiniAppViews(
    val rootView: View,
    val webView: WebView,
    val updatingView: CardView,
    val progressBar: ProgressBar,
    val updateLabelText: TextView,
    val downloadDetailsText: TextView,
    val connectionTypeIcon: ImageView,
    val connectionTypeText: TextView,
    val connectionStatusContainer: LinearLayout,
    val lottieView: LottieAnimationView,
    val lottieContainerView: LinearLayout,
    val loadingView: View,
    val errorContainer: LinearLayout,
    val errorMessage: TextView,
    val retryButton: Button
) {
    companion object {
        fun bind(view: View): MiniAppViews {
            return MiniAppViews(
                rootView = view.findViewById(R.id.root_view),
                webView = view.findViewById(R.id.webview),
                updatingView = view.findViewById(R.id.updating_view),
                progressBar = view.findViewById(R.id.progress),
                updateLabelText = view.findViewById(R.id.update_label_text),
                downloadDetailsText = view.findViewById(R.id.download_details_text),
                connectionTypeIcon = view.findViewById(R.id.connection_type_icon),
                connectionTypeText = view.findViewById(R.id.connection_type),
                connectionStatusContainer = view.findViewById(R.id.connection_status_container),
                lottieView = view.findViewById(R.id.lottieView),
                lottieContainerView = view.findViewById(R.id.lottie_container_view),
                loadingView = view.findViewById(R.id.loading),
                errorContainer = view.findViewById(R.id.error_container),
                errorMessage = view.findViewById(R.id.error_message),
                retryButton = view.findViewById(R.id.retry_button)
            )
        }
    }
}

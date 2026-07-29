package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.webkit.WebView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.payme.sdk.utils.WebViewJsDispatcher
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.viewmodels.SubWebViewViewModel

internal class MiniAppFragmentObserverBinder(
    private val lifecycleOwner: LifecycleOwner,
    private val activityProvider: () -> Activity?,
    private val webViewProvider: () -> WebView?,
    private val updateViewModel: PayMEUpdatePatchViewModel,
    private val notificationViewModel: NotificationViewModel,
    private val subWebViewViewModel: SubWebViewViewModel,
    private val updateController: MiniAppUpdateController
) {
    fun bind() {
        updateViewModel.getDoneUpdate().observe(lifecycleOwner) {
            if (it) {
                updateController.onDoneUpdate(
                    loadDefaultSource = updateViewModel.getLoadDefaultSource().value == true
                ) { url ->
                    webViewProvider()?.loadUrl(url)
                }
            }
        }
        updateViewModel.getShowUpdatingUI().observe(lifecycleOwner) {
            updateController.setUpdatingUiVisible(it)
        }
        updateViewModel.getIsLostConnection().observe(lifecycleOwner) {
            if (!it) {
                updateController.onConnectionRestoredIfForceUpdating()
            }
        }
        notificationViewModel.getNotificationData().observe(lifecycleOwner) {
            if (it.length() != 0) {
                notificationViewModel.setNotificationJSON(it)
                activityProvider()?.let { activity ->
                    WebViewJsDispatcher.evaluate(
                        activity,
                        webViewProvider() ?: return@let,
                        "nativeNotificationOpenedApp",
                        it.toString()
                    )
                }
            }
        }
        subWebViewViewModel.getEvaluateJsData().observe(lifecycleOwner, evaluateJsDataObserver)
    }

    private val evaluateJsDataObserver: Observer<Pair<String, String>> = Observer {
        val webView = webViewProvider()
        if (it.first.isNotEmpty() && webView != null) {
            activityProvider()?.let { activity ->
                WebViewJsDispatcher.evaluate(activity, webView, it.first, it.second)
            }
        }
    }
}

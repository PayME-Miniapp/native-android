package com.payme.sdk.runtime

import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppType

internal class MiniAppSession(
    val id: String,
    val config: PayMEConfig,
    val callbacks: PayMECallbacks,
    val openType: OpenMiniAppType,
    val openMiniAppData: OpenMiniAppDataInterface
) {
    var loadUrl: String = ""
    var webViewUrl: String = ""
    var modalHeight: Int = 0
    var onSetModalHeight: (Int) -> Unit = {}
    var closeAction: (() -> Unit)? = null
    var isOpen: Boolean = true
    var isCloseRequested: Boolean = false
    var isTerminalErrorHandled: Boolean = false

    val action: ActionOpenMiniApp
        get() = openMiniAppData.action

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeAction?.invoke()
    }
}

package com.payme.sdk.runtime

import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.PayMEError
import org.json.JSONObject

internal data class PayMECallbacks(
    var onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit = { _, _ -> },
    var onError: (ActionOpenMiniApp, PayMEError) -> Unit = { _, _ -> },
    var onOneSignalSendTags: ((String) -> Unit)? = null,
    var onOneSignalDeleteTags: ((String) -> Unit)? = null,
    var onChangeEnv: ((String) -> Unit)? = null,
    var onChangeLocale: ((String) -> Unit)? = null
)

package com.payme.sdk.presentation

import android.content.Context
import android.provider.Settings
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.PayMENetworkErrorCode
import com.payme.sdk.network_requests.NetworkRequest
import com.payme.sdk.network_requests.NetworkUtils
import org.json.JSONObject

object AccountPresentation {
    fun getBalance(
        context: Context,
        phone: String,
        onSuccess: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        try {
            val params: MutableMap<String, Any> = mutableMapOf()
            params["phone"] = phone
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            params["clientId"] = deviceId
            val request = NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/account/getAccountInfo",
                "",
                params,
            )
            request.setOnRequest(onError = onError, onSuccess = { jsonObject ->
                val data = jsonObject.optJSONObject("data")
                val accessToken = data?.optString("accessToken", "") ?: ""
                if (accessToken.isEmpty()) {
                    val json = JSONObject()
                    json.put("linked", false)
                    json.put("message", jsonObject.optString("message") ?: "Có lỗi xảy ra")
                    onSuccess(ActionOpenMiniApp.GET_BALANCE, json)
                } else {
                    val paramsBalance: MutableMap<String, Any> = mutableMapOf()
                    val request = NetworkRequest(
                        context,
                        NetworkUtils.getApiUrl(PayMEMiniApp.env),
                        "/be/ewallet/sdk/account/balance",
                        accessToken,
                        paramsBalance,
                    )
                    request.setOnRequest(onError = onError, onSuccess = {
                        val data = it.optJSONObject("data")
                        val balance = data?.optInt("balance")
                        if (balance == null) {
                            val json = JSONObject()
                            json.put("linked", true)
                            json.put("message", it.optString("message") ?: "Có lỗi xảy ra")
                            onSuccess(ActionOpenMiniApp.GET_BALANCE, json)
                        } else {
                            val json = JSONObject()
                            json.put("linked", true)
                            json.put("balance", balance)
                            json.put("message", it.optString("message") ?: "Có lỗi xảy ra")
                            onSuccess(ActionOpenMiniApp.GET_BALANCE, json)
                        }
                    })
                }
            })
        } catch (e: Exception) {
            onError(
                ActionOpenMiniApp.GET_BALANCE,
                PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.OTHER.toString())
            )
        }
    }
}
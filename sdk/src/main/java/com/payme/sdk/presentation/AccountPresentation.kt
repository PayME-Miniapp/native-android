package com.payme.sdk.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.payme.sdk.BuildConfig
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.PayMENetworkErrorCode
import com.payme.sdk.network_requests.NetworkRequest
import com.payme.sdk.network_requests.NetworkUtils
import org.json.JSONObject

object AccountPresentation {
    @SuppressLint("HardwareIds")
    fun getAccountInfo(
        context: Context,
        phone: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        try {
            val params: MutableMap<String, Any> = mutableMapOf()
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            params["deviceId"] = deviceId
            params["platform"] = "ANDROID_V2"
            params["channel"] = "channel"
            params["version"] = BuildConfig.SDK_VERSION
            params["buildNumber"] = BuildConfig.BUILD_NUMBER.toString()
            val requestClient = NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/clientDevice/register",
                "",
                params,
                ActionOpenMiniApp.GET_ACCOUNT_INFO
            )
            requestClient.setOnRequest(onError = onError, onSuccess = { jsonObject ->
                val code = jsonObject.optInt("code")
                if (code == 204300) {
                    val paramsAccount: MutableMap<String, Any> = mutableMapOf()
                    paramsAccount["phone"] = phone
                    paramsAccount["clientId"] = deviceId
                    val request = NetworkRequest(
                        context,
                        NetworkUtils.getApiUrl(PayMEMiniApp.env),
                        "/be/ewallet/sdk/account/getAccountInfo",
                        "",
                        paramsAccount,
                        ActionOpenMiniApp.GET_ACCOUNT_INFO
                    )
                    request.setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                        val data = jsonObjectAccount.optJSONObject("data")
                        val accessToken = if (data?.isNull("accessToken") == true) null else data?.optString("accessToken", "")
                        if (accessToken.isNullOrEmpty()) {
                            val json = JSONObject()
                            json.put("linked", false)
                            json.put("message", jsonObjectAccount.optString("message") ?: "Có lỗi xảy ra")
                            onResponse(ActionOpenMiniApp.GET_ACCOUNT_INFO, json)
                        } else {
                            val accountInfo = data?.optJSONObject("accountInfo")
                            onResponse(ActionOpenMiniApp.GET_ACCOUNT_INFO, accountInfo)
                        }
                    })
                } else {
                    onError(
                        ActionOpenMiniApp.GET_ACCOUNT_INFO,
                        PayMEError(PayMEErrorType.MiniApp, PayMENetworkErrorCode.OTHER.toString())
                    )
                }
            })
        } catch (e: Exception) {
            onError(
                ActionOpenMiniApp.GET_ACCOUNT_INFO,
                PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.OTHER.toString())
            )
        }
    }
    @SuppressLint("HardwareIds")
    fun getBalance(
        context: Context,
        phone: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        try {
            val params: MutableMap<String, Any> = mutableMapOf()
            params["phone"] = phone
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            params["deviceId"] = deviceId
            params["platform"] = "ANDROID_V2"
            params["channel"] = "channel"
            params["version"] = BuildConfig.SDK_VERSION
            params["buildNumber"] = BuildConfig.BUILD_NUMBER.toString()
            val requestClient = NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/clientDevice/register",
                "",
                params,
                ActionOpenMiniApp.GET_BALANCE
            )
            requestClient.setOnRequest(onError = onError, onSuccess = { jsonObject ->
                val code = jsonObject.optInt("code")
                if (code == 204300) {
                    val paramsAccount: MutableMap<String, Any> = mutableMapOf()
                    paramsAccount["phone"] = phone
                    paramsAccount["clientId"] = deviceId
                    val request = NetworkRequest(
                        context,
                        NetworkUtils.getApiUrl(PayMEMiniApp.env),
                        "/be/ewallet/sdk/account/getAccountInfo",
                        "",
                        paramsAccount,
                        ActionOpenMiniApp.GET_BALANCE
                    )
                    request.setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                        val data = jsonObjectAccount.optJSONObject("data")
                        val accessToken = if (data?.isNull("accessToken") == true) null else data?.optString("accessToken", "")
                        if (accessToken.isNullOrEmpty()) {
                            val json = JSONObject()
                            json.put("linked", false)
                            json.put("message", jsonObject.optString("message") ?: "Có lỗi xảy ra")
                            onResponse(ActionOpenMiniApp.GET_BALANCE, json)
                        } else {
                            val paramsBalance: MutableMap<String, Any> = mutableMapOf()
                            val requestBalance = NetworkRequest(
                                context,
                                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                                "/be/ewallet/sdk/account/balance",
                                accessToken,
                                paramsBalance,
                                ActionOpenMiniApp.GET_BALANCE
                            )
                            requestBalance.setOnRequest(onError = onError, onSuccess = {
                                val dataBalance = it.optJSONObject("data")
                                val balance = dataBalance?.opt("balance") as Number?
                                val jsonObjectResponse = JSONObject()
                                Log.d("PAYMELOG", "balance $balance")
                                if (balance == null) {
                                    jsonObjectResponse.put("linked", true)
                                    jsonObjectResponse.put("message", it.optString("message") ?: "Có lỗi xảy ra")
                                    onResponse(ActionOpenMiniApp.GET_BALANCE, jsonObjectResponse)
                                } else {
                                    jsonObjectResponse.put("linked", true)
                                    jsonObjectResponse.put("balance", balance)
                                    jsonObjectResponse.put("message", it.optString("message") ?: "Có lỗi xảy ra")
                                    onResponse(ActionOpenMiniApp.GET_BALANCE, jsonObjectResponse)
                                }
                            })
                        }
                    })
                } else {
                    onError(
                        ActionOpenMiniApp.GET_BALANCE,
                        PayMEError(PayMEErrorType.MiniApp, PayMENetworkErrorCode.OTHER.toString())
                    )
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
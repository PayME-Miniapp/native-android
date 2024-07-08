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
    private const val PLATFORM = "ANDROID_V2"
    private const val CHANNEL = "channel"

    @SuppressLint("HardwareIds")
    private fun registerDevice(
        context: Context,
        action: ActionOpenMiniApp,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit,
        onSuccess: (String) -> Unit
    ) {
        try {
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val params = mutableMapOf(
                "deviceId" to deviceId,
                "platform" to PLATFORM,
                "channel" to CHANNEL,
                "version" to BuildConfig.SDK_VERSION, // Use VERSION_NAME for app version
                "buildNumber" to BuildConfig.BUILD_NUMBER.toString() // Use VERSION_CODE for build number
            )

            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/clientDevice/register",
                "",
                params,
                action
            ).setOnRequest(onError = onError, onSuccess = { jsonObject ->
                if (jsonObject.optInt("code") == 204300) {
                    onSuccess(deviceId)
                } else {
                    onError(
                        action,
                        PayMEError(
                            PayMEErrorType.Network,
                            PayMENetworkErrorCode.CONNECTION_LOST.toString()
                        )
                    )
                }
            })
        } catch (e: Exception) {
            // Log the exception with more context
            Log.e("AccountRepository", "Error registering device", e)
            onError(
                action,
                PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.CONNECTION_LOST.toString())
            )
        }
    }

    @SuppressLint("HardwareIds")
    fun getAccountInfo(
        context: Context,
        phone: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        registerDevice(context, ActionOpenMiniApp.GET_ACCOUNT_INFO, onError) { deviceId ->
            val paramsAccount = mutableMapOf(
                "phone" to phone,
                "clientId" to deviceId
            )
            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/account/getAccountInfo",
                "",
                paramsAccount,
                ActionOpenMiniApp.GET_ACCOUNT_INFO
            ).setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                val accountInfoObject =
                    jsonObjectAccount.optJSONObject("data")?.optJSONObject("accountInfo")
                val accountId = accountInfoObject?.optInt("accountId", -1) ?: -1
                if (accountId != -1) {
                    onResponse(ActionOpenMiniApp.GET_ACCOUNT_INFO, accountInfoObject)
                } else {
                    onError(
                        ActionOpenMiniApp.GET_ACCOUNT_INFO,
                        PayMEError(
                            PayMEErrorType.MiniApp,
                            PayMENetworkErrorCode.OTHER.toString(),
                            "Không lấy được thông tin tài khoản"
                        )
                    )
                }
            })
        }
    }

    @SuppressLint("HardwareIds")
    fun getBalance(
        context: Context,
        phone: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        registerDevice(context, ActionOpenMiniApp.GET_BALANCE, onError) { deviceId ->
            val paramsAccount = mutableMapOf(
                "phone" to phone,
                "clientId" to deviceId
            )
            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(PayMEMiniApp.env),
                "/be/ewallet/sdk/account/getAccountInfo",
                "",
                paramsAccount,
                ActionOpenMiniApp.GET_BALANCE
            ).setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                val data = jsonObjectAccount.optJSONObject("data")
                val accessToken = data?.optString("accessToken") // Simplify accessToken extraction

                val responseJson = JSONObject()
                if (accessToken.isNullOrEmpty()) {
                    responseJson.put("linked", false)
                    responseJson.put("message", "Tài khoản chưa được liên kết")
                    onResponse(ActionOpenMiniApp.GET_BALANCE, responseJson)
                } else {
                    val paramsBalance = mutableMapOf("clientId" to deviceId)
                    NetworkRequest(
                        context,
                        NetworkUtils.getApiUrl(PayMEMiniApp.env),
                        "/be/ewallet/sdk/account/balance",
                        accessToken,
                        paramsBalance,
                        ActionOpenMiniApp.GET_BALANCE
                    ).setOnRequest(onError = onError, onSuccess = { balanceResponse ->
                        val dataBalance = balanceResponse.optJSONObject("data")
                        val balance = dataBalance?.opt("balance") as? Number
                            ?: 0 // Use elvis operator for default balance
                        responseJson.put("linked", true)
                        responseJson.put("balance", balance)
                        responseJson.put(
                            "message",
                            balanceResponse.optString("message") ?: "Có lỗi xảy ra"
                        )
                        onResponse(ActionOpenMiniApp.GET_BALANCE, responseJson)
                    })
                }
            })
        }
    }
}
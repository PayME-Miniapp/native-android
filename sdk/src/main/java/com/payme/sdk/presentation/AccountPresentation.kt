package com.payme.sdk.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.payme.sdk.BuildConfig
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.PayMENetworkErrorCode
import com.payme.sdk.network_requests.NetworkRequest
import com.payme.sdk.network_requests.NetworkUtils
import com.payme.sdk.runtime.PartnerCallbacks
import com.payme.sdk.runtime.PayMERuntime
import org.json.JSONObject

object AccountPresentation {
    private const val HTTP_STATUS_UNAUTHORIZED = 401

    private const val PLATFORM = "ANDROID_V2"
    private const val CHANNEL = "channel"

    @SuppressLint("HardwareIds")
    private fun registerDevice(
        context: Context,
        identityToken: String,
        action: ActionOpenMiniApp,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit,
        onSuccess: (String) -> Unit
    ) {
        try {
            val config = PayMERuntime.requireConfig()
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val params = registerDeviceParams(
                identityToken,
                deviceId,
                BuildConfig.SDK_VERSION,
                BuildConfig.BUILD_NUMBER.toString()
            )

            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(config.env),
                "/be/ewallet/sdk/clientDevice/register",
                "",
                params,
                action,
                config
            ).setOnRequest(onError = onError, onSuccess = { jsonObject ->
                if (jsonObject.optInt("code") == 204300) {
                    onSuccess(deviceId)
                } else {
                    onError(
                        action,
                        PayMEError(
                            PayMEErrorType.Network,
                            PayMENetworkErrorCode.CONNECTION_LOST.toString(),
                            context.getString(R.string.network_connection_failed)
                        )
                    )
                }
            })
        } catch (e: Exception) {
            // Log the exception with more context
            Log.e("AccountRepository", "Error registering device", e)
            onError(
                action,
                PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.CONNECTION_LOST.toString(), context.getString(R.string.network_connection_failed))
            )
        }
    }

    /** identityToken sai hoặc hết hạn: response vẫn giải mã được nhưng có `errors[0].code = 401` (giống luồng web). */
    internal fun isUnauthorized(response: JSONObject): Boolean =
        response.optJSONArray("errors")?.optJSONObject(0)?.optInt("code") == HTTP_STATUS_UNAUTHORIZED

    /** Cùng mã với lỗi 401 miniapp báo về khi đóng, để app đối tác xử lý một chỗ. */
    private fun unauthorizedError(context: Context) = PayMEError(
        PayMEErrorType.MiniApp,
        HTTP_STATUS_UNAUTHORIZED.toString(),
        context.getString(R.string.session_expired)
    )

    /** clientDevice/register body. identityToken goes with every API the SDK calls itself. Sent encrypted, never logged. */
    internal fun registerDeviceParams(
        identityToken: String,
        deviceId: String,
        version: String,
        buildNumber: String
    ): MutableMap<String, String> = mutableMapOf(
        "deviceId" to deviceId,
        "platform" to PLATFORM,
        "channel" to CHANNEL,
        "version" to version,
        "buildNumber" to buildNumber,
        "identityToken" to identityToken
    )

    /** account/balance body: identityToken goes with the accessToken (money API). Sent encrypted, never logged. */
    internal fun balanceParams(identityToken: String, clientId: String): MutableMap<String, String> =
        mutableMapOf("clientId" to clientId, "identityToken" to identityToken)

    /** getAccountInfo body: identityToken replaces phone (SDK 1.0.24). Sent encrypted, never logged. */
    internal fun accountInfoParams(identityToken: String, clientId: String): MutableMap<String, String> =
        mutableMapOf("identityToken" to identityToken, "clientId" to clientId)

    @SuppressLint("HardwareIds")
    fun getAccountInfo(
        context: Context,
        identityToken: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        // Lỗi trong callback partner chỉ ghi log; lỗi của SDK mới thành onError
        @Suppress("NAME_SHADOWING")
        val onResponse = PartnerCallbacks.guard("onResponse", onResponse)
        @Suppress("NAME_SHADOWING")
        val onError = PartnerCallbacks.guard("onError", onError)
        registerDevice(context, identityToken, ActionOpenMiniApp.GET_ACCOUNT_INFO, onError) { deviceId ->
            val config = PayMERuntime.requireConfig()
            val paramsAccount = accountInfoParams(identityToken, deviceId)
            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(config.env),
                "/be/ewallet/sdk/account/getAccountInfo",
                "",
                paramsAccount,
                ActionOpenMiniApp.GET_ACCOUNT_INFO,
                config
            ).setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                if (isUnauthorized(jsonObjectAccount)) {
                    onError(ActionOpenMiniApp.GET_ACCOUNT_INFO, unauthorizedError(context))
                    return@setOnRequest
                }
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
                            context.getString(R.string.account_info_not_found)
                        )
                    )
                }
            })
        }
    }

    @SuppressLint("HardwareIds")
    fun getBalance(
        context: Context,
        identityToken: String,
        onResponse: (ActionOpenMiniApp, JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        // Lỗi trong callback partner chỉ ghi log; lỗi của SDK mới thành onError
        @Suppress("NAME_SHADOWING")
        val onResponse = PartnerCallbacks.guard("onResponse", onResponse)
        @Suppress("NAME_SHADOWING")
        val onError = PartnerCallbacks.guard("onError", onError)
        registerDevice(context, identityToken, ActionOpenMiniApp.GET_BALANCE, onError) { deviceId ->
            val config = PayMERuntime.requireConfig()
            val paramsAccount = accountInfoParams(identityToken, deviceId)
            NetworkRequest(
                context,
                NetworkUtils.getApiUrl(config.env),
                "/be/ewallet/sdk/account/getAccountInfo",
                "",
                paramsAccount,
                ActionOpenMiniApp.GET_BALANCE,
                config
            ).setOnRequest(onError = onError, onSuccess = { jsonObjectAccount ->
                if (isUnauthorized(jsonObjectAccount)) {
                    onError(ActionOpenMiniApp.GET_BALANCE, unauthorizedError(context))
                    return@setOnRequest
                }
                val data = jsonObjectAccount.optJSONObject("data")
                val accessToken = data?.optString("accessToken") // Simplify accessToken extraction

                val responseJson = JSONObject()
                if (accessToken.isNullOrEmpty()) {
                    responseJson.put("linked", false)
                    responseJson.put("message", context.getString(R.string.account_not_linked))
                    onResponse(ActionOpenMiniApp.GET_BALANCE, responseJson)
                } else {
                    val paramsBalance = balanceParams(identityToken, deviceId)
                    NetworkRequest(
                        context,
                        NetworkUtils.getApiUrl(config.env),
                        "/be/ewallet/sdk/account/balance",
                        accessToken,
                        paramsBalance,
                        ActionOpenMiniApp.GET_BALANCE,
                        config
                    ).setOnRequest(onError = onError, onSuccess = { balanceResponse ->
                        val dataBalance = balanceResponse.optJSONObject("data")
                        val balance = dataBalance?.opt("balance") as? Number
                            ?: 0 // Use elvis operator for default balance
                        responseJson.put("linked", true)
                        responseJson.put("balance", balance)
                        responseJson.put(
                            "message",
                            balanceResponse.optString("message") ?: context.getString(R.string.error_occurred)
                        )
                        onResponse(ActionOpenMiniApp.GET_BALANCE, responseJson)
                    })
                }
            })
        }
    }

}

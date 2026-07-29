package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.util.Log
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.Locale
import org.json.JSONException
import org.json.JSONObject
import vn.kalapa.ekyc.KalapaHandler
import vn.kalapa.ekyc.KalapaSDK
import vn.kalapa.ekyc.KalapaSDKConfig
import vn.kalapa.ekyc.KalapaSDKResultCode
import vn.kalapa.ekyc.KalapaScanNFCCallback
import vn.kalapa.ekyc.KalapaScanNFCError
import vn.kalapa.ekyc.models.KalapaResult

internal class KalapaKycGateway(
    private val fragment: Fragment,
    private val localeProvider: () -> Locale,
    private val evaluateJs: (Activity, String, String) -> Unit
) {
    fun startEkyc(data: JSONObject) {
        start(data, requireQrCode = true)
    }

    fun startNfc(data: JSONObject) {
        start(data, requireQrCode = true)
    }

    private fun start(data: JSONObject, requireQrCode: Boolean) {
        val sessionId = data.optString("token", "")
        if (sessionId.isEmpty()) {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: sessionId null")
            return
        }
        val sdkConfigBuilder = KalapaSDKConfig.KalapaSDKConfigBuilder(fragment.requireContext() as Activity)
            .withBackgroundColor("#FFFFFF")
            .withMainColor("#33CB33")
            .withLivenessVersion(0)
            .withNFCTimeoutInSeconds(180)
            .withLanguage(localeProvider().toString())
            .withSpecificLanguageForCustomer("payme")
        if (requireQrCode) {
            sdkConfigBuilder.requireQRCode(true)
        }
        val sdkConfig = sdkConfigBuilder.build()
        KalapaSDK.KalapaSDKBuilder(fragment.requireActivity(), sdkConfig).build()
            .start(sessionId, "nfc_only", createKalapaHandler(data))
    }

    private fun createKalapaHandler(data: JSONObject): KalapaHandler {
        return object : KalapaHandler() {
            override fun onComplete(kalapaResult: KalapaResult) {
                Log.d(PayMEMiniApp.TAG, "Kalapa NFC complete")
                evaluate("nativeKalapaNFC", buildKalapaNfcResponse(data).toString())
            }

            override fun onNFCErrorHandle(
                activity: Activity,
                error: KalapaScanNFCError,
                callback: KalapaScanNFCCallback
            ) {
                Log.d(PayMEMiniApp.TAG, """NFC error handle: $error""")
                val response = buildKalapaNfcResponse(data)
                when (error) {
                    KalapaScanNFCError.ERROR_NFC_TIMEOUT -> {
                        response.put("isTimeout", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    KalapaScanNFCError.ERROR_FACE_NOT_MATCH -> {
                        response.put("isFaceNotMatch", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    KalapaScanNFCError.ERROR_NFC_INFO_NOT_MATCH -> {
                        response.put("isInfoNotMatch", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    else -> Unit
                }
            }

            override fun onError(resultCode: KalapaSDKResultCode) {
                Log.d(PayMEMiniApp.TAG, """startNFC error: $resultCode""")
            }

            override fun onExpired() {
                // This handler is called when current session expires and user taps Retry in Kalapa UI.
            }
        }
    }

    private fun buildKalapaNfcResponse(data: JSONObject): JSONObject {
        val action = data.optString("action", "")
        val payload = data.optString("payload", "")
        val response = JSONObject()
        if (action.isNotEmpty()) {
            response.put("action", action)
            if (payload.isNotEmpty() && action != "KLP_KYC") {
                try {
                    response.put("payload", JSONObject(payload))
                } catch (e: JSONException) {
                    Log.e(PayMEMiniApp.TAG, "Failed to parse Kalapa payload as JSON", e)
                }
            }
        } else {
            response.put("action", "KLP_KYC")
        }
        return response
    }

    private fun evaluate(functionName: String, payload: String) {
        val activity = fragment.activity ?: return
        evaluateJs(activity, functionName, payload)
    }
}

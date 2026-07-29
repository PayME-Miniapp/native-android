package com.payme.sdk.utils

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.payme.sdk.PayMEMiniApp
import org.json.JSONException
import org.json.JSONObject

internal object BiometricGateway {
    fun isReady(context: Context): Boolean {
        return hasBiometricCapability(context) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: AppCompatActivity, webView: WebView, data: String) {
        Log.d(PayMEMiniApp.TAG, "vo hàm bio")
        activity.runOnUiThread {
            try {
                val jsonData = JSONObject(data)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setDescription(jsonData.optString("description", "Dùng sinh trắc học để xác thực"))
                    .setNegativeButtonText(jsonData.optString("cancelTitle", "Hủy"))
                    .setConfirmationRequired(true)
                    .setTitle(jsonData.optString("title", "Yêu cầu xác thực"))
                    .build()

                val biometricPrompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    authenticationCallback(activity, webView)
                )
                biometricPrompt.authenticate(promptInfo)
            } catch (e: JSONException) {
                Log.d(PayMEMiniApp.TAG, "vo catch ${e.message}")
                sendBiometricResult(activity, webView, success = false, error = BiometricError.UNKNOWN)
            }
        }
    }

    fun errorCode(errorCode: Int): BiometricError {
        return when (errorCode) {
            BiometricPrompt.ERROR_CANCELED -> BiometricError.SYSTEM_CANCEL
            BiometricPrompt.ERROR_LOCKOUT -> BiometricError.BIOMETRY_LOCKOUT
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricError.BIOMETRY_LOCKOUT
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricError.USER_CANCEL
            BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricError.BIOMETRY_NOT_ENROLLED
            BiometricPrompt.ERROR_TIMEOUT -> BiometricError.USER_CANCEL
            else -> BiometricError.UNKNOWN
        }
    }

    private fun hasBiometricCapability(
        context: Context,
        authenticator: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK
    ): Int {
        return BiometricManager.from(context).canAuthenticate(authenticator)
    }

    private fun authenticationCallback(
        activity: AppCompatActivity,
        webView: WebView
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(PayMEMiniApp.TAG, "errorcode $errorCode")
                sendBiometricResult(activity, webView, success = false, error = BiometricGateway.errorCode(errorCode))
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                sendBiometricResult(activity, webView, success = true)
            }
        }
    }

    private fun sendBiometricResult(
        activity: AppCompatActivity,
        webView: WebView,
        success: Boolean,
        error: BiometricError? = null
    ) {
        val resultAuthen = JSONObject()
        resultAuthen.put("success", success)
        error?.let { resultAuthen.put("error", it) }
        WebViewJsDispatcher.evaluate(
            activity,
            webView,
            "nativeBiometricAuthentication",
            resultAuthen.toString(),
            null
        )
    }
}

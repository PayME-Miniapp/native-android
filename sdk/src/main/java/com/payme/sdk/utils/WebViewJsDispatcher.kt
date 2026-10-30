package com.payme.sdk.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.payme.sdk.PayMEMiniApp
import org.json.JSONException
import org.json.JSONObject

internal object WebViewJsDispatcher {
    private val origins = java.util.WeakHashMap<WebView, String>()
    fun trust(webView: WebView, origin: String) { origins[webView] = origin }

    fun evaluate(
        activity: Activity,
        webView: WebView,
        functionName: String,
        data: String,
        callback: ((String) -> Unit)? = null
    ) {
        val injectedJS = buildScript(functionName, data) ?: return
        activity.runOnUiThread {
            val origin = origins[webView]
            if (origin != null && !com.payme.sdk.ui.miniapp.OriginPolicy.isLocal(webView.url.orEmpty(), origin)) return@runOnUiThread
            webView.evaluateJavascript(injectedJS, callback)
            Log.d(PayMEMiniApp.TAG, "[EVALUATE_JS] $functionName")
        }
    }

    internal fun buildScript(functionName: String, data: String): String? {
        if (!Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(functionName)) return null
        return try {
            val gson = com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create()
            val json = gson.fromJson(data, com.google.gson.JsonElement::class.java)
            "window[${gson.toJson(functionName)}](${gson.toJson(json)});true;"
        } catch (_: Exception) {
            null
        }
    }

    fun sendNativePreferences(context: Context, webView: WebView) {
        val sharedPreference = context.getSharedPreferences("PAYME_NATIVE", Context.MODE_PRIVATE)
        val all = sharedPreference.all
        Log.d(PayMEMiniApp.TAG, "native preferences count=${all.size}")

        if (all.isNotEmpty()) {
            try {
                all.forEach { (_, value) ->
                    val json = JSONObject(value.toString())
                    Log.d(PayMEMiniApp.TAG, "[SEND_NATIVE_PREF]")
                    evaluate(context as Activity, webView, "nativePreferences", json.toString(), null)
                }
            } catch (e: JSONException) {
                Log.d(PayMEMiniApp.TAG, "sendNativePref exception: $e")
            }
        }
    }

    fun setNativePreferences(context: Context, data: String?) {
        Log.d(PayMEMiniApp.TAG, "[SET_NATIVE_PREF]")
        if (data == null) {
            return
        }
        val sharedPreference = context.getSharedPreferences("PAYME_NATIVE", Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        try {
            val json = JSONObject(data)
            val key = json.keys().next()
            editor.putString(key, data)
            editor.apply()
        } catch (e: JSONException) {
            Log.d(PayMEMiniApp.TAG, "setNativePref exception: $e")
        }
    }

    fun nativePermissionStatus(activity: Activity, webView: WebView, type: String, status: String) {
        val responsePermissions = JSONObject()
        responsePermissions.put("type", type)
        responsePermissions.put("state", status)
        evaluate(activity, webView, "nativePermissionStatus", responsePermissions.toString(), null)
    }

    fun nativeOpenKeyboard(context: Context, view: View?) {
        if (view == null) {
            return
        }
        val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }
}

package com.payme.sdk.network_requests

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.*
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.random.Random

object NetworkUtils {
    fun getApiUrl(env: ENV): String {
        return when(env) {
            ENV.PRODUCTION -> "https://gapi.payme.vn"
            ENV.SANDBOX -> "https://sbx-gapi.payme.vn"
            ENV.DEV -> "http://10.8.103.46:3000"
            else -> "https://gapi.payme.vn"
        }
    }
}

internal class NetworkRequest(
    private val context: Context,
    private val url: String,
    private val path: String,
    private val token: String,
    private val params: MutableMap<String, Any>?,
) {
    fun setOnRequest(
        onSuccess: (response: JSONObject) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        val cryptoRSA = CryptoRSA()
        val nextValues = Random.nextInt(0, 10000000)
        val encryptKey = nextValues.toString()
        val xAPIKey = cryptoRSA.encrypt(encryptKey)
        val cryptoAES = CryptoAES()
        val xAPIAction = cryptoAES.encryptAES(encryptKey, path)
        val xAPIMessage =
            cryptoAES.encryptAES(encryptKey, JSONObject(params as Map<*, *>).toString())
        val valueParams = xAPIAction + "POST" + token + xAPIMessage + encryptKey
        val xAPIValidate = cryptoAES.getMD5(valueParams)
        val body: MutableMap<String, Any> = mutableMapOf()
        body["x-api-message"] = xAPIMessage
        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(
            Method.POST,
            url + path,
            JSONObject(body as Map<*, *>),
            Response.Listener { response ->
                try {
                    var finalJSONObject: JSONObject? = null
                    val jsonObject = JSONObject(response.toString())
                    val xAPIMessageResponse = jsonObject.getString("x-api-message")
                    val headers = jsonObject.getJSONObject("headers")
                    val xAPIKeyResponse = headers.getString("x-api-key")
                    val decryptKey = cryptoRSA.decrypt(xAPIKeyResponse)
                    val result = cryptoAES.decryptAES(decryptKey, xAPIMessageResponse)
                    finalJSONObject = JSONObject(result)
                    Log.d(PayMEMiniApp.TAG, "PARAMS $params")
                    Log.d(PayMEMiniApp.TAG, "RESPONSE $finalJSONObject")
                    onSuccess(finalJSONObject)
                } catch (error: Exception) {
                    Log.d(PayMEMiniApp.TAG, "error ${error.message}")
                    onError(
                        ActionOpenMiniApp.GET_BALANCE,
                       PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.CONNECTION_LOST.toString())
                    )
                }
            },
            Response.ErrorListener { error ->
                Log.d(PayMEMiniApp.TAG, "error ${error.message}")
                onError(
                    ActionOpenMiniApp.GET_BALANCE,
                    PayMEError(PayMEErrorType.Network, PayMENetworkErrorCode.CONNECTION_LOST.toString())
                )
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers: MutableMap<String, String> = mutableMapOf()
                headers["Authorization"] = token
                headers["Accept"] = "application/json"
                headers["Content-Type"] = "application/json"
                headers["x-api-client"] = PayMEMiniApp.appId
                headers["x-api-key"] = xAPIKey
                headers["x-api-action"] = xAPIAction
                headers["x-api-validate"] = xAPIValidate
                return headers
            }

            override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
                return try {
                    val jsonString = String(
                        response!!.data,
                        Charset.forName(
                            HttpHeaderParser.parseCharset(
                                response.headers,
                                PROTOCOL_CHARSET
                            )
                        )
                    )
                    val jsonResponse = JSONObject(jsonString)
                    jsonResponse.put("headers", JSONObject(response.headers as Map<*, *>))
                    Response.success(jsonResponse, HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: JSONException) {
                    Response.error<JSONObject>(ParseError(e))
                } catch (je: JSONException) {
                    Response.error<JSONObject>(ParseError(je))
                }
            }
        }
        val defaultRetryPolicy = DefaultRetryPolicy(
            30000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        request.retryPolicy = defaultRetryPolicy
        queue.add(request)
    }
}

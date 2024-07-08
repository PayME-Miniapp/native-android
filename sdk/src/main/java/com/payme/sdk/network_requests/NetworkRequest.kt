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
        return when (env) {
            ENV.PRODUCTION, ENV.STAGING -> "https://gapi.payme.vn"
            ENV.SANDBOX -> "https://sbx-gapi.payme.vn"
            ENV.DEV, ENV.LOCAL -> "http://vula.mecorp.local:3000"
        }
    }
}

internal class NetworkRequest(
    private val context: Context,
    private val baseUrl: String,
    private val apiPath: String,
    private val token: String,
    private val params: MutableMap<String, String>,
    private val action: ActionOpenMiniApp
) {
    fun setOnRequest(
        onSuccess: (response: JSONObject) -> Unit, onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        val cryptoRSA = CryptoRSA()
        val encryptionKey = Random.nextInt(0, 10000000).toString() // Simplified variable name
        val encryptedKey = cryptoRSA.encrypt(encryptionKey)
        val cryptoAES = CryptoAES()
        val encryptedAction = cryptoAES.encryptAES(encryptionKey, apiPath)
        val encryptedMessage = cryptoAES.encryptAES(
            encryptionKey, JSONObject(params as Map<*, *>).toString()
        )

        // Concatenate values for validation
        val validationString = encryptedAction + "POST" + token + encryptedMessage + encryptionKey
        val validationHash = cryptoAES.getMD5(validationString)

        val requestBody: MutableMap<String, Any> = mutableMapOf()
        requestBody["x-api-message"] = encryptedMessage

        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST,
            baseUrl + apiPath,
            JSONObject(requestBody as Map<*, *>),
            Response.Listener { response ->
                try {
                    val headers = response.getJSONObject("headers")
                    val receivedEncryptedKey = headers.getString("x-api-key")
                    val decryptedKey = cryptoRSA.decrypt(receivedEncryptedKey)
                    val decryptedMessage =
                        cryptoAES.decryptAES(decryptedKey, response.getString("x-api-message"))

                    val finalJSONObject = decryptedMessage?.let { JSONObject(it) }
                    Log.d(PayMEMiniApp.TAG, "PARAMS $params")
                    Log.d(PayMEMiniApp.TAG, "RESPONSE $finalJSONObject")
                    finalJSONObject?.let { onSuccess(it) } ?: run {
                        // Handle the case where finalJSONObject is null
                        onError(
                            action, PayMEError(
                                PayMEErrorType.Network,
                                PayMENetworkErrorCode.CONNECTION_LOST.toString()
                            )
                        )
                    }
                } catch (error: Exception) {
                    Log.d(PayMEMiniApp.TAG, "error ${error.message}")
                    onError(
                        action, PayMEError(
                            PayMEErrorType.Network, PayMENetworkErrorCode.CONNECTION_LOST.toString()
                        )
                    )
                }
            },
            Response.ErrorListener { error ->
                val errorCode = when (error) {
                    is TimeoutError -> PayMENetworkErrorCode.TIMED_OUT.toString()
                    is NoConnectionError -> PayMENetworkErrorCode.CONNECTION_LOST.toString()
                    is AuthFailureError -> PayMENetworkErrorCode.OTHER.toString()
                    is ServerError -> PayMENetworkErrorCode.SERVER_ERROR.toString()
                    is NetworkError -> PayMENetworkErrorCode.CONNECTION_LOST.toString()
                    is ParseError -> PayMENetworkErrorCode.DECODE_FAILED.toString()
                    is VolleyError -> PayMENetworkErrorCode.OTHER.toString()
                    else -> PayMENetworkErrorCode.OTHER.toString()
                }
                Log.d(PayMEMiniApp.TAG, "error $errorCode")
                onError(
                    action, PayMEError(
                        PayMEErrorType.Network, errorCode
                    )
                )
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers: MutableMap<String, String> = mutableMapOf()
                headers["Authorization"] = token
                headers["Accept"] = "application/json"
                headers["Content-Type"] = "application/json"
                headers["x-api-client"] = PayMEMiniApp.appId
                headers["x-api-key"] = encryptedKey
                headers["x-api-action"] = encryptedAction
                headers["x-api-validate"] = validationHash
                return headers
            }

            override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
                return try {
                    val jsonString = String(
                        response!!.data, Charset.forName(
                            HttpHeaderParser.parseCharset(
                                response.headers, PROTOCOL_CHARSET
                            )
                        )
                    )
                    val jsonResponse = JSONObject(jsonString)
                    jsonResponse.put("headers", JSONObject(response.headers as Map<*, *>))
                    Response.success(jsonResponse, HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: JSONException) {
                    Log.d(PayMEMiniApp.TAG, "JSON parsing error", e)
                    Response.error(ParseError(e))
                }
            }
        }
        val defaultRetryPolicy = DefaultRetryPolicy(
            30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        request.retryPolicy = defaultRetryPolicy
        queue.add(request)
    }
}

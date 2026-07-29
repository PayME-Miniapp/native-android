package com.payme.sdk.network_requests

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.*
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset

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
    private val action: ActionOpenMiniApp,
    private val config: PayMEConfig = PayMERuntime.requireConfig()
) {
    fun setOnRequest(
        onSuccess: (response: JSONObject) -> Unit, onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        val encryptedRequest = EncryptedNetworkRequestBuilder(config).build(apiPath, token, params)
        val responseCryptoRSA = CryptoRSA(config.publicKey, config.privateKey)
        val responseCryptoAES = CryptoAES()

        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST,
            baseUrl + apiPath,
            encryptedRequest.body,
            Response.Listener { response ->
                try {
                    val headers = response.getJSONObject("headers")
                    val receivedEncryptedKey = headers.getString("x-api-key")
                    val decryptedKey = responseCryptoRSA.decrypt(receivedEncryptedKey)
                    val decryptedMessage =
                        responseCryptoAES.decryptAES(decryptedKey, response.getString("x-api-message"))

                    val finalJSONObject = JSONObject(decryptedMessage)
                    Log.d(PayMEMiniApp.TAG, "Network request succeeded: action=$action path=$apiPath")
                    onSuccess(finalJSONObject)
                } catch (error: Exception) {
                    Log.d(PayMEMiniApp.TAG, "error ${error.message}")
                    onError(
                        action, PayMEError(
                            PayMEErrorType.Network, 
                            PayMENetworkErrorCode.CONNECTION_LOST.toString(),
                            context.getString(getPayMENetworkErrorDescription(PayMENetworkErrorCode.CONNECTION_LOST.toString()))
                        )
                    )
                }
            },
            Response.ErrorListener { error ->
                val errorCode = NetworkErrorMapper.code(error)
                Log.d(PayMEMiniApp.TAG, "error $errorCode")
                onError(
                    action, PayMEError(
                        PayMEErrorType.Network, 
                        errorCode,
                        context.getString(getPayMENetworkErrorDescription(errorCode))
                    )
                )
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                return encryptedRequest.headers.toMutableMap()
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

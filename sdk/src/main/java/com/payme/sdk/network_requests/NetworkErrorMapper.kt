package com.payme.sdk.network_requests

import com.android.volley.AuthFailureError
import com.android.volley.NetworkError
import com.android.volley.NoConnectionError
import com.android.volley.ParseError
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.payme.sdk.models.PayMENetworkErrorCode

internal object NetworkErrorMapper {
    fun code(error: VolleyError): String {
        return when (error) {
            is TimeoutError -> PayMENetworkErrorCode.TIMED_OUT.toString()
            is NoConnectionError -> PayMENetworkErrorCode.CONNECTION_LOST.toString()
            is AuthFailureError -> PayMENetworkErrorCode.OTHER.toString()
            is ServerError -> PayMENetworkErrorCode.SERVER_ERROR.toString()
            is NetworkError -> PayMENetworkErrorCode.CONNECTION_LOST.toString()
            is ParseError -> PayMENetworkErrorCode.DECODE_FAILED.toString()
            else -> PayMENetworkErrorCode.OTHER.toString()
        }
    }
}

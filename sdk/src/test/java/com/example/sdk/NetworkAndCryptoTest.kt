package com.example.sdk

import com.android.volley.NoConnectionError
import com.android.volley.ParseError
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.payme.sdk.models.ENV
import com.payme.sdk.models.PayMENetworkErrorCode
import com.payme.sdk.models.getPayMENetworkErrorDescription
import com.payme.sdk.network_requests.CryptoAES
import com.payme.sdk.network_requests.NetworkErrorMapper
import com.payme.sdk.network_requests.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NetworkAndCryptoTest {
    @Test
    fun apiUrlMatchesEnvironment() {
        assertEquals("https://gapi.payme.vn", NetworkUtils.getApiUrl(ENV.PRODUCTION))
        assertEquals("https://gapi.payme.vn", NetworkUtils.getApiUrl(ENV.STAGING))
        assertEquals("https://sbx-gapi.payme.vn", NetworkUtils.getApiUrl(ENV.SANDBOX))
        assertEquals("http://vula.mecorp.local:3000", NetworkUtils.getApiUrl(ENV.DEV))
        assertEquals("http://vula.mecorp.local:3000", NetworkUtils.getApiUrl(ENV.LOCAL))
    }

    @Test
    fun aesRoundTripRestoresClearText() {
        val crypto = CryptoAES()
        val encrypted = crypto.encryptAES("123456", """{"amount":120000}""")
        assertNotEquals("""{"amount":120000}""", encrypted)
        assertEquals("""{"amount":120000}""", crypto.decryptAES("123456", encrypted))
    }

    @Test
    fun networkErrorDescriptionFallsBackForUnknownCode() {
        assertEquals(
            com.payme.sdk.R.string.network_connection_failed,
            getPayMENetworkErrorDescription(PayMENetworkErrorCode.CONNECTION_LOST.toString())
        )
        assertEquals(
            com.payme.sdk.R.string.error_occurred,
            getPayMENetworkErrorDescription("UNKNOWN")
        )
    }

    @Test
    fun volleyErrorsMapToPayMENetworkErrorCodes() {
        assertEquals(PayMENetworkErrorCode.TIMED_OUT.toString(), NetworkErrorMapper.code(TimeoutError()))
        assertEquals(
            PayMENetworkErrorCode.CONNECTION_LOST.toString(),
            NetworkErrorMapper.code(NoConnectionError())
        )
        assertEquals(PayMENetworkErrorCode.SERVER_ERROR.toString(), NetworkErrorMapper.code(ServerError()))
        assertEquals(PayMENetworkErrorCode.DECODE_FAILED.toString(), NetworkErrorMapper.code(ParseError()))
        assertEquals(PayMENetworkErrorCode.OTHER.toString(), NetworkErrorMapper.code(VolleyError()))
    }
}

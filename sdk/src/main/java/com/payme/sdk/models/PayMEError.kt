package com.payme.sdk.models

import com.payme.sdk.R

class PayMEError(
    val type: PayMEErrorType,
    val code: String,
    val description: String,
    val isCloseMiniApp: Boolean = false
)

enum class PayMEErrorType {
    MiniApp, UserCancel, Network
}

enum class PayMENetworkErrorCode {
    ENCODE_FAILED, DECODE_FAILED, CONNECTION_LOST, TIMED_OUT, NO_RESPONSE, OTHER, SERVER_ERROR
}

fun getPayMENetworkErrorDescription(code: String): Int {
    return when (code) {
        PayMENetworkErrorCode.ENCODE_FAILED.toString() -> R.string.encryption_failed
        PayMENetworkErrorCode.DECODE_FAILED.toString() -> R.string.decryption_failed
        PayMENetworkErrorCode.CONNECTION_LOST.toString() -> R.string.network_connection_failed
        PayMENetworkErrorCode.TIMED_OUT.toString() -> R.string.server_connection_timed_out
        PayMENetworkErrorCode.NO_RESPONSE.toString() -> R.string.cannot_connect_to_server
        PayMENetworkErrorCode.SERVER_ERROR.toString() -> R.string.server_error_try_again
        PayMENetworkErrorCode.OTHER.toString() -> R.string.error_occurred
        else -> R.string.error_occurred
    }
}

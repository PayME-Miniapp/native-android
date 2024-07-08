package com.payme.sdk.models

class PayMEError(type: PayMEErrorType, code: String, description: String = "Có lỗi xảy ra", isCloseMiniApp: Boolean = false) {
    val code: String
    val description: String
    val isCloseMiniApp: Boolean
    init {
        when (type) {
            PayMEErrorType.MiniApp -> {
                this.code = code
                this.description = description
                this.isCloseMiniApp = isCloseMiniApp
            }
            PayMEErrorType.UserCancel -> {
                this.code = "USER_CANCEL"
                this.description = "Người dùng đóng PayMEMiniApp"
                this.isCloseMiniApp = isCloseMiniApp
            }
            PayMEErrorType.Network -> {
                this.code = code
                this.description = getPayMENetworkErrorDescription(code)
                this.isCloseMiniApp = isCloseMiniApp
            }
        }
    }
}

enum class PayMEErrorType {
    MiniApp, UserCancel, Network
}

enum class PayMENetworkErrorCode {
    ENCODE_FAILED, DECODE_FAILED, CONNECTION_LOST, TIMED_OUT, NO_RESPONSE, OTHER, SERVER_ERROR
}

fun getPayMENetworkErrorDescription(code: String): String {
    return when (code) {
        PayMENetworkErrorCode.ENCODE_FAILED.toString() -> "Mã hóa không thành công"
        PayMENetworkErrorCode.DECODE_FAILED.toString() -> "Giải mã không thành công"
        PayMENetworkErrorCode.CONNECTION_LOST.toString() -> "Kết nối mạng bị sự cố, vui lòng kiểm tra và thử lại. Xin cảm ơn!"
        PayMENetworkErrorCode.TIMED_OUT.toString() -> "Kết nối tới máy chủ quá lâu, vui lòng kiểm tra và thử lại. Xin cảm ơn!"
        PayMENetworkErrorCode.NO_RESPONSE.toString() -> "Không thể kết nối tới server"
        PayMENetworkErrorCode.SERVER_ERROR.toString() -> "Máy chủ gặp lỗi. Vui lòng thử lại sau"
        PayMENetworkErrorCode.OTHER.toString() -> "Có lỗi xảy ra"
        else -> "Có lỗi xảy ra"
    }
}
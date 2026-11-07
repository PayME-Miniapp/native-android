package com.payme.sdk.runtime

import com.payme.sdk.models.PayMEError
import org.json.JSONObject

/**
 * Các đường đóng có callback cho partner. Phiên được đánh dấu đang đóng TRƯỚC callback để partner
 * mở lại ngay trong callback không bị MINIAPP_ALREADY_OPEN; [close] luôn chạy kể cả khi callback ném lỗi.
 */
internal object SessionClose {
    /** Back / jsClose: USER_CANCEL một lần rồi đóng. Phiên đã có lệnh đóng thì bỏ qua. */
    fun userCancel(session: MiniAppSession, description: String, close: () -> Unit) {
        if (session.isCloseRequested) return
        PayMERuntime.markCloseRequested(session)
        try {
            session.notifyUserCancel(description)
        } finally {
            close()
        }
    }

    /** jsResponse: đóng sau onResponse nếu isCloseMiniApp. */
    fun response(session: MiniAppSession, json: JSONObject?, isCloseMiniApp: Boolean, close: () -> Unit) {
        if (isCloseMiniApp) PayMERuntime.markCloseRequested(session)
        try {
            session.callbacks.onResponse(session.action, json)
        } finally {
            if (isCloseMiniApp) close()
        }
    }

    /** jsError: đóng sau onError nếu isCloseMiniApp (bên gọi đã lọc lỗi kết thúc trùng). */
    fun error(session: MiniAppSession, error: PayMEError, close: () -> Unit) {
        if (error.isCloseMiniApp) PayMERuntime.markCloseRequested(session)
        try {
            session.callbacks.onError(session.action, error)
        } finally {
            if (error.isCloseMiniApp) close()
        }
    }
}

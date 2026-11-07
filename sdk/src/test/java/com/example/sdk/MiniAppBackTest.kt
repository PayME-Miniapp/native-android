package com.example.sdk

import com.payme.sdk.ui.miniapp.MiniAppBackAction
import com.payme.sdk.ui.miniapp.WebBackHookGate
import com.payme.sdk.ui.miniapp.isWebBackHandled
import com.payme.sdk.ui.miniapp.resolveMiniAppBack
import com.payme.sdk.ui.miniapp.resolveMiniAppBackAfterHook
import com.payme.sdk.ui.miniapp.resolveMiniAppBackOnHookTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quyết định back (thuần): hook web trước, rồi lùi web / bỏ qua / đóng. Phần WebView/dispatcher không được test ở đây. */
class MiniAppBackTest {
    private val url = "http://localhost:8080/home"

    @Test
    fun backNavigatesInsideTheWebAppWhenItCan() {
        assertEquals(MiniAppBackAction.WEB_BACK, resolveMiniAppBack(canGoBack = true, url = url, isBlockedScreen = false))
    }

    @Test
    fun backClosesTheMiniAppWhenThereIsNoWebHistory() {
        assertEquals(MiniAppBackAction.CLOSE, resolveMiniAppBack(canGoBack = false, url = url, isBlockedScreen = false))
        // Đang tải (chưa có url) vẫn thoát được
        assertEquals(MiniAppBackAction.CLOSE, resolveMiniAppBack(canGoBack = false, url = "", isBlockedScreen = false))
    }

    @Test
    fun blockedScreensIgnoreBack() {
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBack(canGoBack = true, url = url, isBlockedScreen = true))
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBack(canGoBack = false, url = url, isBlockedScreen = true))
    }

    @Test
    fun historyWithoutUrlIsIgnoredAsBefore() {
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBack(canGoBack = true, url = "", isBlockedScreen = false))
    }

    @Test
    fun onlyJsTrueMeansTheWebHandledBack() {
        assertTrue(isWebBackHandled("true"))
        // Web cũ chưa có payMEHandleBack, lỗi JS, trang chưa tải: dùng logic cũ
        listOf("false", "null", "", null, "\"true\"", "1").forEach { assertFalse(isWebBackHandled(it)) }
    }

    @Test
    fun webHandledBackSkipsNativeBack() {
        assertEquals(
            MiniAppBackAction.WEB_HANDLED,
            resolveMiniAppBackAfterHook("true", canGoBack = false, url = url, isBlockedScreen = false)
        )
    }

    @Test
    fun webNotHandlingBackFallsBackToTheOldLogic() {
        assertEquals(MiniAppBackAction.WEB_BACK, resolveMiniAppBackAfterHook("false", true, url, false))
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBackAfterHook("false", true, url, true))
        assertEquals(MiniAppBackAction.CLOSE, resolveMiniAppBackAfterHook(null, false, url, false))
    }

    @Test
    fun backPressesWhileTheHookIsPendingAreIgnored() {
        val gate = WebBackHookGate()
        val token = gate.begin()
        assertNotNull(token)
        assertNull(gate.begin())
        assertTrue(gate.finish(token!!))
        assertNotNull(gate.begin())
    }

    @Test
    fun aHookResultIsHandledOnlyOnce() {
        val gate = WebBackHookGate()
        val first = gate.begin()!!
        // Timeout tới trước, kết quả JS tới muộn: chỉ xử lý một lần
        assertTrue(gate.finish(first))
        assertFalse(gate.finish(first))
        // Kết quả muộn của lần trước không được kết thúc lần chờ mới
        val second = gate.begin()!!
        assertFalse(gate.finish(first))
        assertTrue(gate.finish(second))
    }

    @Test
    fun firstHookTimeoutNeverClosesTheMiniApp() {
        // Web chậm (popup có thể vẫn mở): chỉ lùi web nếu được, không thì bỏ qua
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBackOnHookTimeout(false, canGoBack = false, url = url, isBlockedScreen = false))
        assertEquals(MiniAppBackAction.WEB_BACK, resolveMiniAppBackOnHookTimeout(false, canGoBack = true, url = url, isBlockedScreen = false))
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBackOnHookTimeout(false, canGoBack = true, url = url, isBlockedScreen = true))
    }

    @Test
    fun secondHookTimeoutInARowMayClose() {
        // Renderer treo không được bẫy người dùng
        assertEquals(MiniAppBackAction.CLOSE, resolveMiniAppBackOnHookTimeout(true, canGoBack = false, url = url, isBlockedScreen = false))
        assertEquals(MiniAppBackAction.IGNORE, resolveMiniAppBackOnHookTimeout(true, canGoBack = false, url = url, isBlockedScreen = true))
    }
}

package com.example.sdk

import com.payme.sdk.ui.miniapp.MiniAppBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppBridgeTest {
    @Test
    fun bridgeRoutesJsEntrypointsToNativeCallbacks() {
        val calls = mutableListOf<String>()
        val bridge = MiniAppBridge(
            setNativePreferences = { calls.add("setPrefs:$it") },
            sendNativePreferences = { calls.add("sendPrefs") },
            biometricAuthen = { calls.add("biometric:$it") },
            startCardKyc = { calls.add("card:$it") },
            startKalapaKyc = { calls.add("kalapaKyc:$it") },
            startKalapaNFC = { calls.add("kalapaNfc:$it") },
            startFaceKyc = { calls.add("faceKyc:$it") },
            startFaceAuthen = { calls.add("faceAuth:$it") },
            openSettings = { calls.add("settings") },
            share = { calls.add("share:$it") },
            requestPermission = { calls.add("permission:$it") },
            sendNativeDeviceInfo = { onSent -> calls.add("device"); onSent() },
            resendOpenMiniApp = { calls.add("openMiniApp") },
            getContacts = { calls.add("contacts") },
            nativeOpenKeyboard = { calls.add("keyboard") },
            openWebView = { calls.add("webview:$it") },
            onSuccess = { calls.add("success:$it") },
            onError = { calls.add("error:$it") },
            closeMiniApp = { calls.add("close") },
            openUrl = { calls.add("url:$it") },
            saveQR = { calls.add("qr:$it") },
            changeEnv = { calls.add("env:$it") },
            changeLocale = { calls.add("locale:$it") },
            setListScreenBackBlocked = { calls.add("blocked:${it.length()}") },
            setModalHeight = { calls.add("height:$it") },
            requestNFCPermission = { calls.add("nfc:$it") },
            trackLog = { calls.add("log") },
            oneSignalSendTags = { calls.add("sendTags:$it") },
            oneSignalDeleteTags = { calls.add("deleteTags:$it") }
        )

        bridge.jsLog("{\"accessToken\":\"dummy-token\",\"phone\":\"dummy-phone\"}")
        bridge.jsLog("invalid JSON")
        assertEquals(listOf("log", "log"), calls)
        calls.clear()
        bridge.jsPreferences("pref")
        bridge.jsRequestPreferences()
        bridge.jsBiometricAuthentication("bio")
        bridge.jsRequestCardKyc("card")
        bridge.jsRequestKalapaKyc("klp")
        bridge.jsRequestKalapaNfc("nfc")
        bridge.jsRequestFaceKyc("face")
        bridge.jsRequestFaceAuthen("auth")
        bridge.jsOpenSetting()
        bridge.jsShare("share")
        bridge.jsRequestPermission("camera")
        bridge.jsRequestDeviceInfo()
        bridge.jsRequestContacts()
        bridge.jsShowKeyboard()
        bridge.jsOpenWebView("web")
        bridge.jsResponse("ok")
        bridge.jsError("bad")
        bridge.jsClose()
        bridge.jsOpenUrl("https://example.com")
        bridge.jsSaveQr("qr-data")
        bridge.jsChangeEnv("sandbox")
        bridge.jsChangeLocale("en")
        bridge.jsRequestNfcPermission("check")
        bridge.jsOneSignalSendTags("tag")
        bridge.jsOneSignalDeleteTags("tag")

        assertEquals(
            listOf(
                "setPrefs:pref",
                "sendPrefs",
                "biometric:bio",
                "card:card",
                "kalapaKyc:klp",
                "kalapaNfc:nfc",
                "faceKyc:face",
                "faceAuth:auth",
                "settings",
                "share:share",
                "permission:camera",
                "device",
                "openMiniApp",
                "contacts",
                "keyboard",
                "webview:web",
                "success:ok",
                "error:bad",
                "close",
                "url:https://example.com",
                "qr:qr-data",
                "env:sandbox",
                "locale:en",
                "nfc:check",
                "sendTags:tag",
                "deleteTags:tag"
            ),
            calls
        )
        calls.clear()
        val transport = com.payme.sdk.webServer.JavaScriptInterface(bridge)
        transport.dispatch("invalid JSON")
        transport.dispatch("{\"type\":\"unknown\",\"data\":{}}")
        assertEquals(emptyList<String>(), calls)
        transport.dispatch("{\"type\":\"jsClose\",\"data\":null}")
        assertEquals(listOf("close"), calls)
        calls.clear()
        // Reload: the web boots with jsRequestDeviceInfo and needs openMiniApp after the normal reply.
        transport.dispatch("{\"type\":\"jsRequestDeviceInfo\",\"data\":{}}")
        assertEquals(listOf("device", "openMiniApp"), calls)
    }

    @Test
    fun openMiniAppWaitsForDeferredDeviceInfoReply() {
        val calls = mutableListOf<String>()
        val pendingReplies = mutableListOf<() -> Unit>()
        val bridge = MiniAppBridge(
            setNativePreferences = {}, sendNativePreferences = {}, biometricAuthen = {}, startCardKyc = {},
            startKalapaKyc = {}, startKalapaNFC = {}, startFaceKyc = {}, startFaceAuthen = {}, openSettings = {},
            share = {}, requestPermission = {},
            // Cold root-check cache: the reply is posted later from BridgeExecutor.
            sendNativeDeviceInfo = { onSent -> pendingReplies.add { calls.add("device"); onSent() } },
            resendOpenMiniApp = { calls.add("openMiniApp") },
            getContacts = {}, nativeOpenKeyboard = {}, openWebView = {}, onSuccess = {}, onError = {},
            closeMiniApp = {}, openUrl = {}, saveQR = {}, changeEnv = {}, changeLocale = {},
            setListScreenBackBlocked = {}, setModalHeight = {}, requestNFCPermission = {}, trackLog = {},
            oneSignalSendTags = null, oneSignalDeleteTags = null
        )

        bridge.jsRequestDeviceInfo()
        assertEquals(emptyList<String>(), calls)
        pendingReplies.single().invoke()
        assertEquals(listOf("device", "openMiniApp"), calls)
    }

    @Test
    fun rootCheckIsCachedPerProcessOnlyWhenComputedWithContext() {
        com.payme.sdk.utils.DeviceSecurityUtils.clearRootCacheForTest()
        try {
            var runs = 0
            val detect = { runs++; true }
            assertTrue(com.payme.sdk.utils.DeviceSecurityUtils.cachedRootCheck(cacheable = false, detect = detect))
            assertFalse(com.payme.sdk.utils.DeviceSecurityUtils.isRootCheckCached())
            assertTrue(com.payme.sdk.utils.DeviceSecurityUtils.cachedRootCheck(cacheable = true, detect = detect))
            assertTrue(com.payme.sdk.utils.DeviceSecurityUtils.isRootCheckCached())
            assertTrue(com.payme.sdk.utils.DeviceSecurityUtils.cachedRootCheck(cacheable = true) { runs++; false })
            assertEquals(2, runs)
        } finally {
            com.payme.sdk.utils.DeviceSecurityUtils.clearRootCacheForTest()
        }
    }
}

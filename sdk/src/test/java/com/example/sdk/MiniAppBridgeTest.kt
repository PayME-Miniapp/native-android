package com.example.sdk

import com.payme.sdk.ui.miniapp.MiniAppBridge
import org.junit.Assert.assertEquals
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
            sendNativeDeviceInfo = { calls.add("device") },
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
            oneSignalSendTags = { calls.add("sendTags:$it") },
            oneSignalDeleteTags = { calls.add("deleteTags:$it") }
        )

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
    }
}

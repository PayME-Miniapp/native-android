package com.example.sdk

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** App đối tác bật minify (R8) phải build được mà không tự thêm rule cho thư viện eKYC/NFC của SDK. */
class ConsumerRulesTest {
    private val rules = File("consumer-rules.pro").readText()

    @Test fun keepsEkycAndNfcLibraries() {
        listOf("vn.kalapa.ekyc.**", "com.fis.ekyc.**", "com.fis.nfc.**").forEach {
            assertTrue("thiếu -keep $it", rules.contains("-keep class $it"))
        }
    }

    @Test fun silencesOptionalClassesOfEkycLibraries() {
        listOf("io.reactivex.Observable", "org.jmrtd.lds.DataGroup", "vn.kalapa.nfc.R\$layout").forEach {
            assertTrue("thiếu -dontwarn $it", rules.contains("-dontwarn $it"))
        }
    }

    @Test fun keepsGsonSerializedNameFields() {
        assertTrue(rules.contains("@com.google.gson.annotations.SerializedName"))
    }

    @Test fun keepsMlKitRegistrarConstructors() {
        // R8 full mode (AGP 9) bỏ constructor của registrar → ML Kit (khuôn mặt, CCCD) không khởi tạo được.
        assertTrue(rules.contains("implements com.google.firebase.components.ComponentRegistrar"))
        assertTrue(rules.contains("<init>();"))
    }
}

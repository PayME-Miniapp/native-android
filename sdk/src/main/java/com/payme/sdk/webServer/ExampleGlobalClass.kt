package com.payme.sdk.webServer

import android.graphics.Bitmap
import com.payme.sdk.models.NFCVerificationData
import vn.kalapa.ekyc.models.KalapaResult
import vn.kalapa.ekyc.models.PreferencesConfig

class ExampleGlobalClass {
    companion object {
        lateinit var preferencesConfig: PreferencesConfig
        lateinit var nfcData: NFCVerificationData
        lateinit var kalapaResult: KalapaResult
        lateinit var faceImage: Bitmap
        lateinit var frontImage: Bitmap
        lateinit var backImage: Bitmap
        fun isNFCDataInitialized(): Boolean {
            return this::nfcData.isInitialized
        }

        fun isFaceImageInitialized(): Boolean {
            return this::faceImage.isInitialized
        }
        fun isFrontImageInitialized(): Boolean {
            return this::frontImage.isInitialized
        }

        fun isBackImageInitialized(): Boolean {
            return this::backImage.isInitialized
        }

        fun isPreferencesConfigInitialized(): Boolean {
            return this::preferencesConfig.isInitialized
        }

        fun isKalapaResultInitialized(): Boolean {
            return this::kalapaResult.isInitialized
        }
    }
}
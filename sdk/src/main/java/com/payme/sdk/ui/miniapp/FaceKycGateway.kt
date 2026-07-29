package com.payme.sdk.ui.miniapp

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.payme.sdk.R
import com.payme.sdk.ui.FaceAuthenticationActivity
import com.payme.sdk.ui.FaceDetectorActivity
import org.json.JSONArray
import org.json.JSONObject

internal object FaceKycGateway {
    fun createFaceDetectorIntent(fragment: Fragment, data: JSONObject): Intent {
        val hints = data.optJSONArray("hints") ?: JSONArray().apply {
            put(fragment.getString(R.string.face_detector_hint1))
            put(fragment.getString(R.string.face_detector_hint2))
            put(fragment.getString(R.string.face_detector_hint3))
        }
        return Intent(fragment.requireContext(), FaceDetectorActivity::class.java).apply {
            putExtra("title", data.optString("title", ""))
            putExtra("hint1", hints.get(0) as String)
            putExtra("hint2", hints.get(1) as String)
            putExtra("hint3", hints.get(2) as String)
        }
    }

    fun createFaceAuthenticationIntent(context: Context, fragment: Fragment, data: JSONObject): Intent {
        val hints = data.optJSONArray("hints") ?: JSONArray().apply {
            put(fragment.getString(R.string.face_detector_hint1))
        }
        return Intent(context, FaceAuthenticationActivity::class.java).apply {
            putExtra("title", data.optString("title", ""))
            putExtra("hint1", hints.get(0) as String)
        }
    }

    fun buildFaceKycResponse(): JSONObject {
        val images = JSONArray().apply {
            put("images/kycFace1.jpeg")
            put("images/kycFace2.jpeg")
            put("images/kycFace3.jpeg")
        }
        return JSONObject().put("images", images)
    }

    fun buildFaceAuthCancelResponse(faceAuthenData: JSONObject?): JSONObject {
        return buildFaceAuthResponse(faceAuthenData, image = null).put("error", "CLOSE")
    }

    fun buildFaceAuthSuccessResponse(faceAuthenData: JSONObject?, image: String?): JSONObject {
        return buildFaceAuthResponse(faceAuthenData, image)
    }

    private fun buildFaceAuthResponse(faceAuthenData: JSONObject?, image: String?): JSONObject {
        val action = faceAuthenData?.optString("action", "")
        val payload = faceAuthenData?.optString("payload", "")
        return JSONObject().apply {
            if (image != null) {
                put("image", image)
            }
            put("action", action)
            put("payload", payload?.let { JSONObject(it) })
        }
    }
}

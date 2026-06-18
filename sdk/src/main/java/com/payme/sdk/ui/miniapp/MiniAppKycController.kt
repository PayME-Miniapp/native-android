package com.payme.sdk.ui.miniapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.ui.FaceAuthenticationActivity
import com.payme.sdk.ui.FaceDetectorActivity
import com.payme.sdk.ui.IdentityCardActivity
import com.payme.sdk.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import vn.kalapa.ekyc.KalapaHandler
import vn.kalapa.ekyc.KalapaSDK
import vn.kalapa.ekyc.KalapaSDKConfig
import vn.kalapa.ekyc.KalapaSDKResultCode
import vn.kalapa.ekyc.KalapaScanNFCCallback
import vn.kalapa.ekyc.KalapaScanNFCError
import vn.kalapa.ekyc.models.KalapaResult

internal class MiniAppKycController(
    private val fragment: Fragment,
    private val webViewProvider: () -> WebView?,
    private val openTypeProvider: () -> OpenMiniAppType,
    private val restartWithScreen: () -> Unit
) {
    private var paramsKyc: JSONObject? = null
    private var faceAuthenData: JSONObject? = null

    private val identityCardLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val fileName = resultData?.extras?.getString("title")
                    val type = resultData?.extras?.getString("type") ?: "FRONT"
                    val responseCardKyc = JSONObject()
                    responseCardKyc.put("image", fileName)
                    responseCardKyc.put("type", type)
                    evaluateJs("nativeCardKYC", responseCardKyc.toString())
                }
            }
        }

    private val faceDetectorLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val images3 = JSONArray()
                    images3.put("images/kycFace1.jpeg")
                    images3.put("images/kycFace2.jpeg")
                    images3.put("images/kycFace3.jpeg")
                    val responseFaceKyc = JSONObject().put("images", images3)
                    Log.d(PayMEMiniApp.TAG, "responseFaceKyc: $responseFaceKyc ")
                    evaluateJs("nativeFaceKYC", responseFaceKyc.toString())
                }
            }
        }

    private val faceAuthenticationLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    val responseFaceAuthen = JSONObject()
                    val action = faceAuthenData?.optString("action", "")
                    val payload = faceAuthenData?.optString("payload", "")
                    responseFaceAuthen.put("action", action)
                    responseFaceAuthen.put("payload", payload?.let { JSONObject(it) })
                    responseFaceAuthen.put("error", "CLOSE")
                    evaluateJs("nativeFaceAuthen", responseFaceAuthen.toString())
                    Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                }

                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val image = resultData?.extras?.getString("image")
                    val action = faceAuthenData?.optString("action", "")
                    val payload = faceAuthenData?.optString("payload", "")
                    val responseFaceAuthen = JSONObject()
                    responseFaceAuthen.put("image", image)
                    responseFaceAuthen.put("action", action)
                    responseFaceAuthen.put("payload", payload?.let { JSONObject(it) })
                    Log.d(PayMEMiniApp.TAG, "responseFaceAuthen: $responseFaceAuthen")
                    evaluateJs("nativeFaceAuthen", responseFaceAuthen.toString())
                }
            }
        }

    private val requestCardKycPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { startIdentityCardActivity(it) }
            }
        }

    private val requestFaceKycPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { startFaceDetectorActivity(it) }
            }
        }

    private val requestKalapaKYCPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { startEKYC(it) }
            }
        }

    private val requestKalapaNFCPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { startNFC(it) }
            }
        }

    private val requestFaceAuthPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { startFaceAuthenticationActivity(it) }
            }
        }

    fun startCardKyc(data: String) {
        try {
            if (openTypeProvider() == OpenMiniAppType.modal) {
                restartWithScreen()
                return
            }

            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestCardKycPermissionLauncher) {
                startIdentityCardActivity(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    fun startKalapaKyc(data: String) {
        Log.d(PayMEMiniApp.TAG, "startKalapaKyc: ${JSONObject(data)} ")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestKalapaKYCPermissionLauncher) {
                startNFC(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: ${e.message} ")
        }
    }

    fun startKalapaNFC(data: String) {
        Log.d(PayMEMiniApp.TAG, "startKalapaNFC: ${JSONObject(data)} ")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestKalapaNFCPermissionLauncher) {
                startNFC(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startKalapaNfc exception: ${e.message} ")
        }
    }

    fun startFaceKyc(data: String) {
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestFaceKycPermissionLauncher) {
                startFaceDetectorActivity(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    fun startFaceAuthen(data: String) {
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestFaceAuthPermissionLauncher) {
                startFaceAuthenticationActivity(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    private fun requestCameraPermissionOrStart(
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        onGranted: () -> Unit
    ) {
        val activity = fragment.activity
        val webView = webViewProvider()
        if (activity == null || webView == null) {
            return
        }
        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Utils.nativePermissionStatus(activity, webView, "CAMERA", "GRANTED")
                onGranted()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            ) -> {
                Utils.nativePermissionStatus(activity, webView, "CAMERA", "BLOCKED")
            }

            else -> {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startIdentityCardActivity(data: JSONObject) {
        val intent = Intent(fragment.requireContext(), IdentityCardActivity::class.java)
        val title = data.optString("title", "")
        val type = data.optString("type", "FRONT")
        val description = data.optString("description", "")
        val toastError = data.optString("toastError", "")
        intent.putExtra("title", title)
        intent.putExtra("type", type)
        intent.putExtra("description", description)
        intent.putExtra("toastError", toastError)
        identityCardLauncher.launch(intent)
    }

    private fun startFaceDetectorActivity(data: JSONObject) {
        val title = data.optString("title", "")
        val jsonArray = JSONArray()
        jsonArray.put(fragment.getString(R.string.face_detector_hint1))
        jsonArray.put(fragment.getString(R.string.face_detector_hint2))
        jsonArray.put(fragment.getString(R.string.face_detector_hint3))
        val hints: JSONArray = data.optJSONArray("hints") ?: jsonArray
        val intent = Intent(fragment.requireContext(), FaceDetectorActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("hint1", hints.get(0) as String)
        intent.putExtra("hint2", hints.get(1) as String)
        intent.putExtra("hint3", hints.get(2) as String)
        faceDetectorLauncher.launch(intent)
    }

    private fun startFaceAuthenticationActivity(data: JSONObject) {
        faceAuthenData = data
        Log.d(PayMEMiniApp.TAG, "faceAuthenData: $faceAuthenData , $data ")
        val title = data.optString("title", "")
        val jsonArray = JSONArray()
        jsonArray.put(fragment.getString(R.string.face_detector_hint1))
        val hints: JSONArray = data.optJSONArray("hints") ?: jsonArray
        val intent = Intent(fragment.requireContext(), FaceAuthenticationActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("hint1", hints.get(0) as String)
        faceAuthenticationLauncher.launch(intent)
    }

    private fun startEKYC(data: JSONObject) {
        val sessionId = data.optString("token", "")
        if (sessionId != "") {
            val sdkConfig = KalapaSDKConfig.KalapaSDKConfigBuilder(fragment.requireContext() as Activity)
                .withBackgroundColor("#FFFFFF")
                .withMainColor("#33CB33")
                .withLivenessVersion(0)
                .withNFCTimeoutInSeconds(180)
                .withLanguage(PayMEMiniApp.locale.toString())
                .requireQRCode(true)
                .withSpecificLanguageForCustomer("payme")
                .build()
            val klpHandler = createKalapaHandler(data)
            KalapaSDK.KalapaSDKBuilder(fragment.requireActivity(), sdkConfig).build()
                .start(sessionId, "nfc_only", klpHandler)
        } else {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: sessionId null")
        }
    }

    private fun startNFC(data: JSONObject) {
        val sessionId = data.optString("token", "")
        if (sessionId != "") {
            val sdkConfig = KalapaSDKConfig.KalapaSDKConfigBuilder(fragment.requireContext() as Activity)
                .withBackgroundColor("#FFFFFF")
                .withMainColor("#33CB33")
                .withLivenessVersion(0)
                .withNFCTimeoutInSeconds(180)
                .withLanguage(PayMEMiniApp.locale.toString())
                .withSpecificLanguageForCustomer("payme")
                .requireQRCode(true)
                .build()
            val klpHandler = createKalapaHandler(data)
            KalapaSDK.KalapaSDKBuilder(fragment.requireActivity(), sdkConfig).build()
                .start(sessionId, "nfc_only", klpHandler)
        } else {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: sessionId null")
        }
    }

    private fun createKalapaHandler(data: JSONObject): KalapaHandler {
        return object : KalapaHandler() {
            override fun onComplete(kalapaResult: KalapaResult) {
                Log.d(PayMEMiniApp.TAG, """Kalapa NFC complete: $kalapaResult""")
                evaluateJs("nativeKalapaNFC", buildKalapaNfcResponse(data).toString())
            }

            override fun onNFCErrorHandle(
                activity: Activity,
                error: KalapaScanNFCError,
                callback: KalapaScanNFCCallback
            ) {
                Log.d(PayMEMiniApp.TAG, """NFC error handle: $error""")
                val response = buildKalapaNfcResponse(data)
                when (error) {
                    KalapaScanNFCError.ERROR_NFC_TIMEOUT -> {
                        response.put("isTimeout", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    KalapaScanNFCError.ERROR_FACE_NOT_MATCH -> {
                        response.put("isFaceNotMatch", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    KalapaScanNFCError.ERROR_NFC_INFO_NOT_MATCH -> {
                        response.put("isInfoNotMatch", true)
                        evaluateJs(activity, "nativeKalapaNFC", response.toString())
                        callback.close {}
                    }

                    else -> Unit
                }
            }

            override fun onError(resultCode: KalapaSDKResultCode) {
                Log.d(PayMEMiniApp.TAG, """startNFC error: $resultCode""")
            }

            override fun onExpired() {
                // This handler is called when current session goes expired and user clicks the Retry button in the popup.
            }
        }
    }

    private fun buildKalapaNfcResponse(data: JSONObject): JSONObject {
        val action = data.optString("action", "")
        val payload = data.optString("payload", "")
        val response = JSONObject()
        if (action != "") {
            response.put("action", action)
            if (payload != "" && action != "KLP_KYC") {
                try {
                    response.put("payload", JSONObject(payload))
                } catch (e: JSONException) {
                    Log.e(PayMEMiniApp.TAG, "Failed to parse payload as JSON", e)
                }
            }
        } else {
            response.put("action", "KLP_KYC")
        }
        return response
    }

    private fun notifyCameraPermission(status: String) {
        val activity = fragment.activity
        val webView = webViewProvider()
        if (activity != null && webView != null) {
            Utils.nativePermissionStatus(activity, webView, "CAMERA", status)
        }
    }

    private fun evaluateJs(functionName: String, payload: String) {
        val activity = fragment.activity ?: return
        evaluateJs(activity, functionName, payload)
    }

    private fun evaluateJs(activity: Activity, functionName: String, payload: String) {
        val webView = webViewProvider() ?: return
        Utils.evaluateJSWebView(activity, webView, functionName, payload, null)
    }
}

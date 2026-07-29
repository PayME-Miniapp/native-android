package com.payme.sdk.ui.miniapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.utils.WebViewJsDispatcher
import org.json.JSONArray
import org.json.JSONObject

internal class MiniAppKycController(
    private val fragment: Fragment,
    private val webViewProvider: () -> WebView?,
    private val openTypeProvider: () -> OpenMiniAppType,
    private val localeProvider: () -> Locale,
    private val restartWithScreen: () -> Unit
) {
    private var paramsKyc: JSONObject? = null
    private var faceAuthenData: JSONObject? = null
    private val kalapaGateway = KalapaKycGateway(
        fragment = fragment,
        localeProvider = localeProvider,
        evaluateJs = { activity, functionName, payload ->
            evaluateJs(activity, functionName, payload)
        }
    )

    private val identityCardLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val fileName = resultData?.extras?.getString("title")
                    val type = resultData?.extras?.getString("type") ?: "FRONT"
                    evaluateJs("nativeCardKYC", CardKycGateway.buildResponse(fileName, type).toString())
                }
            }
        }

    private val faceDetectorLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    Log.d(PayMEMiniApp.TAG, "responseFaceKyc")
                    evaluateJs("nativeFaceKYC", FaceKycGateway.buildFaceKycResponse().toString())
                }
            }
        }

    private val faceAuthenticationLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    evaluateJs(
                        "nativeFaceAuthen",
                        FaceKycGateway.buildFaceAuthCancelResponse(faceAuthenData).toString()
                    )
                    Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                }

                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val image = resultData?.extras?.getString("image")
                    Log.d(PayMEMiniApp.TAG, "responseFaceAuthen")
                    evaluateJs(
                        "nativeFaceAuthen",
                        FaceKycGateway.buildFaceAuthSuccessResponse(faceAuthenData, image).toString()
                    )
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
                paramsKyc?.let { kalapaGateway.startEkyc(it) }
            }
        }

    private val requestKalapaNFCPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            notifyCameraPermission(if (isGranted) "GRANTED" else "DENIED")
            if (isGranted) {
                paramsKyc?.let { kalapaGateway.startNfc(it) }
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
        Log.d(PayMEMiniApp.TAG, "startKalapaKyc")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestKalapaKYCPermissionLauncher) {
                kalapaGateway.startNfc(json)
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: ${e.message} ")
        }
    }

    fun startKalapaNFC(data: String) {
        Log.d(PayMEMiniApp.TAG, "startKalapaNFC")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            requestCameraPermissionOrStart(requestKalapaNFCPermissionLauncher) {
                kalapaGateway.startNfc(json)
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
                WebViewJsDispatcher.nativePermissionStatus(activity, webView, "CAMERA", "GRANTED")
                onGranted()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            ) -> {
                WebViewJsDispatcher.nativePermissionStatus(activity, webView, "CAMERA", "BLOCKED")
            }

            else -> {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startIdentityCardActivity(data: JSONObject) {
        identityCardLauncher.launch(CardKycGateway.createIntent(fragment.requireContext(), data))
    }

    private fun startFaceDetectorActivity(data: JSONObject) {
        faceDetectorLauncher.launch(FaceKycGateway.createFaceDetectorIntent(fragment, data))
    }

    private fun startFaceAuthenticationActivity(data: JSONObject) {
        faceAuthenData = data
        Log.d(PayMEMiniApp.TAG, "faceAuthenData received")
        faceAuthenticationLauncher.launch(
            FaceKycGateway.createFaceAuthenticationIntent(fragment.requireContext(), fragment, data)
        )
    }

    private fun notifyCameraPermission(status: String) {
        val activity = fragment.activity
        val webView = webViewProvider()
        if (activity != null && webView != null) {
            WebViewJsDispatcher.nativePermissionStatus(activity, webView, "CAMERA", status)
        }
    }

    private fun evaluateJs(functionName: String, payload: String) {
        val activity = fragment.activity ?: return
        evaluateJs(activity, functionName, payload)
    }

    private fun evaluateJs(activity: Activity, functionName: String, payload: String) {
        val webView = webViewProvider() ?: return
        WebViewJsDispatcher.evaluate(activity, webView, functionName, payload)
    }
}

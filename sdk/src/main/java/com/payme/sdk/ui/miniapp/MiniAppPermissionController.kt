package com.payme.sdk.ui.miniapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.ui.SubWebView
import com.payme.sdk.utils.Utils
import org.json.JSONObject

internal class MiniAppPermissionController(
    private val fragment: Fragment,
    private val webViewProvider: () -> WebView?,
    private val nativeAppStateProvider: () -> String
) {
    private var permissionType = ""
    private var paramsSaveQr: String? = null

    private val requestWriteExternalStoragePermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity != null && webView != null) {
                Utils.nativePermissionStatus(
                    activity, webView, "WRITE_EXTERNAL_STORAGE",
                    if (isGranted) "GRANTED" else "DENIED"
                )
            }
            if (isGranted) {
                paramsSaveQr?.let { downloadImageQR(it) }
            }
        }

    private val requestContactsPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity != null && webView != null) {
                Utils.nativePermissionStatus(
                    activity, webView, "READ_CONTACTS",
                    if (isGranted) "GRANTED" else "DENIED"
                )
                if (isGranted) {
                    Utils.getContacts(fragment.requireContext(), webView)
                }
            }
        }

    private val requestPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (permissionType.isEmpty()) {
                return@registerForActivityResult
            }
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity != null && webView != null) {
                Utils.nativePermissionStatus(
                    activity, webView, permissionType,
                    if (isGranted) "GRANTED" else "DENIED"
                )
            }
            permissionType = ""
        }

    fun saveQR(data: String) {
        paramsSaveQr = data
        val activity = fragment.activity
        val webView = webViewProvider()
        if (activity == null || webView == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Utils.nativePermissionStatus(activity, webView, "WRITE_EXTERNAL_STORAGE", "GRANTED")
            paramsSaveQr?.let { downloadImageQR(it) }
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                Utils.nativePermissionStatus(activity, webView, "WRITE_EXTERNAL_STORAGE", "GRANTED")
                paramsSaveQr?.let { downloadImageQR(it) }
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) -> {
                Utils.nativePermissionStatus(activity, webView, "WRITE_EXTERNAL_STORAGE", "BLOCKED")
            }

            else -> {
                requestWriteExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    fun getContacts() {
        try {
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity == null || webView == null) {
                return
            }
            when {
                ContextCompat.checkSelfPermission(
                    fragment.requireContext(), Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Utils.nativePermissionStatus(activity, webView, "READ_CONTACTS", "GRANTED")
                    Utils.getContacts(fragment.requireContext(), webView)
                }

                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.READ_CONTACTS
                ) -> {
                    Utils.nativePermissionStatus(activity, webView, "READ_CONTACTS", "BLOCKED")
                }

                else -> {
                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "getContacts exception: ${e.message} ")
        }
    }

    fun requestPermission(data: String) {
        try {
            val json = JSONObject(data)
            val type = json.optString("type", "")
            val isCheckPermissionStatus = json.getBoolean("isCheckPermissionStatus")
            permissionType = type
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity == null || webView == null) {
                return
            }
            val androidPermission = "android.permission.$permissionType"
            when {
                ContextCompat.checkSelfPermission(
                    fragment.requireContext(), androidPermission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Utils.nativePermissionStatus(activity, webView, permissionType, "GRANTED")
                }

                ActivityCompat.shouldShowRequestPermissionRationale(activity, androidPermission) -> {
                    Utils.nativePermissionStatus(activity, webView, permissionType, "BLOCKED")
                }

                else -> {
                    if (!isCheckPermissionStatus) {
                        when (permissionType) {
                            "CAMERA" -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            "READ_EXTERNAL_STORAGE" -> requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "requestPermission exception: ${e.message} ")
        }
    }

    fun requestNFCPermission() {
        try {
            val activity = fragment.activity
            val webView = webViewProvider()
            if (activity == null || webView == null) {
                return
            }
            val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(fragment.context)
            when {
                nfcAdapter == null -> {
                    Utils.nativePermissionStatus(activity, webView, "NFC", "BLOCKED")
                }

                !nfcAdapter.isEnabled -> {
                    Utils.nativePermissionStatus(activity, webView, "NFC", "DENIED")
                }

                else -> {
                    Utils.nativePermissionStatus(activity, webView, "NFC", "GRANTED")
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "requestNFCPermission exception: ${e.message} ")
        }
    }

    fun share(data: String) {
        try {
            val json = JSONObject(data)
            val title = json.optString("title", "")
            val content = json.optString("content", "")
            if (content.isNotEmpty() && nativeAppStateProvider() == "active") {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
                shareIntent.putExtra(Intent.EXTRA_TEXT, content)
                shareIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                fragment.startActivity(Intent.createChooser(shareIntent, title))
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "share exception: ${e.message} ")
        }
    }

    fun openUrl(data: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, data.substring(1, data.length - 1).toUri())
            (fragment.requireContext() as Activity).startActivity(intent)
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "openurl e ${e.message}")
        }
    }

    fun openWebView(data: String) {
        try {
            val json = JSONObject(data)
            val content = json.optString("content", "")
            val type = json.optString("type", "")
            val closeInstruction = json.optString("closeInstruction", "")
            if (content.isNotEmpty() && type.isNotEmpty()) {
                val subWebView = SubWebView(content, type, closeInstruction)
                subWebView.show(fragment.parentFragmentManager, "SUBWEBVIEW")
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "openWebView exception: ${e.message} ")
        }
    }

    private fun downloadImageQR(data: String) {
        val activity = fragment.activity
        val webView = webViewProvider()
        if (activity == null || webView == null) {
            return
        }
        val bitmap = Utils.generateQRCode(data)
        Utils.saveImage(bitmap, fragment.requireContext(), fragment.getString(R.string.qr_folder), onSuccess = {
            val response = JSONObject()
            response.put("succeeded", true)
            Utils.evaluateJSWebView(activity, webView, "nativeSaveQR", response.toString(), null)
        }, onError = {
            val response = JSONObject()
            response.put("error", "Tải mã QR thất bại")
            Utils.evaluateJSWebView(activity, webView, "nativeSaveQR", response.toString(), null)
        })
    }
}

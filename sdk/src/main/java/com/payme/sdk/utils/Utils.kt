package com.payme.sdk.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.objects.DetectedObject
import java.io.InputStream
import java.io.OutputStream

enum class BiometricError {
    APP_CANCEL,
    AUTHENTICATION_FAILED,
    PASSCODE_NOT_SET,
    SYSTEM_CANCEL,
    USER_CANCEL,
    USER_FALLBACK,
    BIOMETRY_LOCKOUT,
    BIOMETRY_NOT_AVAILABLE,
    BIOMETRY_NOT_ENROLLED,
    UNKNOWN
}

object Utils {
    fun getStatusBarHeight(activity: Activity): Int {
        return WindowMetricsUtils.getStatusBarHeight(activity)
    }

    fun getSoftNavigationHeight(context: Context): Int {
        return WindowMetricsUtils.getSoftNavigationHeight(context)
    }

    fun unzipFile(filePath: String, destination: String): Boolean {
        return ArchiveUtils.unzipFile(filePath, destination)
    }

    fun evaluateJSWebView(
        activity: Activity,
        webView: WebView,
        functionName: String,
        data: String,
        callback: ((String) -> Unit)?
    ) {
        WebViewJsDispatcher.evaluate(activity, webView, functionName, data, callback)
    }

    fun copyFileToFile(sourcePath: String, desPath: String) {
        ArchiveUtils.copyFileToFile(sourcePath, desPath)
    }

    fun copyDir(context: Context, path: String) {
        ArchiveUtils.copyDir(context, path)
    }

    fun findRandomOpenPort(): Int? {
        return FileDownloadUtils.findRandomOpenPort()
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return WindowMetricsUtils.dpToPx(context, dp)
    }

    fun pxToDp(context: Context, px: Int): Int {
        return WindowMetricsUtils.pxToDp(context, px)
    }

    fun getUserAgent(context: Context): String? {
        return WindowMetricsUtils.getUserAgent(context)
    }

    fun isDeviceRooted(context: Context? = null): Boolean {
        return DeviceSecurityUtils.isRooted(context)
    }

    fun isEmulator(): Boolean {
        return DeviceSecurityUtils.isEmulator()
    }

    fun sendNativePref(context: Context, webView: WebView) {
        WebViewJsDispatcher.sendNativePreferences(context, webView)
    }

    fun setNativePref(context: Context, data: String?) {
        WebViewJsDispatcher.setNativePreferences(context, data)
    }

    fun isBiometricReady(context: Context): Boolean {
        return BiometricGateway.isReady(context)
    }

    fun getErrorCode(errorCode: Int): BiometricError {
        return BiometricGateway.errorCode(errorCode)
    }

    fun biometricAuthenticate(activity: AppCompatActivity, webView: WebView, data: String) {
        BiometricGateway.authenticate(activity, webView, data)
    }

    fun getRootWindowInsetsCompat(rootView: View): Float? {
        return WindowMetricsUtils.getRootWindowInsetsCompat(rootView)
    }

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return ImageProcessingUtils.imageProxyToBitmap(image)
    }

    fun handleFaceImageProxy(image: ImageProxy): Bitmap? {
        return ImageProcessingUtils.handleFaceImageProxy(image)
    }

    fun handleImageProxy(context: Context, image: ImageProxy): Bitmap? {
        return ImageProcessingUtils.handleImageProxy(context, image)
    }

    fun compressBitmapToFile(context: Context, bitmap: Bitmap, fileName: String) {
        ImageProcessingUtils.compressBitmapToFile(context, bitmap, fileName)
    }

    fun nativePermissionStatus(
        activity: Activity,
        webView: WebView,
        permissionType: String,
        permissionState: String
    ) {
        WebViewJsDispatcher.nativePermissionStatus(activity, webView, permissionType, permissionState)
    }

    fun validateListObject(detectedObject: DetectedObject): Boolean {
        return ImageProcessingUtils.validateListObject(detectedObject)
    }

    fun getContacts(context: Context, webView: WebView) {
        ContactsReader.sendContacts(context, webView)
    }

    fun downloadWithoutTemp(link: String, path: String) {
        FileDownloadUtils.downloadWithoutTemp(link, path)
    }

    fun download(
        context: Context,
        link: String,
        path: String,
        onCopy: (totalBytesCopied: Long, length: Int, speed: Long) -> Unit
    ) {
        FileDownloadUtils.download(context, link, path, onCopy)
    }

    fun nativeOpenKeyboard(context: Context, view: View?) {
        WebViewJsDispatcher.nativeOpenKeyboard(context, view)
    }

    fun generateQRCode(qrContent: String): Bitmap {
        return QrImageStore.generateQRCode(qrContent)
    }

    fun saveImage(
        bitmap: Bitmap,
        context: Context,
        folderName: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        QrImageStore.saveImage(bitmap, context, folderName, onSuccess, onError)
    }

    fun formatStringToValidJsonString(dataRaw: String): String {
        return Formatters.formatStringToValidJsonString(dataRaw)
    }

    fun formatFileSize(size: Long): String {
        return Formatters.formatFileSize(size)
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        return Formatters.formatSpeed(bytesPerSecond)
    }
}

fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytesCopied: Long) -> Any): Long {
    var bytesCopied = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        onCopy(bytesCopied)
        bytes = read(buffer)
    }
    return bytesCopied
}

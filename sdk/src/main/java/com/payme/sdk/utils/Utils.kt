package com.payme.sdk.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.provider.ContactsContract
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.objects.DetectedObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import java.net.URL
import java.nio.ByteBuffer
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.min


enum class BiometricError {
    APP_CANCEL, AUTHENTICATION_FAILED, PASSCODE_NOT_SET, SYSTEM_CANCEL, USER_CANCEL, USER_FALLBACK,
    BIOMETRY_LOCKOUT, BIOMETRY_NOT_AVAILABLE, BIOMETRY_NOT_ENROLLED, UNKNOWN
}

object Utils {
    private val IPV4_PATTERN: Pattern = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$"
    )

    private fun isIPv4Address(input: String?): Boolean {
        return IPV4_PATTERN.matcher(input).matches()
    }

    fun getStatusBarHeight(activity: Activity): Int {
        val rectangle = Rect()
        val window: Window = activity.window
        window.decorView.getWindowVisibleDisplayFrame(rectangle)
        return rectangle.top
    }

    fun getSoftNavigationHeight(context: Context): Int {
        val resources: Resources = context.resources
        val resourceId: Int = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    fun unzipFile(filePath: String, destination: String) {
        try {
            val inputStream = FileInputStream(filePath)
            val zipStream = ZipInputStream(inputStream)
            var zEntry: ZipEntry? = null
            while (zipStream.nextEntry.also { zEntry = it } != null) {
                if (zEntry!!.isDirectory) {
                    val f = File(destination + "/" + zEntry!!.name)
                    if (!f.isDirectory) {
                        f.mkdirs()
                    }
                } else {
                    val fout = FileOutputStream(
                        destination + "/" + zEntry!!.name
                    )
                    val bufout = BufferedOutputStream(fout)
                    val buffer = ByteArray(1024)
                    var read = 0
                    while (zipStream.read(buffer).also { read = it } != -1) {
                        bufout.write(buffer, 0, read)
                    }
                    zipStream.closeEntry()
                    bufout.close()
                    fout.close()
                }
            }
            zipStream.close()
            Log.d("PAYME", "Unzipping complete. path : $destination")
        } catch (e: java.lang.Exception) {
            Log.d("PAYME", "Unzipping failed ${e.message}")
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress) {
                        val ip = inetAddress.hostAddress
                        if (isIPv4Address(ip)) {
                            return ip
                        }
                    }
                }
            }
        } catch (ex: SocketException) {
            Log.e("PAYME", ex.toString())
        }
        return "127.0.0.1"
    }

    fun evaluateJSWebView(
        activity: Activity,
        webView: WebView,
        functionName: String,
        data: String,
        callback: ((String) -> Unit)?
    ) {
        val injectedJS = "       const script = document.createElement('script');\n" +
                "          script.type = 'text/javascript';\n" +
                "          script.async = true;\n" +
                "          script.text = '${functionName}($data)';\n" +
                "          document.body.appendChild(script);\n" +
                "          true; // note: this is required, or you'll sometimes get silent failures\n"
        activity.runOnUiThread {
            webView.evaluateJavascript("(function() {\n$injectedJS;\n})();", callback)
            Log.d("PAYME", "[EVALUATE_JS] $functionName  $data")
        }
    }

    fun copyFileToFile(sourcePath: String, desPath: String) {
        val sourceFile = File(sourcePath)
        val desFile = File(desPath)
        sourceFile.copyTo(desFile, true)
    }

    private fun copyFile(context: Context, filePath: String) {
        val file = context.assets.open(filePath)
        val outFile = File(context.filesDir, filePath)
        val outStream = FileOutputStream(outFile)

        file.copyTo(outStream)
        outStream.close()
    }

    fun copyDir(context: Context, path: String) {
        val assets = context.assets
        val asset = assets.list(path)

        asset?.forEach { list ->
            val listPath = "$path/$list"
            if (!list.toString().contains(".")) {
                File(context.filesDir.path, listPath).mkdir()
                copyDir(context, listPath)
                return
            }
            copyFile(context, listPath)
        }
    }

    fun findRandomOpenPort(): Int? {
        return try {
            val socket = ServerSocket(0)
            val port = socket.localPort
            Log.d("PAYME", "port:$port")
            socket.close()
            port
        } catch (e: IOException) {
            4646
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun pxToDp(context: Context, px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }

    fun getUserAgent(context: Context): String? {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: RuntimeException) {
            System.getProperty("http.agent")
        }
    }

    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.lowercase(Locale.ROOT).contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("vbox86")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
                || Build.BOARD.lowercase(Locale.ROOT).contains("nox")
                || Build.BOOTLOADER.lowercase(Locale.ROOT).contains("nox")
                || Build.HARDWARE.lowercase(Locale.ROOT).contains("nox")
                || Build.PRODUCT.lowercase(Locale.ROOT).contains("nox")
                || Build.SERIAL.lowercase(Locale.ROOT).contains("nox")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    fun sendNativePref(context: Context, webView: WebView) {
        val sharedPreference = context.getSharedPreferences("PAYME_NATIVE", Context.MODE_PRIVATE)
        val all = sharedPreference.all
        Log.d("PAYME", "all $all")

        if (all.isNotEmpty()) {
            try {
                all.forEach { (_, value) ->
                    val json = JSONObject(value.toString())
                    Log.d("PAYME", "[SEND_NATIVE_PREF]set native pref json $json")
                    Utils.evaluateJSWebView(
                        context as Activity, webView, "nativePreferences", json.toString(), null
                    )
                }
            } catch (e: JSONException) {
                Log.d("PAYME", "sendNativePref exception: $e")
            }
        }
    }

    fun setNativePref(context: Context, data: String?) {
        Log.d("PAYME", "[SET_NATIVE_PREF]set native pref data $data")
        if (data == null) {
            return
        }
        val sharedPreference = context.getSharedPreferences("PAYME_NATIVE", Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        try {
            val json = JSONObject(data)
            val key = json.keys().next()
            editor.putString(key, data)
            editor.apply()
        } catch (e: JSONException) {
            Log.d("PAYME", "setNativePref exception: $e")
        }
    }

    private fun hasBiometricCapability(
        context: Context,
        authenticator: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK
    ): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(authenticator)
    }

    fun isBiometricReady(context: Context) =
        hasBiometricCapability(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun getErrorCode(errorCode: Int): BiometricError {
        return when (errorCode) {
            BiometricPrompt.ERROR_CANCELED -> BiometricError.SYSTEM_CANCEL
            BiometricPrompt.ERROR_LOCKOUT -> BiometricError.BIOMETRY_LOCKOUT
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricError.BIOMETRY_LOCKOUT
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricError.USER_CANCEL
            BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricError.BIOMETRY_NOT_ENROLLED
            BiometricPrompt.ERROR_TIMEOUT -> BiometricError.USER_CANCEL
            else -> {
                BiometricError.UNKNOWN
            }
        }
    }

    fun biometricAuthenticate(activity: AppCompatActivity, webView: WebView, data: String) {
        Log.d("PAYME", "vo hàm bio")
        activity.runOnUiThread {
            try {
                val jsonData = JSONObject(data)
                val cancelTitle = jsonData.optString("cancelTitle", "Hủy")
                val description =
                    jsonData.optString("description", "Dùng sinh trắc học để xác thực")
                val title = jsonData.optString("title", "Yêu cầu xác thực")
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setDescription(description)
                    .setNegativeButtonText(cancelTitle)
                    .setConfirmationRequired(true)
                    .setTitle(title)
                    .build()

                val executor = ContextCompat.getMainExecutor(activity)

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Log.d("PAYME", "errorcode $errorCode")
                        val resultAuthen = JSONObject()
                        resultAuthen.put("success", false)
                        resultAuthen.put("error", getErrorCode(errorCode))
                        evaluateJSWebView(
                            activity,
                            webView,
                            "nativeBiometricAuthentication",
                            resultAuthen.toString(),
                            null
                        )
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val resultAuthen = JSONObject()
                        resultAuthen.put("success", true)
                        evaluateJSWebView(
                            activity,
                            webView,
                            "nativeBiometricAuthentication",
                            resultAuthen.toString(),
                            null
                        )
                    }
                }

                val biometricPrompt = BiometricPrompt(activity, executor, callback)
                biometricPrompt.authenticate(promptInfo)
            } catch (e: JSONException) {
                Log.d("PAYME", "vo catch ${e.message}")

                val resultAuthen = JSONObject()
                resultAuthen.put("success", false)
                resultAuthen.put("error", BiometricError.UNKNOWN)
                evaluateJSWebView(
                    activity,
                    webView,
                    "nativeBiometricAuthentication",
                    resultAuthen.toString(),
                    null
                )
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getRootWindowInsetsCompatR(rootView: View): Float? {
        val insets =
            rootView.rootWindowInsets?.getInsets(
                WindowInsets.Type.statusBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.navigationBars()
            )
                ?: return null
        return insets.bottom.toFloat()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Suppress("DEPRECATION")
    private fun getRootWindowInsetsCompatM(rootView: View): Float? {
        val insets = rootView.rootWindowInsets ?: return null
        return min(insets.systemWindowInsetBottom, insets.stableInsetBottom).toFloat()
    }

    private fun getRootWindowInsetsCompatBase(rootView: View): Float {
        val visibleRect = Rect()
        rootView.getWindowVisibleDisplayFrame(visibleRect)
        return (rootView.height - visibleRect.bottom).toFloat()
    }

    fun getRootWindowInsetsCompat(rootView: View): Float? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getRootWindowInsetsCompatR(rootView)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> getRootWindowInsetsCompatM(rootView)
            else -> getRootWindowInsetsCompatBase(rootView)
        }
    }

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
    }

    fun handleFaceImageProxy(image: ImageProxy): Bitmap? {
        return try {
            val rotationDegree = image.imageInfo.rotationDegrees
            Log.d("PAYME", "rotation $rotationDegree")
            var bitmap = imageProxyToBitmap(image)
            val rotationMatrix = Matrix()
            rotationMatrix.postRotate((rotationDegree).toFloat())
            if (rotationDegree != 0) {
                bitmap =
                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        rotationMatrix,
                        true
                    )
            }
            bitmap
        } catch (e: Exception) {
            Log.d("PAYME", "handle image proxy ${e.message}")
            null
        }
    }

    fun handleImageProxy(context: Context, image: ImageProxy, previewView: View): Bitmap? {
        try {
            val rotationDegree = image.imageInfo.rotationDegrees
            Log.d("PAYME", "rotation $rotationDegree")
            var bitmap = imageProxyToBitmap(image)

            val rotationMatrix = Matrix()
            rotationMatrix.postRotate((rotationDegree).toFloat())
            if (rotationDegree != 0) {
                bitmap =
                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        rotationMatrix,
                        true
                    )
            }
            val viewportMargin = dpToPx(context, 20)
            val displayMetrics = DisplayMetrics()
            val display = previewView.display
            val metrics = DisplayMetrics().also { display.getMetrics(it) }
            (context as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
//    val width = displayMetrics.widthPixels.toFloat() - viewportMargin
            val bitmapHeight = bitmap.height
            val windowHeight = metrics.heightPixels
            val width = bitmap.width - viewportMargin * 2
            val top = dpToPx(context, 86) * bitmapHeight / windowHeight
            val height = bitmap.width * 0.7
//    Log.d("PAYME", "x $viewportMargin y $top x $width x $height ")
            Log.d(
                "PAYME",
                "screeheight ${displayMetrics.heightPixels} bitmapHeight $bitmapHeight windowHeight $windowHeight"
            )
            bitmap = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height.toInt())
            return bitmap
        } catch (e: Exception) {
            Log.d("PAYME", "handle image proxy ${e.message}")
            return null
        }
    }

    fun compressBitmapToFile(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            val imagesDir = File("${context.filesDir.path}/www/sdkWebapp3-main", "images")
            val f = File(imagesDir, fileName)
            if (f.exists()) {
                f.delete();
            }
            f.createNewFile()
            val out = FileOutputStream(f)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            Log.d("PAYME", "save image exception ${e.message}")
        }
    }

    fun nativePermissionStatus(
        activity: Activity,
        webView: WebView,
        permissionType: String,
        permissionState: String
    ) {
        val responsePermissions = JSONObject()
        responsePermissions.put("type", permissionType)
        responsePermissions.put("state", permissionState)
        evaluateJSWebView(
            activity,
            webView,
            "nativePermissionStatus",
            responsePermissions.toString(),
            null
        )
    }

    fun validateListObject(detectedObject: DetectedObject): Boolean {
        if (detectedObject.labels.size == 0) {
            return false
        }
        val label = detectedObject.labels[0].text.lowercase()
        if (label.contains("card") || label.contains("license")) {
            return true
        }
        return false
    }

    @SuppressLint("Range")
    fun getContacts(context: Context, webView: WebView) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                val contacts = JSONArray()
                val cr: ContentResolver = context.contentResolver
                val cur = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, null
                )

                if ((cur?.count ?: 0) > 0) {
                    while (cur != null && cur.moveToNext()) {
                        val name = cur.getString(
                            cur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                            )
                        )
                        val phoneNumber =
                            cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                .replace("[^0-9]".toRegex(), "")
                        val phone = JSONObject("""{name:"$name",phone:"$phoneNumber"}""")
                        contacts.put(phone)
                    }
                }
                cur?.close()
                evaluateJSWebView(
                    context as Activity,
                    webView,
                    "nativeContacts",
                    contacts.toString(),
                    null
                )
            }
        } catch (e: Exception) {
            Log.d("PAYME", "util get contacts exception ${e.message}")
        }

    }

    fun downloadWithoutTemp(link: String, path: String) {
        URL(link).openStream().use { input ->
            FileOutputStream(File(path)).use { output ->
                input.copyTo(output)
            }
        }
        Log.d("PAYME", "done download")
    }

    @SuppressLint("SetTextI18n")
    fun download(
        context: Context,
        link: String,
        path: String,
        onCopy: (totalBytesCopied: Long, length: Int) -> Unit
    ) {
        val sourceTemp = File("${context.filesDir.path}/update", "sdkWebapp3-mainTemp.zip")
        val destSource = File(path)
        val length = URL(link).openConnection().contentLength
        URL(link).openStream().use { input ->
            FileOutputStream(sourceTemp).use { output ->
                input.copyTo(output, onCopy = { totalBytesCopied ->
                    onCopy(totalBytesCopied, length)
                })
            }
        }
        if (sourceTemp.length() > 0) {
            sourceTemp.copyTo(destSource, true)
            Log.d("PAYME", "done download")
            sourceTemp.delete()
        } else {
            Log.d("PAYME", "downlaod fail")
        }
    }

    fun nativeOpenKeyboard(context: Context, view: View?) {
        if (view == null) {
            return
        }
        val imm: InputMethodManager =
            context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }
}

fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytesCopied: Long) -> Any): Long {
    var bytesCopied: Long = 0
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
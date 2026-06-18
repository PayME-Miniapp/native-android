package com.payme.sdk.ui.miniapp

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.payme.sdk.BuildConfig
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.Locale
import com.payme.sdk.models.PayMEVersion
import com.payme.sdk.utils.LocaleUtils
import com.payme.sdk.utils.NetworkMonitor
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.WebServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal class MiniAppUpdateController(
    private val contextProvider: () -> Context?,
    private val activityProvider: () -> android.app.Activity?,
    private val viewsProvider: () -> MiniAppViews,
    private val updateViewModelProvider: () -> PayMEUpdatePatchViewModel,
    private val getLoadUrl: () -> String,
    private val setLoadUrl: (String) -> Unit,
    private val returnError: (String) -> Unit,
    private val closeMiniApp: () -> Unit
) {
    private var wwwRoot: File? = null
    private var server: com.payme.sdk.webServer.MySimpleWebServer? = null
    private var port = 4646

    private var lastDownloadUrl: String? = null
    private var lastDownloadEditor: SharedPreferences.Editor? = null
    private var lastDownloadPatch = 0
    private var networkError: Exception? = null
    private var backgroundDownload = false
    private var versionCheckingTask: Thread? = null

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var isDownloadInProgress = false
    private var slowSpeedStartTime: Long = 0
    private var downloadStartTime: Long = 0
    private var downloadSpeedHandler: Handler? = null
    private var timeoutCheckRunnable: Runnable? = null
    private var forceUpdateNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val slowSpeedThreshold = 1024L
    private val slowSpeedDuration = 5000L
    private val downloadTimeout = 1 * 60 * 1000L

    fun startVersionCheck() {
        versionCheckingTask = Thread {
            try {
                val safeContext = contextProvider() ?: return@Thread
                activityProvider()?.runOnUiThread {
                    viewsProvider().loadingView.visibility = View.VISIBLE
                }
                val versionFile = File(safeContext.filesDir.path, "version.json")
                if (versionFile.exists()) {
                    versionFile.delete()
                }
                versionFile.createNewFile()
                Utils.downloadWithoutTemp(
                    "https://static.payme.vn/frontend/miniapp-store/PayMEMiniAppVersion.json",
                    versionFile.absolutePath
                )
                val jsonString: String = File(versionFile.absolutePath).readText(Charsets.UTF_8)
                val jsonArray = JSONArray(jsonString)
                var version = ""
                var found: JSONObject? = null
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.getString("version") == BuildConfig.SDK_VERSION) {
                        found = item
                        version = item.getString("version")
                    }
                }
                val updateViewModel = updateViewModelProvider()
                if (found == null) {
                    updateViewModel.setDoneUpdate(true)
                    return@Thread
                }
                Log.d("PAYMELOG", "payme miniapp mode ${PayMEMiniApp.mode}")
                val sharedPreference = safeContext.getSharedPreferences(
                    "PAYME_NATIVE_UPDATE", Context.MODE_PRIVATE
                )
                val editor = sharedPreference.edit()
                val mode = found.optJSONObject(PayMEMiniApp.mode)
                if (mode == null) {
                    updateViewModel.setDoneUpdate(true)
                    return@Thread
                }
                val localMode = sharedPreference.getString("PAYME_MODE", "")
                if (PayMEMiniApp.mode != localMode) {
                    editor.putString("PAYME_MODE", PayMEMiniApp.mode)
                    editor.putInt("PAYME_PATCH", if (localMode == "") 0 else -1)
                    editor.apply()
                }
                val patch = mode.optInt("patch", 0)
                val latestMandatory = mode.optInt("latestMandatoryPatch", 0)
                val url = mode.optString("url")
                val localPatch = sharedPreference.getInt("PAYME_PATCH", 0)

                if (patch == 0 && latestMandatory == 0) {
                    Log.d(PayMEMiniApp.TAG, "default")
                    updateViewModel.setLoadDefaultSource(true)
                    updateViewModel.setDoneUpdate(true)
                    return@Thread
                }
                val localMandatory = localPatch < latestMandatory
                val payMEVersion = PayMEVersion(patch, version, localMandatory, url)
                if (payMEVersion.patch <= localPatch) {
                    Log.d(PayMEMiniApp.TAG, "do not update")
                    updateViewModel.setDoneUpdate(true)
                    return@Thread
                }
                if (!payMEVersion.mandatory) {
                    Log.d(PayMEMiniApp.TAG, "download ngầm")
                    backgroundDownload = true
                    updateViewModel.setDoneUpdate(true)
                    downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                    return@Thread
                }
                Log.d(PayMEMiniApp.TAG, "force update")
                activityProvider()?.runOnUiThread {
                    val context = contextProvider() ?: return@runOnUiThread
                    viewsProvider().updateLabelText.text =
                        context.getString(R.string.loading_data, BuildConfig.SDK_VERSION, patch)
                }
                updateViewModel.setShowUpdatingUI(true)
                updateViewModel.setIsForceUpdating(true)
                downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                Log.d(PayMEMiniApp.TAG, "downloaded source moi")
                updateViewModel.setIsForceUpdating(false)
                updateViewModel.setDoneUpdate(true)
            } catch (e: SSLException) {
                Log.d(PayMEMiniApp.TAG, "SSLException ex $e")
            } catch (e: Exception) {
                updateViewModelProvider().setDoneUpdate(true)
                Log.d(PayMEMiniApp.TAG, "thread ex $e")
            }
        }
        versionCheckingTask?.start()
    }

    fun registerConnectivityCallback() {
        val safeContext = contextProvider() ?: return
        unregisterForceUpdateNetworkCallback()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateViewModelProvider().setIsLostConnection(false)
            }

            override fun onLost(network: Network) {
                updateViewModelProvider().setIsLostConnection(true)
            }
        }

        val connectivityManager =
            safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        forceUpdateNetworkCallback = networkCallback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun onDoneUpdate(loadDefaultSource: Boolean, onLoadUrlReady: (String) -> Unit) {
        if (loadDefaultSource) {
            unzipDefaultSource()
        } else {
            unzip()
        }
        startServer()
        val currentLoadUrl = getLoadUrl()
        if (currentLoadUrl.isNotEmpty()) {
            activityProvider()?.runOnUiThread {
                onLoadUrlReady(currentLoadUrl)
            }
        }
    }

    fun setUpdatingUiVisible(visible: Boolean) {
        activityProvider()?.runOnUiThread {
            val views = viewsProvider()
            if (visible) {
                views.loadingView.visibility = View.GONE
                views.updatingView.visibility = View.VISIBLE
            } else {
                views.updatingView.visibility = View.GONE
            }
        }
    }

    fun onConnectionRestoredIfForceUpdating() {
        if (updateViewModelProvider().getIsForceUpdating().value == true) {
            if (versionCheckingTask == null || versionCheckingTask?.isAlive == false) {
                startVersionCheck()
            }
        }
    }

    fun restartLocalServerAfterWebResourceError() {
        try {
            stopServer()
            server = WebServer("localhost", port, wwwRoot)
            (server as WebServer).start()
            Log.d(PayMEMiniApp.TAG, "start server")
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "error ${e.message}")
        }
    }

    fun stopServer() {
        if (server != null) {
            Log.d(PayMEMiniApp.TAG, "Stopped Server")
            server!!.stop()
            server = null
        }
    }

    fun dispose() {
        unregisterForceUpdateNetworkCallback()
        unregisterNetworkCallback()
        stopSpeedAndTimeoutMonitoring()
        stopServer()
    }

    private fun unregisterForceUpdateNetworkCallback() {
        forceUpdateNetworkCallback?.let {
            try {
                val safeContext = contextProvider() ?: return
                val connectivityManager = safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error unregistering force update network callback: ${e.message}")
            }
            forceUpdateNetworkCallback = null
        }
    }

    private fun unzip() {
        val safeContext = contextProvider() ?: return
        val filesDir = safeContext.filesDir
        val sourceWeb = File("${filesDir.path}/update", "sdkWebapp3-main.zip")
        if (sourceWeb.exists() && !backgroundDownload && sourceWeb.length() > 0) {
            Log.d(PayMEMiniApp.TAG, "chay vo copy update")
            val wwwDirectory = File(filesDir.path, "www")
            wwwDirectory.delete()
            if (!wwwDirectory.exists()) {
                wwwDirectory.mkdir()
            }
            val unzipResult = Utils.unzipFile("${filesDir.path}/update/sdkWebapp3-main.zip", "${filesDir.path}/www")
            if (!unzipResult) {
                Log.e(PayMEMiniApp.TAG, "Failed to unzip update file. Closing miniapp")
                handleUnzipError()
                return
            }
            sourceWeb.delete()
            return
        }

        Log.d(PayMEMiniApp.TAG, "chay vo unzip source down san")

        val wwwDirectory = File(filesDir.path, "www")
        if (!wwwDirectory.exists()) {
            wwwDirectory.mkdir()
        }
        val unzipped = File("${filesDir.path}/www", "sdkWebapp3-main")
        val content = unzipped.listFiles()
        if (content == null || content.isEmpty()) {
            Utils.copyDir(safeContext, path = "www")
            val unzipResult = Utils.unzipFile("${filesDir.path}/www/sdkWebapp3-main.zip", "${filesDir.path}/www")
            if (!unzipResult) {
                Log.e(PayMEMiniApp.TAG, "Failed to unzip default source file. Closing miniapp")
                handleUnzipError()
            }
        }
    }

    private fun unzipDefaultSource() {
        val safeContext = contextProvider() ?: return
        val filesDir = safeContext.filesDir
        Log.d(PayMEMiniApp.TAG, "chay vo unzipDefaultSource")
        val wwwDirectory = File(filesDir.path, "www")
        if (!wwwDirectory.exists()) {
            wwwDirectory.mkdir()
        }
        Utils.copyDir(safeContext, path = "www")
        val unzipResult = Utils.unzipFile("${filesDir.path}/www/sdkWebapp3-main.zip", "${filesDir.path}/www")
        if (!unzipResult) {
            Log.e(PayMEMiniApp.TAG, "Failed to unzip default source file. Closing miniapp")
            handleUnzipError()
        }
    }

    private fun handleUnzipError() {
        val errorDescription = when (PayMEMiniApp.locale) {
            Locale.en -> "Failed to unzip source files. The app will be closed."
            Locale.vi -> "Không thể giải nén tệp nguồn. Ứng dụng sẽ được đóng."
        }

        val errorJson = JSONObject().apply {
            put("code", "UNZIP_FAILED")
            put("description", errorDescription)
            put("isCloseMiniApp", true)
        }.toString()

        activityProvider()?.runOnUiThread {
            try {
                returnError(errorJson)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error handling unzip failure: ${e.message}")
                closeMiniApp()
            }
        }
    }

    private fun startServer() {
        val safeContext = contextProvider() ?: return
        if (server != null) {
            return
        }
        port = Utils.findRandomOpenPort() ?: 4646
        wwwRoot = File("${safeContext.filesDir.path}/www", "sdkWebapp3-main")
        if (getLoadUrl().contains("http://localhost") || getLoadUrl().isEmpty()) {
            setLoadUrl("http://localhost:$port/")
        }

        try {
            server = WebServer("localhost", port, wwwRoot)
            (server as WebServer).start()
            Log.d(PayMEMiniApp.TAG, "start server with port $port")
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "error start server ${e.message}")
        }
    }

    private fun getConnectionType(): String {
        return when (NetworkMonitor.shared.connectionType.value) {
            NetworkMonitor.ConnectionType.WIFI -> "WiFi"
            NetworkMonitor.ConnectionType.CELLULAR -> "Mobile Data"
            NetworkMonitor.ConnectionType.ETHERNET -> "Ethernet"
            else -> "Unknown"
        }
    }

    private fun updateConnectionTypeDisplay() {
        val context = contextProvider() ?: return
        val connectionType = NetworkMonitor.shared.connectionType.value
        val isConnected = NetworkMonitor.shared.isConnected.value

        val iconResId = when (connectionType) {
            NetworkMonitor.ConnectionType.WIFI -> R.drawable.ic_wifi
            NetworkMonitor.ConnectionType.CELLULAR -> R.drawable.ic_network_cell
            NetworkMonitor.ConnectionType.ETHERNET -> R.drawable.ic_network
            else -> R.drawable.ic_network_unknown
        }

        val isError = networkError != null
        val isDownloadSlowOrInterrupted = slowSpeedStartTime > 0L

        val colorResId = when {
            isError -> R.color.warning
            isDownloadSlowOrInterrupted -> R.color.warning
            !isConnected -> R.color.warning
            else -> R.color.grey_text
        }

        val connectionText = when {
            isError -> networkError?.let {
                when (it) {
                    is UnknownHostException -> LocaleUtils.ErrorMessages.serverUnavailable()
                    is SocketTimeoutException -> LocaleUtils.ErrorMessages.connectionTimeout()
                    is ConnectException -> LocaleUtils.ErrorMessages.serverConnectionFailed()
                    is SocketException -> LocaleUtils.ErrorMessages.networkConnectionLost()
                    is SSLException -> LocaleUtils.ErrorMessages.secureConnectionFailed()
                    is IOException -> LocaleUtils.ErrorMessages.networkError()
                    else -> it.message ?: LocaleUtils.ErrorMessages.unknownError()
                }
            } ?: LocaleUtils.ErrorMessages.unknownError()
            isDownloadSlowOrInterrupted -> LocaleUtils.DownloadMessages.slowNetworkSpeed()
            !isConnected -> LocaleUtils.ErrorMessages.noNetworkConnection()
            else -> getConnectionType()
        }

        val views = viewsProvider()
        views.connectionTypeIcon.setImageResource(iconResId)
        views.connectionTypeIcon.setColorFilter(ContextCompat.getColor(context, colorResId))
        views.connectionTypeText.text = connectionText
        views.connectionTypeText.setTextColor(ContextCompat.getColor(context, colorResId))
        views.connectionStatusContainer.visibility = View.VISIBLE
    }

    private fun isNetworkConnected(): Boolean {
        val safeContext = contextProvider() ?: return false
        val connectivityManager = safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()

        val safeContext = contextProvider() ?: return
        val connectivityManager = safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.e(PayMEMiniApp.TAG, "Network connectivity lost during download")

                if (isDownloadInProgress) {
                    activityProvider()?.runOnUiThread {
                        handleNetworkDisconnection("Network connection lost")
                    }
                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e(PayMEMiniApp.TAG, "Network unavailable during download")

                if (isDownloadInProgress) {
                    activityProvider()?.runOnUiThread {
                        handleNetworkDisconnection("Network unavailable")
                    }
                }
            }
        }

        activeNetworkCallback = networkCallback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun unregisterNetworkCallback() {
        activeNetworkCallback?.let {
            try {
                val safeContext = contextProvider() ?: return
                val connectivityManager = safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error unregistering network callback: ${e.message}")
            }
            activeNetworkCallback = null
        }
    }

    private fun startSpeedAndTimeoutMonitoring() {
        slowSpeedStartTime = 0
        downloadStartTime = System.currentTimeMillis()
        stopSpeedAndTimeoutMonitoring()
        downloadSpeedHandler = Handler(Looper.getMainLooper())
        timeoutCheckRunnable = Runnable {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - downloadStartTime
            val remainingTime = downloadTimeout - elapsedTime

            if (isDownloadInProgress) {
                val elapsedSeconds = elapsedTime / 1000
                val remainingSeconds = remainingTime / 1000
                val timeRemainingMessage = LocaleUtils.DownloadMessages.downloadTimeRemaining(remainingSeconds)

                Log.d(PayMEMiniApp.TAG, "$timeRemainingMessage (${elapsedSeconds}s đã trôi qua)")

                if (remainingTime in 1..29999) {
                    Log.w(PayMEMiniApp.TAG, timeRemainingMessage)
                }

                if (elapsedTime > downloadTimeout) {
                    val timeoutMessage = when (PayMEMiniApp.locale) {
                        Locale.en -> "Download timeout after ${elapsedTime / 1000}s - Cancelling download"
                        Locale.vi -> "Tải xuống quá thời gian sau ${elapsedTime / 1000}s - Hủy tải xuống"
                    }
                    Log.e(PayMEMiniApp.TAG, timeoutMessage)
                    val timeoutErrorMessage = LocaleUtils.ErrorMessages.downloadTimeout()
                    handleDownloadIssue(timeoutErrorMessage)
                } else {
                    timeoutCheckRunnable?.let {
                        downloadSpeedHandler?.postDelayed(it, 10000)
                    }
                }
            }
        }

        timeoutCheckRunnable?.let {
            downloadSpeedHandler?.postDelayed(it, 10000)
        }
    }

    private fun stopSpeedAndTimeoutMonitoring() {
        timeoutCheckRunnable?.let { downloadSpeedHandler?.removeCallbacks(it) }
        downloadSpeedHandler = null
        timeoutCheckRunnable = null
    }

    private fun checkDownloadSpeed(speed: Long) {
        if (speed < slowSpeedThreshold && isDownloadInProgress) {
            val currentTime = System.currentTimeMillis()
            if (slowSpeedStartTime == 0L) {
                slowSpeedStartTime = currentTime
                val formattedSpeed = Utils.formatSpeed(speed)
                val slowSpeedMessage = when (PayMEMiniApp.locale) {
                    Locale.en -> "Slow download speed detected: ${formattedSpeed}/s"
                    Locale.vi -> "Phát hiện tốc độ tải xuống chậm: ${formattedSpeed}/s"
                }
                Log.w(PayMEMiniApp.TAG, slowSpeedMessage)
                activityProvider()?.runOnUiThread {
                    updateConnectionTypeDisplay()
                }
            } else {
                val slowDuration = currentTime - slowSpeedStartTime
                if (slowDuration > slowSpeedDuration) {
                    val formattedSpeed = Utils.formatSpeed(speed)
                    Log.e(PayMEMiniApp.TAG, "Download speed too slow (${formattedSpeed}/s) for ${slowDuration / 1000}s - aborting download")
                    val errorMessage = LocaleUtils.ErrorMessages.downloadFailed("Tốc độ quá chậm")
                    handleDownloadIssue(errorMessage)
                }
            }
        } else {
            if (slowSpeedStartTime > 0L) {
                slowSpeedStartTime = 0L
                activityProvider()?.runOnUiThread {
                    updateConnectionTypeDisplay()
                }
            }
        }
    }

    private fun handleDownloadIssue(errorMessage: String) {
        if (!isDownloadInProgress) return
        stopSpeedAndTimeoutMonitoring()
        isDownloadInProgress = false
        showDownloadError(
            errorMessage = errorMessage,
            logPrefix = "Current download parameters - URL: $lastDownloadUrl, Patch: $lastDownloadPatch"
        )
    }

    private fun handleNetworkDisconnection(errorMessage: String) {
        isDownloadInProgress = false
        stopSpeedAndTimeoutMonitoring()
        showDownloadError(
            errorMessage = errorMessage,
            logPrefix = "Network disconnection - Current download parameters - URL: $lastDownloadUrl, Patch: $lastDownloadPatch"
        )
    }

    private fun showDownloadError(errorMessage: String, logPrefix: String) {
        val context = contextProvider() ?: return
        val views = viewsProvider()
        views.errorContainer.visibility = View.VISIBLE
        views.errorMessage.text = context.getString(R.string.download_failed_with_reason, errorMessage)

        Log.d(PayMEMiniApp.TAG, logPrefix)

        val localUrl = lastDownloadUrl
        val localEditor = lastDownloadEditor
        val localPatch = lastDownloadPatch

        views.retryButton.text = context.getString(R.string.retry)
        views.retryButton.setOnClickListener {
            views.errorMessage.text = context.getString(R.string.wait)
            if (localUrl != null && localEditor != null) {
                if (isNetworkConnected()) {
                    Log.d(PayMEMiniApp.TAG, "Retrying download with - URL: $localUrl, Patch: $localPatch")
                    views.errorContainer.visibility = View.GONE

                    Handler(Looper.getMainLooper()).postDelayed({
                        downloadSourceWeb(localUrl, localEditor, localPatch)
                    }, 200)
                } else {
                    Log.e(PayMEMiniApp.TAG, "Cannot retry - No network connection")
                    views.errorMessage.text = context.getString(
                        R.string.download_failed_with_reason,
                        context.getString(R.string.no_network_connection)
                    )
                }
            } else {
                Log.e(PayMEMiniApp.TAG, "Cannot retry - Missing parameters")
                views.errorMessage.text = context.getString(
                    R.string.download_failed_wait,
                    context.getString(R.string.wait)
                )
            }
        }
    }

    private fun downloadSourceWeb(url: String?, editor: SharedPreferences.Editor, patch: Int) {
        val safeContext = contextProvider() ?: return
        lastDownloadUrl = url
        lastDownloadEditor = editor
        lastDownloadPatch = patch

        Log.d(PayMEMiniApp.TAG, "Starting download with URL: $url, Patch: $patch")
        val filesDir = safeContext.filesDir
        val updateDirectory = File(filesDir.path, "update")
        if (!updateDirectory.exists()) {
            updateDirectory.mkdir()
        }
        val sourceWeb = File("${filesDir.path}/update", "sdkWebapp3-main.zip")
        if (sourceWeb.exists()) {
            sourceWeb.delete()
        }
        sourceWeb.createNewFile()

        if (!isNetworkConnected()) {
            activityProvider()?.runOnUiThread {
                val message = LocaleUtils.ErrorMessages.noNetworkConnection()
                handleNetworkDisconnection(message)
            }
            return
        }

        activityProvider()?.runOnUiThread {
            viewsProvider().updateLabelText.text =
                safeContext.getString(R.string.loading_data, BuildConfig.SDK_VERSION, patch)
        }

        registerNetworkCallback()
        startSpeedAndTimeoutMonitoring()

        activityProvider()?.runOnUiThread {
            viewsProvider().errorContainer.visibility = View.GONE
            updateConnectionTypeDisplay()
        }

        url?.let {
            try {
                isDownloadInProgress = true
                networkError = null

                Utils.download(
                    safeContext, it, sourceWeb.absolutePath
                ) { totalBytesCopied, length, speed ->
                    checkDownloadSpeed(speed)

                    val progressValuePercent = (totalBytesCopied * 100 / length).toInt()

                    activityProvider()?.runOnUiThread {
                        val views = viewsProvider()
                        views.progressBar.progress = progressValuePercent

                        val downloadedSizeFormatted = Utils.formatFileSize(totalBytesCopied)
                        val totalSizeFormatted = Utils.formatFileSize(length.toLong())
                        val speedFormatted = Utils.formatSpeed(speed)

                        val percentFormatted = safeContext.getString(
                            R.string.download_decimal_percent,
                            progressValuePercent.toFloat()
                        )
                        val downloadInfo = safeContext.getString(
                            R.string.download_progress_detail,
                            downloadedSizeFormatted,
                            totalSizeFormatted,
                            speedFormatted,
                            percentFormatted
                        )
                        views.downloadDetailsText.text = downloadInfo

                        updateConnectionTypeDisplay()

                        val layoutParams = views.lottieView.layoutParams as LinearLayout.LayoutParams
                        layoutParams.leftMargin =
                            progressValuePercent * (views.lottieContainerView.width - views.lottieView.width) / 100
                        layoutParams.topMargin = 0
                        layoutParams.rightMargin = 0
                        layoutParams.bottomMargin = 0
                        views.lottieView.requestLayout()
                    }
                }

                isDownloadInProgress = false
                unregisterNetworkCallback()
                stopSpeedAndTimeoutMonitoring()
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Download error: ${e.message}")

                isDownloadInProgress = false
                stopSpeedAndTimeoutMonitoring()
                networkError = e

                val errorMsg = when (e) {
                    is UnknownHostException -> LocaleUtils.ErrorMessages.serverUnavailable()
                    is SocketTimeoutException -> LocaleUtils.ErrorMessages.connectionTimeout()
                    is ConnectException -> LocaleUtils.ErrorMessages.serverConnectionFailed()
                    is SocketException -> LocaleUtils.ErrorMessages.networkConnectionLost()
                    is SSLException -> LocaleUtils.ErrorMessages.secureConnectionFailed()
                    is IOException -> LocaleUtils.ErrorMessages.networkError()
                    else -> e.message ?: LocaleUtils.ErrorMessages.unknownError()
                }

                activityProvider()?.runOnUiThread {
                    handleNetworkDisconnection(errorMsg)
                }

                return@let
            }

            Log.d(PayMEMiniApp.TAG, "source web length ${sourceWeb.length()}")
            if (sourceWeb.length() > 0) {
                editor.putInt("PAYME_PATCH", patch)
                editor.apply()
            } else {
                if (!backgroundDownload) {
                    sourceWeb.delete()
                    updateViewModelProvider().setDoneUpdate(true)
                }
            }
            backgroundDownload = false
        }

        if (isDownloadInProgress) {
            isDownloadInProgress = false
            unregisterNetworkCallback()
            stopSpeedAndTimeoutMonitoring()
        }
    }
}

package com.payme.sdk.ui.miniapp

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.Locale
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.ui.miniapp.source.ConnectivityMonitor
import com.payme.sdk.ui.miniapp.source.LocalhostServerController
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.SourceDownloadException
import com.payme.sdk.ui.miniapp.source.SourceDownloadFailureReason
import com.payme.sdk.ui.miniapp.source.SourceDownloader
import com.payme.sdk.ui.miniapp.source.SourceInstallFailureReason
import com.payme.sdk.ui.miniapp.source.SourceInstaller
import com.payme.sdk.ui.miniapp.source.StoredUpdateState
import com.payme.sdk.ui.miniapp.source.UpdateDecision
import com.payme.sdk.ui.miniapp.source.UpdateDecisionResolver
import com.payme.sdk.ui.miniapp.source.VersionRepository
import com.payme.sdk.utils.LocaleUtils
import com.payme.sdk.utils.NetworkMonitor
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import org.json.JSONObject
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
    private val versionRepository = VersionRepository()
    private val updateDecisionResolver = UpdateDecisionResolver()
    private val localhostServerController = LocalhostServerController(getLoadUrl, setLoadUrl)
    private val connectivityMonitor = ConnectivityMonitor()
    private val uiRenderer = MiniAppUpdateUiRenderer(contextProvider, activityProvider, viewsProvider)
    private val downloadMonitor = MiniAppDownloadMonitor(
        isDownloadInProgressProvider = { isDownloadInProgress },
        currentLocaleProvider = { currentLocale() },
        onDownloadIssue = { handleDownloadIssue(it) },
        onConnectionStatusChanged = { updateConnectionTypeDisplay() }
    )

    private var lastDownloadUrl: String? = null
    private var lastDownloadEditor: SharedPreferences.Editor? = null
    private var lastDownloadPatch = 0
    private var networkError: Exception? = null
    private var backgroundDownload = false
    private var pendingMandatoryUpdate = false
    private var downloadedUpdateReady = false
    private var versionCheckingTask: Thread? = null

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var isDownloadInProgress = false
    private var forceUpdateNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun startVersionCheck() {
        versionCheckingTask = Thread {
            try {
                val safeContext = contextProvider() ?: return@Thread
                uiRenderer.setLoadingVisible()
                val versionLookup = versionRepository.fetchVersionForCurrentSdk(safeContext)
                val updateViewModel = updateViewModelProvider()
                if (versionLookup == null || versionLookup.modeJson == null) {
                    updateViewModel.setDoneUpdate(true)
                    return@Thread
                }
                Log.d("PAYMELOG", "payme miniapp mode ${currentMode()}")
                val sharedPreference = safeContext.getSharedPreferences(
                    MiniAppSourceConstants.PREFERENCES_NAME, Context.MODE_PRIVATE
                )
                val editor = sharedPreference.edit()
                val decisionResult = updateDecisionResolver.resolve(
                    versionLookup = versionLookup,
                    currentMode = currentMode(),
                    storedState = StoredUpdateState(
                        mode = sharedPreference.getString(MiniAppSourceConstants.PREF_MODE, "").orEmpty(),
                        patch = sharedPreference.getInt(MiniAppSourceConstants.PREF_PATCH, 0)
                    )
                )
                decisionResult.mutation?.let {
                    editor.putString(MiniAppSourceConstants.PREF_MODE, it.mode)
                    editor.putInt(MiniAppSourceConstants.PREF_PATCH, it.patch)
                    editor.apply()
                }
                when (val decision = decisionResult.decision) {
                    UpdateDecision.LoadDefaultSource -> {
                        Log.d(PayMEMiniApp.TAG, "default")
                        pendingMandatoryUpdate = false
                        downloadedUpdateReady = false
                        updateViewModel.setLoadDefaultSource(true)
                        updateViewModel.setDoneUpdate(true)
                        return@Thread
                    }

                    UpdateDecision.NoUpdate -> {
                        Log.d(PayMEMiniApp.TAG, "do not update")
                        pendingMandatoryUpdate = false
                        downloadedUpdateReady = false
                        updateViewModel.setDoneUpdate(true)
                        return@Thread
                    }

                    is UpdateDecision.BackgroundUpdate -> {
                        Log.d(PayMEMiniApp.TAG, "download ngầm")
                        pendingMandatoryUpdate = false
                        downloadedUpdateReady = false
                        backgroundDownload = true
                        updateViewModel.setDoneUpdate(true)
                        downloadSourceWeb(decision.url, editor, decision.patch)
                        return@Thread
                    }

                    is UpdateDecision.MandatoryUpdate -> {
                        Log.d(PayMEMiniApp.TAG, "force update")
                        pendingMandatoryUpdate = true
                        downloadedUpdateReady = false
                        uiRenderer.setUpdateLabel(decision.patch)
                        updateViewModel.setShowUpdatingUI(true)
                        updateViewModel.setIsForceUpdating(true)
                        if (!downloadSourceWeb(decision.url, editor, decision.patch)) {
                            return@Thread
                        }
                        Log.d(PayMEMiniApp.TAG, "downloaded source moi")
                        updateViewModel.setIsForceUpdating(false)
                        updateViewModel.setDoneUpdate(true)
                    }
                }
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

        forceUpdateNetworkCallback = networkCallback
        connectivityMonitor.registerDefaultNetworkCallback(safeContext, networkCallback)
    }

    fun onDoneUpdate(loadDefaultSource: Boolean, onLoadUrlReady: (String) -> Unit) {
        val sourceReady = if (loadDefaultSource) {
            unzipDefaultSource()
        } else {
            unzip()
        }
        if (!sourceReady) {
            return
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
        uiRenderer.setUpdatingUiVisible(visible)
    }

    fun onConnectionRestoredIfForceUpdating() {
        if (updateViewModelProvider().getIsForceUpdating().value == true) {
            if (versionCheckingTask == null || versionCheckingTask?.isAlive == false) {
                startVersionCheck()
            }
        }
    }

    fun restartLocalServerAfterWebResourceError() {
        val safeContext = contextProvider() ?: return
        val root = localhostServerController.wwwRoot ?: SourceInstaller(safeContext).wwwRoot
        localhostServerController.restart(safeContext, root)
    }

    fun stopServer() {
        localhostServerController.stop()
    }

    fun dispose() {
        unregisterForceUpdateNetworkCallback()
        unregisterNetworkCallback()
        downloadMonitor.stop()
        stopServer()
    }

    private fun unregisterForceUpdateNetworkCallback() {
        forceUpdateNetworkCallback?.let {
            try {
                val safeContext = contextProvider() ?: return
                connectivityMonitor.unregisterDefaultNetworkCallback(safeContext, it)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error unregistering force update network callback: ${e.message}")
            }
            forceUpdateNetworkCallback = null
        }
    }

    private fun unzip(): Boolean {
        val safeContext = contextProvider() ?: return false
        val sourceInstaller = SourceInstaller(safeContext)
        val sourceWeb = sourceInstaller.updateZip
        if (sourceWeb.exists() && !backgroundDownload && sourceWeb.length() > 0) {
            Log.d(PayMEMiniApp.TAG, "chay vo copy update")
            val installResult = sourceInstaller.installUpdatedSourceDetailed()
            if (!installResult.success) {
                if (installResult.failureReason == SourceInstallFailureReason.INSUFFICIENT_STORAGE) {
                    Log.e(PayMEMiniApp.TAG, "Not enough storage to install update source")
                    return handleInstallStorageFailure(safeContext, sourceInstaller)
                }
                Log.e(PayMEMiniApp.TAG, "Failed to unzip update file. Closing miniapp")
                handleUnzipError()
                return false
            }
            persistInstalledPatchIfNeeded()
            backgroundDownload = false
            pendingMandatoryUpdate = false
            return true
        }

        Log.d(PayMEMiniApp.TAG, "chay vo unzip source down san")
        val sourceResult = sourceInstaller.ensureExistingOrBundledSourceDetailed()
        if (!sourceResult.success) {
            if (sourceResult.failureReason == SourceInstallFailureReason.INSUFFICIENT_STORAGE) {
                Log.e(PayMEMiniApp.TAG, "Not enough storage to install bundled source")
                return handleInstallStorageFailure(safeContext, sourceInstaller)
            }
            Log.e(PayMEMiniApp.TAG, "Failed to unzip default source file. Closing miniapp")
            handleUnzipError()
            return false
        }
        return true
    }

    private fun unzipDefaultSource(): Boolean {
        val safeContext = contextProvider() ?: return false
        Log.d(PayMEMiniApp.TAG, "chay vo unzipDefaultSource")
        val sourceInstaller = SourceInstaller(safeContext)
        val installResult = sourceInstaller.installBundledSourceDetailed()
        if (!installResult.success) {
            if (installResult.failureReason == SourceInstallFailureReason.INSUFFICIENT_STORAGE) {
                Log.e(PayMEMiniApp.TAG, "Not enough storage to install bundled source")
                return handleInstallStorageFailure(safeContext, sourceInstaller)
            }
            Log.e(PayMEMiniApp.TAG, "Failed to unzip default source file. Closing miniapp")
            handleUnzipError()
            return false
        }
        return true
    }

    private fun handleInstallStorageFailure(
        context: Context,
        sourceInstaller: SourceInstaller
    ): Boolean {
        sourceInstaller.cleanUpdateArtifacts()
        downloadedUpdateReady = false

        if (pendingMandatoryUpdate) {
            updateViewModelProvider().setIsForceUpdating(true)
            activityProvider()?.runOnUiThread {
                showDownloadError(
                    errorMessage = context.getString(R.string.insufficient_storage),
                    logPrefix = "Insufficient storage - Current download parameters - URL: $lastDownloadUrl, Patch: $lastDownloadPatch"
                )
            }
            return false
        }

        backgroundDownload = false
        if (SourceInstaller.findWebRoot(sourceInstaller.wwwDirectory) != null) {
            Log.w(PayMEMiniApp.TAG, "Skipping optional source update because storage is insufficient")
            return true
        }

        handleStorageErrorAndClose(context)
        return false
    }

    private fun persistInstalledPatchIfNeeded() {
        if (!downloadedUpdateReady) {
            return
        }

        val editor = lastDownloadEditor ?: return
        editor.putInt(MiniAppSourceConstants.PREF_PATCH, lastDownloadPatch)
        editor.apply()
        downloadedUpdateReady = false
    }

    private fun handleUnzipError() {
        val errorDescription = when (currentLocale()) {
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

    private fun handleStorageErrorAndClose(context: Context) {
        val errorJson = JSONObject().apply {
            put("code", "INSUFFICIENT_STORAGE")
            put("description", context.getString(R.string.insufficient_storage))
            put("isCloseMiniApp", true)
        }.toString()

        activityProvider()?.runOnUiThread {
            try {
                returnError(errorJson)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error handling storage failure: ${e.message}")
                closeMiniApp()
            }
        }
    }

    private fun startServer() {
        val safeContext = contextProvider() ?: return
        val root = SourceInstaller(safeContext).wwwRoot
        localhostServerController.start(safeContext, root)
    }

    private fun currentMode(): String {
        return PayMERuntime.requireConfig().mode
    }

    private fun currentLocale(): Locale {
        return PayMERuntime.requireConfig().locale
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
        activityProvider()?.runOnUiThread {
            uiRenderer.updateConnectionStatus(
                error = networkError,
                isSlowOrInterrupted = downloadMonitor.isSlowOrInterrupted,
                isConnected = NetworkMonitor.shared.isConnected.value,
                connectionType = getConnectionType()
            )
        }
    }

    private fun isNetworkConnected(): Boolean {
        val safeContext = contextProvider() ?: return false
        return connectivityMonitor.isConnected(safeContext)
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()

        val safeContext = contextProvider() ?: return
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
        connectivityMonitor.registerDefaultNetworkCallback(safeContext, networkCallback)
    }

    private fun unregisterNetworkCallback() {
        activeNetworkCallback?.let {
            try {
                val safeContext = contextProvider() ?: return
                connectivityMonitor.unregisterDefaultNetworkCallback(safeContext, it)
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Error unregistering network callback: ${e.message}")
            }
            activeNetworkCallback = null
        }
    }

    private fun handleDownloadIssue(errorMessage: String) {
        if (!isDownloadInProgress) return
        downloadMonitor.stop()
        isDownloadInProgress = false
        showDownloadError(
            errorMessage = errorMessage,
            logPrefix = "Current download parameters - URL: $lastDownloadUrl, Patch: $lastDownloadPatch"
        )
    }

    private fun handleNetworkDisconnection(errorMessage: String) {
        isDownloadInProgress = false
        downloadMonitor.stop()
        showDownloadError(
            errorMessage = errorMessage,
            logPrefix = "Network disconnection - Current download parameters - URL: $lastDownloadUrl, Patch: $lastDownloadPatch"
        )
    }

    private fun showDownloadError(errorMessage: String, logPrefix: String) {
        val localUrl = lastDownloadUrl
        val localEditor = lastDownloadEditor
        val localPatch = lastDownloadPatch

        uiRenderer.showDownloadError(
            errorMessage = errorMessage,
            logPrefix = logPrefix,
            hasRetryContext = localUrl != null && localEditor != null,
            canRetry = { isNetworkConnected() },
            onRetry = {
                if (localUrl != null && localEditor != null) {
                    Log.d(PayMEMiniApp.TAG, "Retrying download with - URL: $localUrl, Patch: $localPatch")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (downloadSourceWeb(localUrl, localEditor, localPatch) && pendingMandatoryUpdate) {
                            updateViewModelProvider().setIsForceUpdating(false)
                            updateViewModelProvider().setDoneUpdate(true)
                        }
                    }, 200)
                }
            }
        )
    }

    private fun downloadSourceWeb(url: String?, editor: SharedPreferences.Editor, patch: Int): Boolean {
        val safeContext = contextProvider() ?: return false
        lastDownloadUrl = url
        lastDownloadEditor = editor
        lastDownloadPatch = patch
        downloadedUpdateReady = false

        Log.d(PayMEMiniApp.TAG, "Starting download with URL: $url, Patch: $patch")
        val sourceDownloader = SourceDownloader(safeContext)
        val sourceWeb = sourceDownloader.resetDestination()

        if (!isNetworkConnected()) {
            activityProvider()?.runOnUiThread {
                val message = LocaleUtils.ErrorMessages.noNetworkConnection()
                handleNetworkDisconnection(message)
            }
            return false
        }

        uiRenderer.setUpdateLabel(patch)

        registerNetworkCallback()
        downloadMonitor.start()

        uiRenderer.hideDownloadErrorAndUpdateConnection(
            error = networkError,
            isSlowOrInterrupted = downloadMonitor.isSlowOrInterrupted,
            isConnected = NetworkMonitor.shared.isConnected.value,
            connectionType = getConnectionType()
        )

        var didDownloadUpdate = false
        url?.let {
            try {
                isDownloadInProgress = true
                networkError = null

                val downloadResult = sourceDownloader.download(it, sourceWeb) { totalBytesCopied, length, speed ->
                    downloadMonitor.checkSpeed(speed)

                    val progressValuePercent =
                        if (length > 0) (totalBytesCopied * 100 / length).toInt() else 0

                    uiRenderer.updateDownloadProgress(
                        totalBytesCopied = totalBytesCopied,
                        length = length,
                        speed = speed,
                        progressValuePercent = progressValuePercent
                    )
                    updateConnectionTypeDisplay()
                }

                isDownloadInProgress = false
                unregisterNetworkCallback()
                downloadMonitor.stop()

                Log.d(PayMEMiniApp.TAG, "source web length ${downloadResult.bytesCopied}")
                if (downloadResult.bytesCopied > 0) {
                    downloadedUpdateReady = true
                    didDownloadUpdate = true
                }
            } catch (e: Exception) {
                Log.e(PayMEMiniApp.TAG, "Download error: ${e.message}")

                isDownloadInProgress = false
                unregisterNetworkCallback()
                downloadMonitor.stop()
                networkError = e
                downloadedUpdateReady = false

                if (isInsufficientStorageDownload(e)) {
                    SourceInstaller(safeContext).cleanUpdateArtifacts()
                    if (backgroundDownload && !pendingMandatoryUpdate) {
                        Log.w(PayMEMiniApp.TAG, "Skipping background source update because storage is insufficient")
                        backgroundDownload = false
                        networkError = null
                        return false
                    }
                }

                val errorMsg = MiniAppUpdateErrorMapper.downloadErrorMessage(safeContext, e)

                activityProvider()?.runOnUiThread {
                    handleNetworkDisconnection(errorMsg)
                }

                return false
            }
            backgroundDownload = false
        }

        if (isDownloadInProgress) {
            isDownloadInProgress = false
            unregisterNetworkCallback()
            downloadMonitor.stop()
        }
        return didDownloadUpdate
    }

    private fun isInsufficientStorageDownload(error: Exception): Boolean {
        return error is SourceDownloadException &&
            error.reason == SourceDownloadFailureReason.INSUFFICIENT_STORAGE
    }
}

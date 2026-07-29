package com.payme.sdk.ui.miniapp

import android.content.Context
import com.payme.sdk.R
import com.payme.sdk.ui.miniapp.source.SourceDownloadException
import com.payme.sdk.ui.miniapp.source.SourceDownloadFailureReason
import com.payme.sdk.utils.LocaleUtils
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal object MiniAppUpdateErrorMapper {
    fun connectionText(
        error: Exception?,
        isSlowOrInterrupted: Boolean,
        isConnected: Boolean,
        connectionType: String
    ): String {
        return when {
            error != null -> exceptionMessage(error)
            isSlowOrInterrupted -> LocaleUtils.DownloadMessages.slowNetworkSpeed()
            !isConnected -> LocaleUtils.ErrorMessages.noNetworkConnection()
            else -> connectionType
        }
    }

    fun downloadErrorMessage(context: Context, error: Exception): String {
        return when (error) {
            is SourceDownloadException -> sourceDownloadErrorMessage(context, error)
            else -> exceptionMessage(error)
        }
    }

    private fun sourceDownloadErrorMessage(context: Context, error: SourceDownloadException): String {
        return when (error.reason) {
            SourceDownloadFailureReason.INVALID_CONNECTION -> context.getString(R.string.cannot_connect_to_server)
            SourceDownloadFailureReason.HTTP_ERROR -> {
                if (error.statusCode == HTTP_STATUS_NOT_FOUND) {
                    context.getString(R.string.error_404)
                } else {
                    context.getString(R.string.server_error_try_again)
                }
            }
            SourceDownloadFailureReason.INSUFFICIENT_STORAGE -> context.getString(R.string.insufficient_storage)
            SourceDownloadFailureReason.EMPTY_BODY,
            SourceDownloadFailureReason.INCOMPLETE_BODY -> context.getString(R.string.download_failed)
            SourceDownloadFailureReason.IO_ERROR -> exceptionMessage(error.cause)
        }
    }

    private fun exceptionMessage(error: Throwable?): String {
        return when (error) {
            is UnknownHostException -> LocaleUtils.ErrorMessages.serverUnavailable()
            is SocketTimeoutException -> LocaleUtils.ErrorMessages.connectionTimeout()
            is ConnectException -> LocaleUtils.ErrorMessages.serverConnectionFailed()
            is SocketException -> LocaleUtils.ErrorMessages.networkConnectionLost()
            is SSLException -> LocaleUtils.ErrorMessages.secureConnectionFailed()
            is IOException -> LocaleUtils.ErrorMessages.networkError()
            else -> error?.message ?: LocaleUtils.ErrorMessages.unknownError()
        }
    }

    private const val HTTP_STATUS_NOT_FOUND = 404
}

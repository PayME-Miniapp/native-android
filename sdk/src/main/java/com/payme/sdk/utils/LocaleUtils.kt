package com.payme.sdk.utils

import com.payme.sdk.models.Locale
import com.payme.sdk.runtime.PayMERuntime

/** Lớp tiện ích để xử lý các thông báo đa ngôn ngữ */
object LocaleUtils {

    /**
     * Lấy thông báo theo locale hiện tại
     * @param viMessage Thông báo tiếng Việt
     * @param enMessage Thông báo tiếng Anh
     * @return Thông báo tương ứng với ngôn ngữ hiện tại
     */
    fun getLocalizedMessage(viMessage: String, enMessage: String): String {
        return when (currentLocale()) {
            Locale.en -> enMessage
            Locale.vi -> viMessage
        }
    }

    /** Định dạng tin nhắn với các tham số */
    fun formatMessage(viFormat: String, enFormat: String, vararg args: Any): String {
        val format =
                when (currentLocale()) {
                    Locale.en -> enFormat
                    Locale.vi -> viFormat
                }
        return String.format(format, *args)
    }

    private fun currentLocale(): Locale {
        return PayMERuntime.requireConfig().locale
    }

    /** Các thông báo liên quan đến tải xuống */
    object DownloadMessages {
        // Tiến trình tải xuống: {downloadedSize} / {totalSize} ({speed}/s)
        fun downloadProgress(downloadedSize: String, totalSize: String, speed: String): String {
            val viFormat = "%s / %s (%s/s)"
            val enFormat = "%s / %s (%s/s)"
            return formatMessage(viFormat, enFormat, downloadedSize, totalSize, speed)
        }

        // Đang tải xuống...
        fun downloading(): String {
            val viMessage = "Đang tải xuống..."
            val enMessage = "Downloading..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Đang thử lại tải xuống...
        fun retryingDownload(): String {
            val viMessage = "Đang thử lại tải xuống..."
            val enMessage = "Retrying download..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Loại kết nối: {type}
        fun connectionType(type: String): String {
            val viFormat = "Kết nối: %s"
            val enFormat = "Connection: %s"
            return formatMessage(viFormat, enFormat, type)
        }

        // Tải xuống hoàn tất
        fun downloadComplete(): String {
            val viMessage = "Tải xuống hoàn tất"
            val enMessage = "Download complete"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Đang tải mã nguồn...
        fun loadingSource(): String {
            val viMessage = "Đang tải mã nguồn..."
            val enMessage = "Loading source..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Tốc độ mạng chậm
        fun slowNetworkSpeed(): String {
            val viMessage = "Tốc độ mạng chậm"
            val enMessage = "Slow network"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Thời gian tải xuống còn lại
        fun downloadTimeRemaining(seconds: Long): String {
            return if (seconds > 60) {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val viFormat = "Còn lại: %d phút %d giây"
                val enFormat = "Time remaining: %d min %d sec"
                formatMessage(viFormat, enFormat, minutes, remainingSeconds)
            } else {
                val viFormat = "Còn lại: %d giây"
                val enFormat = "Time remaining: %d sec"
                formatMessage(viFormat, enFormat, seconds)
            }
        }
    }

    /** Các thông báo lỗi thường gặp */
    object ErrorMessages {
        // Lỗi không tìm thấy tài nguyên
        fun resourceNotFound(path: String): String {
            val viFormat = "Lỗi 404: Không tìm thấy %s"
            val enFormat = "Error 404: Not found %s"
            return formatMessage(viFormat, enFormat, path)
        }

        // Lỗi giải nén
        fun unzipFailed(): String {
            val viMessage = "Không thể giải nén"
            val enMessage = "Extract failed"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi không có kết nối mạng
        fun noNetworkConnection(): String {
            val viMessage = "Không có kết nối mạng"
            val enMessage = "No network"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi tải xuống
        fun downloadFailed(reason: String? = null): String {
            val viBase = "Tải thất bại"
            val enBase = "Download failed"
            return if (reason != null) {
                "$viBase: $reason" // Giữ nguyên message lỗi bằng tiếng Anh vì thường là thông báo
                // kỹ thuật
            } else {
                getLocalizedMessage(viBase, enBase)
            }
        }

        // Lỗi timeout
        fun downloadTimeout(): String {
            val viMessage = "Quá thời gian chờ"
            val enMessage = "Timed out"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi kết nối gián đoạn
        fun connectionInterrupted(): String {
            val viMessage = "Kết nối gián đoạn"
            val enMessage = "Connection interrupted"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi xảy ra khi tải
        fun loadingErrorOccurred(): String {
            val viMessage = "Lỗi khi tải"
            val enMessage = "Loading error"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi không thể kết nối tới server
        fun serverUnavailable(): String {
            val viMessage = "Máy chủ không khả dụng"
            val enMessage = "Server unavailable"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi hết thời gian kết nối
        fun connectionTimeout(): String {
            val viMessage = "Hết thời gian kết nối"
            val enMessage = "Connection timeout"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi kết nối tới server thất bại
        fun serverConnectionFailed(): String {
            val viMessage = "Lỗi kết nối máy chủ"
            val enMessage = "Server connection failed"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi mất kết nối mạng
        fun networkConnectionLost(): String {
            val viMessage = "Mất kết nối"
            val enMessage = "Connection lost"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi kết nối bảo mật thất bại
        fun secureConnectionFailed(): String {
            val viMessage = "Lỗi kết nối bảo mật"
            val enMessage = "Security error"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi mạng xảy ra
        fun networkError(): String {
            val viMessage = "Lỗi mạng"
            val enMessage = "Network error"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Không thể thử lại - Thiếu thông tin
        fun cannotRetryMissingInfo(): String {
            val viMessage = "Không thể thử lại - Thiếu thông tin"
            val enMessage = "Cannot retry - Missing information"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi không xác định
        fun unknownError(): String {
            val viMessage = "Đã xảy ra lỗi không xác định"
            val enMessage = "An unknown error occurred"
            return getLocalizedMessage(viMessage, enMessage)
        }
    }

    /** Các thông báo chung */
    object CommonMessages {
        // Đang tải
        fun loading(): String {
            val viMessage = "Đang tải..."
            val enMessage = "Loading..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Thử lại
        fun retry(): String {
            val viMessage = "Thử lại"
            val enMessage = "Retry"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Đóng
        fun close(): String {
            val viMessage = "Đóng"
            val enMessage = "Close"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Đang xử lý
        fun processing(): String {
            val viMessage = "Đang xử lý..."
            val enMessage = "Processing..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Chờ một chút
        fun wait(): String {
            val viMessage = "Chờ một chút..."
            val enMessage = "Please wait..."
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Hoàn thành
        fun completed(): String {
            val viMessage = "Hoàn thành"
            val enMessage = "Completed"
            return getLocalizedMessage(viMessage, enMessage)
        }
    }
}

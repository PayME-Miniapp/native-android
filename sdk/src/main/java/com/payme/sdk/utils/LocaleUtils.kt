package com.payme.sdk.utils

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.Locale

/**
 * Lớp tiện ích để xử lý các thông báo đa ngôn ngữ
 */
object LocaleUtils {

    /**
     * Lấy thông báo theo locale hiện tại
     * @param viMessage Thông báo tiếng Việt
     * @param enMessage Thông báo tiếng Anh
     * @return Thông báo tương ứng với ngôn ngữ hiện tại
     */
    fun getLocalizedMessage(viMessage: String, enMessage: String): String {
        return when (PayMEMiniApp.locale) {
            Locale.en -> enMessage
            Locale.vi -> viMessage
            else -> viMessage // Mặc định là tiếng Việt
        }
    }
    
    /**
     * Định dạng tin nhắn với các tham số
     */
    fun formatMessage(viFormat: String, enFormat: String, vararg args: Any): String {
        val format = when (PayMEMiniApp.locale) {
            Locale.en -> enFormat
            Locale.vi -> viFormat
            else -> viFormat
        }
        return String.format(format, *args)
    }

    /**
     * Các thông báo liên quan đến tải xuống
     */
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
            val viMessage = "Tốc độ mạng chậm, tải xuống có thể mất nhiều thời gian hơn"
            val enMessage = "Slow network speed detected, download may take longer"
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

    /**
     * Các thông báo lỗi thường gặp
     */
    object ErrorMessages {
        // Lỗi không tìm thấy tài nguyên
        fun resourceNotFound(path: String): String {
            val viMessage = "Lỗi 404: Không tìm thấy tài nguyên tại $path"
            val enMessage = "Error 404: Resource not found at $path"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi giải nén
        fun unzipFailed(): String {
            val viMessage = "Lỗi: Không thể giải nén tệp nguồn"
            val enMessage = "Error: Failed to unzip source files"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi không có kết nối mạng
        fun noNetworkConnection(): String {
            val viMessage = "Không có kết nối mạng"
            val enMessage = "No network connection available"
            return getLocalizedMessage(viMessage, enMessage)
        }

        // Lỗi tải xuống
        fun downloadFailed(reason: String? = null): String {
            val viMessage = if (reason != null) "Tải xuống thất bại: $reason" else "Tải xuống thất bại"
            val enMessage = if (reason != null) "Download failed: $reason" else "Download failed"
            return getLocalizedMessage(viMessage, enMessage)
        }
        
        // Lỗi timeout
        fun downloadTimeout(): String {
            val viMessage = "Tải xuống bị hủy do quá thời gian chờ"
            val enMessage = "Download timed out"
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
            val viMessage = "Lỗi không xác định"
            val enMessage = "Unknown error"
            return getLocalizedMessage(viMessage, enMessage)
        }
    }

    /**
     * Các thông báo chung
     */
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

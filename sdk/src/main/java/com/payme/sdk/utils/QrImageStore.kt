package com.payme.sdk.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal object QrImageStore {
    fun generateQRCode(qrContent: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun saveImage(
        bitmap: Bitmap,
        context: Context,
        folderName: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = contentValues()
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
                values.put(MediaStore.Images.Media.IS_PENDING, true)

                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
                if (uri != null) {
                    saveImageToStream(
                        bitmap,
                        context.contentResolver.openOutputStream(uri),
                        onSuccess,
                        onError
                    )
                    values.put(MediaStore.Images.Media.IS_PENDING, false)
                    context.contentResolver.update(uri, values, null, null)
                }
            } else {
                val directory = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        .toString() + File.separator + folderName
                )
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val file = File(directory, System.currentTimeMillis().toString() + ".png")
                saveImageToStream(bitmap, FileOutputStream(file), onSuccess, onError)
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (e: Exception) {
            Log.d("PAYMELOG", "error save bitmap $e")
            onError()
        }
    }

    private fun contentValues(): ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        return values
    }

    private fun saveImageToStream(
        bitmap: Bitmap,
        outputStream: OutputStream?,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        if (outputStream != null) {
            try {
                outputStream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("PAYMELOG", "error saveImageToStream $e")
                onError()
            }
        }
    }
}

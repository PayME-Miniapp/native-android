package com.payme.sdk.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.objects.DetectedObject
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.SourceInstaller
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

internal object ImageProcessingUtils {
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
    }

    fun handleFaceImageProxy(image: ImageProxy): Bitmap? {
        return try {
            rotateImageProxy(image)
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "handle image proxy ${e.message}")
            null
        }
    }

    fun handleImageProxy(context: Context, image: ImageProxy): Bitmap? {
        return try {
            var bitmap = rotateImageProxy(image)
            val metrics = displayMetrics(context)
            val bitmapHeight = bitmap.height
            val windowHeight = metrics.heightPixels
            val top = WindowMetricsUtils.dpToPx(context, 86) * bitmapHeight / windowHeight
            val height = bitmap.width * 0.7
            Log.d(
                "PAYME",
                "screenHeight ${metrics.heightPixels} bitmapHeight $bitmapHeight windowHeight $windowHeight"
            )
            bitmap = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height.toInt())
            bitmap
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "handle image proxy ${e.message}")
            null
        }
    }

    fun compressBitmapToFile(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            val wwwDirectory = File(context.filesDir, MiniAppSourceConstants.WWW_DIR)
            val sourceRoot = SourceInstaller.findWebRoot(wwwDirectory)
                ?: File(wwwDirectory, MiniAppSourceConstants.SOURCE_ROOT)
            val imagesDir = File(sourceRoot, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val file = File(imagesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "save image exception ${e.message}")
        }
    }

    fun validateListObject(detectedObject: DetectedObject): Boolean {
        if (detectedObject.labels.isEmpty()) {
            return false
        }
        val label = detectedObject.labels[0].text.lowercase()
        return label.contains("card") || label.contains("license")
    }

    private fun rotateImageProxy(image: ImageProxy): Bitmap {
        val rotationDegree = image.imageInfo.rotationDegrees
        Log.d(PayMEMiniApp.TAG, "rotation $rotationDegree")
        var bitmap = imageProxyToBitmap(image)
        val rotationMatrix = Matrix()
        rotationMatrix.postRotate(rotationDegree.toFloat())
        if (rotationDegree != 0) {
            bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                rotationMatrix,
                true
            )
        }
        return bitmap
    }

    private fun displayMetrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.xdpi = context.resources.displayMetrics.xdpi
            metrics.ydpi = context.resources.displayMetrics.ydpi
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getMetrics(metrics)
        }
        return metrics
    }
}

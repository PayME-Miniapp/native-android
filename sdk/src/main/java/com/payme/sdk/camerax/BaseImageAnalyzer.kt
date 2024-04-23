package com.payme.sdk.camerax

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.payme.sdk.PayMEMiniApp

abstract class BaseImageAnalyzer<T> : ImageAnalysis.Analyzer {

    abstract val graphicOverlay: GraphicOverlay

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        try {
            mediaImage?.let {
                detectInImage(
                    InputImage.fromMediaImage(
                        it,
                        imageProxy.imageInfo.rotationDegrees
                    )
                ).addOnSuccessListener { results ->
                        onSuccess(
                            results, graphicOverlay, it.cropRect
                        )
                        imageProxy.close()
                    }.addOnFailureListener {
                        onFailure(it)
                        imageProxy.close()
                    }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, e.toString())
        }
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    abstract fun stop()

    protected abstract fun onSuccess(
        results: T, graphicOverlay: GraphicOverlay, rect: Rect
    )

    protected abstract fun onFailure(e: Exception)

}

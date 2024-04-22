package com.payme.sdk.object_detection

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface.ROTATION_0
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
import androidx.camera.core.impl.ImageOutputConfig.RotationValue
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.rotationMatrix
import androidx.lifecycle.LifecycleOwner
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.IdentityCardViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class IdentityCardCameraManager(
    private val context: Context,
    private val finderView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val graphicOverlay: GraphicOverlay,
    private val activeFrame: IdentityCardActiveFrame,
    private val button: View,
    private val imageTaken: ImageView,
    private val identityCardType: String,
    private val identityCardViewModel: IdentityCardViewModel,
) {
    private var preview: Preview? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelectorOption = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var imageCapture: ImageCapture? = null

    var confidence = 0

    init {
        createNewExecutor()
    }

    private fun createNewExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val rotation = context.display?.rotation ?: ROTATION_0
                val metrics = context.resources.displayMetrics

                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                    .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, selectAnalyzer())
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraSelectorOption)
                    .build()

                setCameraConfig(cameraProvider, cameraSelector)

            }, ContextCompat.getMainExecutor(context)
        )
    }

    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        return ObjectDetectionProcessor(
            context,
            graphicOverlay,
            activeFrame,
            button,
            this,
            identityCardViewModel
        )
    }

    private fun setCameraConfig(
        cameraProvider: ProcessCameraProvider?,
        cameraSelector: CameraSelector
    ) {
        finderView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        try {
            cameraProvider?.unbindAll()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview!!)
                .addUseCase(imageAnalyzer!!)
                .addUseCase(imageCapture!!)
                .setViewPort(finderView.viewPort!!)
                .build()

            camera = cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
            preview?.setSurfaceProvider(
                finderView.surfaceProvider
            )
            button.setOnClickListener {
                val imageCapture = imageCapture ?: return@setOnClickListener

                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        @SuppressLint("UnsafeOptInUsageError")
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val imageBitmap = Utils.handleImageProxy(context, image, finderView)
                            val fileName =
                                if (identityCardType == "FRONT") "kycFrontIdCard.jpeg" else "kycBackIdCard.jpeg"
                            if (imageBitmap != null) {
                                Utils.compressBitmapToFile(context, imageBitmap, fileName)
                                val intent = Intent()
                                intent.putExtra("fileName", "images/$fileName")
                                intent.putExtra("type", identityCardType)
                                (context as Activity).setResult(Activity.RESULT_OK, intent)
                                image.close()
                                context.finish()
                            }
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            val errorType = exception.imageCaptureError
                            Log.d(PayMEMiniApp.TAG, "error capture image: $errorType")
                        }
                    })
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "Use case binding failed", e)
        }
    }
}

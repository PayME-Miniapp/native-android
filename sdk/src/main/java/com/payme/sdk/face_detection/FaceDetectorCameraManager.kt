package com.payme.sdk.face_detection

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.FaceDetectorStepViewModel
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class FaceDetectorCameraManager(
    private val context: Context,
    private val finderView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val graphicOverlay: GraphicOverlay,
    private val activeFrame: FaceDetectorActiveFrame,
    private val progressBar: ProgressDetectBar,
    private var timerTask: TimerTask?,
    private val textHint: TextView,
    private val lottieView: LottieAnimationView,
    private val faceDetectorStepViewModel: FaceDetectorStepViewModel,
    private val hint2: String?,
    private val hint3: String?,
) {
    private var preview: Preview? = null
    private var camera: Camera? = null
    lateinit var cameraExecutor: ExecutorService
    private var cameraSelectorOption = CameraSelector.LENS_FACING_FRONT
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    var imageCapture: ImageCapture? = null
    var isClosedEyes = false

    init {
        createNewExecutor()

        faceDetectorStepViewModel.getStep().observe(context as LifecycleOwner) {
            when (it) {
                2 -> {
                    progressBar.setProgress(0.33F)
                    textHint.text =
                        (hint2 ?: context.getString(R.string.face_detector_hint2)) as CharSequence?
                    takePicture("kycFace1.jpeg", null)
                    lottieView.setAnimation(R.raw.chop_mat)
                    lottieView.playAnimation()
                }
                3 -> {
                    progressBar.setProgress(0.67F)
                    textHint.text =
                        (hint3 ?: context.getString(R.string.face_detector_hint3)) as CharSequence?
                    takePicture("kycFace2.jpeg", null)
                    lottieView.setAnimation(R.raw.cuoi)
                    lottieView.playAnimation()
                }
                4 -> {
                    progressBar.setProgress(1F)
                    takePicture("kycFace3.jpeg") {
                        val intent = Intent()
                        val images =
                            arrayOf(
                                "images/kycFace1.jpeg",
                                "images/kycFace2.jpeg",
                                "images/kycFace3.jpeg"
                            )
                        intent.putExtra("images", images)
                        (context as Activity).setResult(Activity.RESULT_OK, intent)
                        context.finish()
                    }
                }
            }
        }
    }

    private fun createNewExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun takePicture(fileName: String, callback: (() -> Unit)? = null) {
        imageCapture?.takePicture(cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeOptInUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {
                    val imageBitmap = Utils.handleFaceImageProxy(image)
                    if (imageBitmap != null) {
                        Utils.compressBitmapToFile(context, imageBitmap, fileName)
                    }
                    Log.d(PayMEMiniApp.TAG, "pic taken")
                    if (callback != null) {
                        callback()
                    }
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.d(PayMEMiniApp.TAG, "error capture image face detector: ${exception.message}")
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val display = finderView?.display ?: context.display
                val rotation = display?.rotation ?: Surface.ROTATION_0
                val metrics = DisplayMetrics().also { display?.getMetrics(it) }

                preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        return FaceContourDetectionProcessor(
            context,
            graphicOverlay,
            progressBar,
            activeFrame,
            isClosedEyes,
            timerTask,
            faceDetectorStepViewModel
        )
    }

    private fun setCameraConfig(
        cameraProvider: ProcessCameraProvider?,
        cameraSelector: CameraSelector
    ) {
        try {
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview!!)
                .addUseCase(imageAnalyzer!!)
                .addUseCase(imageCapture!!)
                .setViewPort(finderView.viewPort!!)
                .build()

            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)

            preview?.setSurfaceProvider(
                finderView.surfaceProvider
            )
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Use case binding failed", e)
        }
    }
}

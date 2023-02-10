package com.payme.sdk.face_detection

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.payme.sdk.camerax.BaseImageAnalyzer
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.viewmodels.FaceDetectorStepViewModel
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule

class FaceContourDetectionProcessor(
    private val context: Context,
    private val graphicOverlayView: GraphicOverlay,
    private val progressBar: ProgressDetectBar,
    private val activeFrame: FaceDetectorActiveFrame,
    private var isClosedEyes: Boolean,
    private var timerTask: TimerTask?,
    private val faceDetectorStepViewModel: FaceDetectorStepViewModel,
) :
    BaseImageAnalyzer<List<Face>>() {

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.95f)
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)

    override val graphicOverlay: GraphicOverlay
        get() = graphicOverlayView

    override fun detectInImage(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.d("PAYME", "Exception thrown while trying to close Face Detector: $e")
        }
    }

    override fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay,
        rect: Rect
    ) {
        graphicOverlay.clear()
        if (results.size == 1) {
            val face = results[0]
            val faceGraphic = FaceContourGraphic(
                context,
                graphicOverlay,
                face,
                rect,
                progressBar,
                activeFrame,
                faceDetectorStepViewModel
            )
            graphicOverlay.add(faceGraphic)
            if (!progressBar.getIsValid()) return
            val condition1 = face.headEulerAngleX >= -20 && face.headEulerAngleX < 20
                    && face.headEulerAngleY >= -20 && face.headEulerAngleY < 20
                    && face.headEulerAngleZ >= -20 && face.headEulerAngleZ < 20
                    && (face.leftEyeOpenProbability == null || face.leftEyeOpenProbability!! >= 0.5)
                    && (face.rightEyeOpenProbability == null || face.rightEyeOpenProbability!! >= 0.5)

            if (faceDetectorStepViewModel.getStep().value == 1) {
                if (condition1) {
                    faceDetectorStepViewModel.setStep(2)
                }
            } else if (faceDetectorStepViewModel.getStep().value == 2) {
                if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                    if (face.leftEyeOpenProbability!! >= 0.5 && face.rightEyeOpenProbability!! >= 0.5) {
                        if (isClosedEyes) {
                            faceDetectorStepViewModel.setStep(3)
                        }
                    } else if (face.leftEyeOpenProbability!! < 0.6 && face.rightEyeOpenProbability!! < 0.6) { // blink
                        isClosedEyes = true
                        timerTask = Timer("Blinking", false).schedule(3000) {
                            isClosedEyes = false
                        }
                    }
                }
            } else if (faceDetectorStepViewModel.getStep().value == 3) {
                if (face.smilingProbability != null && face.smilingProbability!! >= 0.5) {
                    faceDetectorStepViewModel.setStep(4)
                }
            }
        } else {
            faceDetectorStepViewModel.setTextHint("")
            progressBar.setIsValid(false)
        }
        graphicOverlay.postInvalidate()
    }

    override fun onFailure(e: Exception) {
        Log.d("PAYME", "Face Detector failed.${e.message}")
    }
}

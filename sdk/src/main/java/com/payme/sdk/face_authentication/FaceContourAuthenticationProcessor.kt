package com.payme.sdk.face_authentication

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.camerax.BaseImageAnalyzer
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.face_authentication.FaceContourGraphic
import com.payme.sdk.face_detection.FaceDetectorActiveFrame
import com.payme.sdk.viewmodels.FaceDetectorStepViewModel
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule

class FaceContourAuthenticationProcessor(
    private val context: Context,
    private val graphicOverlayView: GraphicOverlay,
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
            Log.d(PayMEMiniApp.TAG, "Exception thrown while trying to close Face Detector: $e")
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
                activeFrame,
                faceDetectorStepViewModel
            )
            graphicOverlay.add(faceGraphic)
            val condition1 = face.headEulerAngleX >= -20 && face.headEulerAngleX < 20
                    && face.headEulerAngleY >= -20 && face.headEulerAngleY < 20
                    && face.headEulerAngleZ >= -20 && face.headEulerAngleZ < 20
                    && (face.leftEyeOpenProbability == null || face.leftEyeOpenProbability!! >= 0.5)
                    && (face.rightEyeOpenProbability == null || face.rightEyeOpenProbability!! >= 0.5)

            if (faceDetectorStepViewModel.getStep().value == 1) {
                if (condition1) {
                    faceDetectorStepViewModel.setStep(2)
                }
            }
        } else {
            faceDetectorStepViewModel.setTextHint("")
        }
        graphicOverlay.postInvalidate()
    }

    override fun onFailure(e: Exception) {
        Log.d(PayMEMiniApp.TAG, "Face Detector failed.${e.message}")
    }
}

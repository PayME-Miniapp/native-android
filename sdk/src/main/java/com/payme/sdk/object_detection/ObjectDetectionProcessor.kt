package com.payme.sdk.object_detection

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.View
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.camerax.BaseImageAnalyzer
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.IdentityCardViewModel
import java.io.IOException

class ObjectDetectionProcessor(
    private val context: Context,
    private val view: GraphicOverlay,
    private val activeFrame: IdentityCardActiveFrame,
    private val button: View,
    private val cameraManager: IdentityCardCameraManager?,
    private val identityCardViewModel: IdentityCardViewModel,
) :
    BaseImageAnalyzer<List<DetectedObject>>() {

    private val localModel =
        LocalModel.Builder().setAssetFilePath("custom_models/object_labeler.tflite").build()
    private val options = CustomObjectDetectorOptions.Builder(localModel)
        .enableMultipleObjects()
        .setMaxPerObjectLabelCount(3)
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableClassification()
        .build()

    private val detector: ObjectDetector = ObjectDetection.getClient(options)
    override val graphicOverlay: GraphicOverlay
        get() = view

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(PayMEMiniApp.TAG, "Exception thrown while trying to close Face Detector: $e")
        }
    }

    override fun onSuccess(
        results: List<DetectedObject>,
        graphicOverlay: GraphicOverlay,
        rect: Rect
    ) {
        graphicOverlay.clear()
//    Log.d(PayMEMiniApp.TAG, "onSuccess: ${results.size} $results")
        val filtered = results.filter { Utils.validateListObject(it) }
        if (filtered.size == 1) {
            val objectGraphic =
                ObjectContourGraphic(
                    context,
                    graphicOverlay,
                    filtered[0].boundingBox,
                    rect,
                    activeFrame,
                    button,
                    cameraManager,
                    identityCardViewModel
                )
            graphicOverlay.add(objectGraphic)
        } else {
            cameraManager?.confidence = 0
            activeFrame.setStrokeColor(Color.parseColor("#FBBC05"))
            identityCardViewModel.setEnableButton(false)
            identityCardViewModel.setTextHintValue("")
        }
    }

    override fun onFailure(e: Exception) {
        Log.d(PayMEMiniApp.TAG, "onFailure: ${e.message}")
    }

}

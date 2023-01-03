package com.payme.sdk.face_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.payme.sdk.BuildConfig
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.FaceDetectorStepViewModel
import kotlin.math.abs

class FaceContourGraphic(
    private val context: Context,
    overlay: GraphicOverlay,
    private val face: Face,
    private val imageRect: Rect,
    private val progressBar: ProgressDetectBar,
    private val activeFrame: FaceDetectorActiveFrame,
    private val faceDetectorStepViewModel: FaceDetectorStepViewModel,
) : GraphicOverlay.Graphic(overlay) {

    private val facePositionPaint: Paint
    private val idPaint: Paint
    private val boxPaint: Paint

    init {
        val selectedColor = Color.WHITE

        facePositionPaint = Paint()
        facePositionPaint.color = selectedColor

        idPaint = Paint()
        idPaint.color = selectedColor

        boxPaint = Paint()
        boxPaint.color = selectedColor
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = BOX_STROKE_WIDTH
    }

    override fun draw(canvas: Canvas?) {
        val rect = calculateRect(
            imageRect.height().toFloat(),
            imageRect.width().toFloat(),
            face.boundingBox
        )

        if (!overlay.isFrontMode()) {
            return
        }

        val rectHeight = Utils().pxToDp(context, abs(rect.bottom - rect.top).toInt())
        val activeHeight = Utils().pxToDp(
            context,
            abs(activeFrame.boundingBox.bottom - activeFrame.boundingBox.top).toInt()
        )

        val condition =
            (abs(rect.top - activeFrame.boundingBox.top) < 100 || rect.top >= activeFrame.boundingBox.top) &&
                    (abs(rect.bottom - activeFrame.boundingBox.bottom) < 100 || rect.bottom <= activeFrame.boundingBox.bottom)
                    && rect.left < overlay.width.toFloat() && rect.right > 0

//    Log.d("HIEU", "rectHeight ${rectHeight}")
//    Log.d("HIEU", "activeHeight ${activeHeight}")

        val condition2 = abs(rectHeight - activeHeight) > 100 && (rectHeight > activeHeight)
        val condition3 = abs(rectHeight - activeHeight) > 100 && (rectHeight < activeHeight)

        if (condition) {
            boxPaint.color = Color.GREEN
            progressBar.setIsValid(true)
        } else {
            boxPaint.color = Color.WHITE
            progressBar.setIsValid(false)
        }

        if (condition3) {
            faceDetectorStepViewModel.setTextHint("Đưa camera đến gần mặt bạn hơn nha")
        } else if (condition2) {
            faceDetectorStepViewModel.setTextHint("Tuyệt! Bây giờ đưa camera ra xa một chút")
        } else {
            faceDetectorStepViewModel.setTextHint("")
        }

        if (BuildConfig.DEBUG) {
            canvas?.drawRect(rect, boxPaint)
        }
    }

    companion object {
        private const val BOX_STROKE_WIDTH = 5.0f
    }

}

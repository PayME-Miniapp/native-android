package com.payme.sdk.object_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.View
import com.payme.sdk.BuildConfig
import com.payme.sdk.R
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.IdentityCardViewModel
import kotlin.math.abs

class ObjectContourGraphic(
    private val context: Context,
    overlay: GraphicOverlay,
    private val boundingBox: Rect,
    private val imageRect: Rect,
    private val activeFrame: IdentityCardActiveFrame,
    private val button: View,
    private val cameraManager: IdentityCardCameraManager?,
    private val identityCardViewModel: IdentityCardViewModel,
) : GraphicOverlay.Graphic(overlay) {
    private val boxPaint: Paint = Paint()

    init {
        boxPaint.color = Color.WHITE
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 2F
    }

    override fun draw(canvas: Canvas?) {
        val rect = calculateRect(
            imageRect.height().toFloat(),
            imageRect.width().toFloat(),
            boundingBox
        )
        if (!overlay.isFrontMode()) {
            val absLeft =
                Utils.pxToDp(
                    context,
                    abs(boundingBox.left - activeFrame.boundingBox.left).toInt()
                )
            val absRight =
                Utils.pxToDp(
                    context,
                    abs(boundingBox.right - activeFrame.boundingBox.right).toInt()
                )

            val absTop =
                Utils.pxToDp(context, abs(rect.top - activeFrame.boundingBox.top).toInt())
            val absBottom =
                Utils.pxToDp(context, abs(rect.bottom - activeFrame.boundingBox.bottom).toInt())

            val rectWidth = Utils.pxToDp(context, abs(rect.right - rect.left).toInt())
            val rectHeight = Utils.pxToDp(context, abs(rect.bottom - rect.top).toInt())

            val activeWidth = Utils.pxToDp(
                context,
                abs(activeFrame.boundingBox.right - activeFrame.boundingBox.left).toInt()
            )
            val activeHeight = Utils.pxToDp(
                context,
                abs(activeFrame.boundingBox.bottom - activeFrame.boundingBox.top).toInt()
            )

            //      Log.d("HIEU", "boundingBox ${boundingBox.left}")
//      Log.d("HIEU", "rectLeft ${rect.left}")
//      Log.d("HIEU", "activeFrame ${activeFrame.boundingBox.left}")
//      Log.d("HIEU", "boundingBox top ${boundingBox.top}")
//      Log.d("HIEU", "rectLeft top ${rect.top}")
//      Log.d("HIEU", "rect.top ${rect.top}")
//      Log.d("HIEU", "activeFrame boundingBox top ${activeFrame.boundingBox.top}")

            Log.d("HIEU", "aaaa")

            val condition =
                (abs(rect.top - activeFrame.boundingBox.top) < 60 || rect.top > activeFrame.boundingBox.top)
                        &&
                        (abs(rect.bottom - activeFrame.boundingBox.bottom) < 60 || rect.bottom < activeFrame.boundingBox.bottom)
                        && rect.left < overlay.width.toFloat() && rect.right > 0

            val condition2 =
                rect.top > activeFrame.boundingBox.top
                        && rect.bottom < activeFrame.boundingBox.bottom
                        && rect.left < overlay.width.toFloat() && rect.right > 0

            val condition3 =
                abs(rectWidth - activeWidth) > 50 && abs(rectHeight - activeHeight) > 50
            val condition4 =
                abs(rectWidth - activeWidth) < 40 && abs(rectHeight - activeHeight) < 40

            identityCardViewModel.setTextHintValue("")

            if (condition) {
                activeFrame.setStrokeColor(Color.parseColor("#33CB33"))
                identityCardViewModel.setEnableButton(true)
            } else {
                activeFrame.setStrokeColor(Color.parseColor("#FBBC05"))
                identityCardViewModel.setEnableButton(false)
            }

            if (condition3 && condition2) {
                identityCardViewModel.setTextHintValue(context.getString(R.string.identity_card_hint2))
                activeFrame.setStrokeColor(Color.parseColor("#FBBC05"))
                identityCardViewModel.setEnableButton(false)
            }

            if (condition4 && condition2) {
                cameraManager?.confidence = cameraManager?.confidence?.plus(1)!!
                identityCardViewModel.setTextHintValue(context.getString(R.string.identity_card_hint3))
            } else {
                cameraManager?.confidence = 0
            }

            if (cameraManager?.confidence == 10) {
                button.performClick()
            }
        }

        if (BuildConfig.DEBUG) {
            canvas?.drawRect(rect, boxPaint)
        }
    }
}

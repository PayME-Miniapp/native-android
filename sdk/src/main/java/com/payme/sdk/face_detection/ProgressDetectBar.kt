package com.payme.sdk.face_detection

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.payme.sdk.utils.Utils
import kotlin.math.PI
import kotlin.math.cos

class ProgressDetectBar : View {
    private var paint: Paint = Paint()
    private var progress: Float = 0F
    private var placeholderPaint: Paint = Paint()
    private var isValid: Boolean = false

    constructor(context: Context) : super(context) {
        initial()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initial()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initial()
    }

    private fun initial() {
        paint.color = Color.parseColor("#33CB33")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10F

        placeholderPaint.color = Color.parseColor("#66ffffff")
        placeholderPaint.style = Paint.Style.STROKE
        placeholderPaint.strokeWidth = 10F
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        val width = this.width.toFloat()

        val viewportMargin = Utils.dpToPx(context, 40)
        val radius = (width - viewportMargin * 2) / 2
        val padding = Utils.dpToPx(context, 10)
        val paddingMilesStones = Utils.dpToPx(context, 16)

        val oval = RectF(
            viewportMargin.toFloat() - padding,
            Utils.dpToPx(context, 79).toFloat() - padding,
            viewportMargin.toFloat() + radius * 2 + padding,
            Utils.dpToPx(context, 79).toFloat() + radius * 2 + padding
        )

        val milesStonesPaint = Paint()
        milesStonesPaint.color = Color.parseColor("#33CB33")
        milesStonesPaint.style = Paint.Style.STROKE
        milesStonesPaint.strokeWidth = 14F
        milesStonesPaint.strokeCap = Paint.Cap.ROUND

        canvas?.drawLine(
            viewportMargin.toFloat() + radius,
            Utils.dpToPx(context, 79).toFloat() - paddingMilesStones,
            viewportMargin.toFloat() + radius,
            Utils.dpToPx(context, 79).toFloat(),
            milesStonesPaint
        )

        canvas?.drawLine(
            viewportMargin.toFloat() + radius + (radius * cos((PI / 6).toFloat())),
            Utils.dpToPx(context, 79).toFloat() + radius + (radius * cos((PI / 3).toFloat())),
            viewportMargin.toFloat() + radius + ((radius + paddingMilesStones) * cos((PI / 6).toFloat())),
            Utils.dpToPx(context, 79)
                .toFloat() + radius + ((radius + paddingMilesStones) * cos((PI / 3).toFloat())),
            milesStonesPaint
        )

        canvas?.drawLine(
            viewportMargin.toFloat() + radius - radius * cos(PI / 6).toFloat(),
            Utils.dpToPx(context, 79).toFloat() + radius + (radius * cos((PI / 3).toFloat())),
            viewportMargin.toFloat() + radius - (radius + paddingMilesStones) * cos((PI / 6).toFloat()),
            Utils.dpToPx(context, 79)
                .toFloat() + radius + ((radius + paddingMilesStones) * cos((PI / 3).toFloat())),
            milesStonesPaint
        )

        canvas?.drawArc(
            oval,
            -90F + progress * 360f,
            (1 - progress) * 360f,
            false,
            placeholderPaint
        )
        canvas?.drawArc(oval, -90F, progress * 360f, false, paint)
    }

    fun setProgress(progress: Float) {
        val animator = ValueAnimator.ofFloat(1F, 2F)
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            val progressAnim = progress * value / 2
            this.progress = progressAnim
            invalidate()
        }
        animator.interpolator = LinearInterpolator()
        animator.duration = 500
        animator.start()
    }

    fun setIsValid(value: Boolean) {
        this.isValid = value
        if (value) {
            paint.color = Color.parseColor("#33CB33")
        } else {
            paint.color = Color.parseColor("#FBBC05")
        }
        invalidate()

    }

    fun getIsValid(): Boolean {
        return this.isValid
    }
}

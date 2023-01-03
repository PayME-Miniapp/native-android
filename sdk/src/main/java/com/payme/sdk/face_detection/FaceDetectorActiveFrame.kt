package com.payme.sdk.face_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup
import com.payme.sdk.utils.Utils

class FaceDetectorActiveFrame : ViewGroup {
    lateinit var boundingBox: RectF

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    public override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val viewportMargin = Utils().dpToPx(context, 40)
        val radius = (width.toFloat() - viewportMargin * 2) / 2

        val eraser = Paint()
        eraser.isAntiAlias = true
        eraser.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        val cx = width.toFloat() / 2
        val cy = radius + Utils().dpToPx(context, 79)

        val rect = RectF(
            viewportMargin.toFloat(),
            Utils().dpToPx(context, 79).toFloat(),
            viewportMargin.toFloat() + radius * 2,
            Utils().dpToPx(context, 79).toFloat() + radius * 2
        )
        boundingBox = rect
        canvas.drawCircle(cx, cy, radius, eraser)
    }
}
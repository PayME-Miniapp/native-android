package com.payme.sdk.object_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup
import com.payme.sdk.utils.Utils


class IdentityCardActiveFrame : ViewGroup {
    private var color: Int = Color.parseColor("#FBBC05")
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

    fun setStrokeColor(color: Int) {
        this.color = color
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val viewportMargin = Utils.dpToPx(context, 20)
        val viewportCornerRadius = 16
        val top = Utils.dpToPx(context, 86)
        val eraser = Paint()
        eraser.isAntiAlias = true
        eraser.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val width = width.toFloat() - viewportMargin
        val bottom = width * 0.7 + Utils.dpToPx(context, 66)
        val rect = RectF(viewportMargin.toFloat(), top.toFloat(), width, bottom.toFloat())
        val frame =
            RectF(viewportMargin.toFloat() - 2, top.toFloat() - 2, width + 4, bottom.toFloat() + 4)
        val path = Path()
        val stroke = Paint()
        stroke.isAntiAlias = true
        stroke.strokeWidth = 4F
        stroke.color = color
        stroke.style = Paint.Style.STROKE
        path.addRoundRect(
            frame,
            viewportCornerRadius.toFloat(),
            viewportCornerRadius.toFloat(),
            Path.Direction.CW
        )
        this.boundingBox = rect
        canvas.drawPath(path, stroke)
        canvas.drawRoundRect(
            rect,
            viewportCornerRadius.toFloat(),
            viewportCornerRadius.toFloat(),
            eraser
        )
    }
}
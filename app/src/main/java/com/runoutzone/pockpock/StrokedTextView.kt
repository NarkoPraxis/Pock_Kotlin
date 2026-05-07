package com.runoutzone.pockpock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes

class StrokedTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var fillColor: Int = Color.WHITE
    private var strokeColor: Int = Color.BLACK

    private val fatPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val textPath = Path()
    private val solidPath = Path()
    private var pathsDirty = true

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.StrokedTextView) {
                fillColor = getColor(R.styleable.StrokedTextView_fillColor, fillColor)
                strokeColor = getColor(R.styleable.StrokedTextView_strokeColor, strokeColor)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val halfStroke = (textSize * 0.125f).toInt()
        setMeasuredDimension(measuredWidth + halfStroke * 2, measuredHeight + halfStroke * 2)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        pathsDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        val str = text.toString()
        if (str.isEmpty()) return
        val currentLayout = layout ?: return super.onDraw(canvas)

        if (pathsDirty) {
            val halfStroke = textSize * 0.125f
            val x = compoundPaddingLeft + halfStroke
            val y = (compoundPaddingTop + currentLayout.getLineBaseline(0)).toFloat()

            textPath.reset()
            paint.getTextPath(str, 0, str.length, x, y, textPath)

            // Fat stroke traces outer and inner (counter) glyph edges; wide enough to flood counters.
            // Its outer expansion beyond the glyph edge becomes the visible border.
            fatPaint.strokeWidth = textSize * 0.25f
            solidPath.reset()
            fatPaint.getFillPath(textPath, solidPath)
            solidPath.op(textPath, Path.Op.UNION)

            pathsDirty = false
        }

        // Pass 1: strokeColor fills everything — outer expansion = border, counters = solid
        paint.style = Paint.Style.FILL
        paint.color = strokeColor
        canvas.drawPath(solidPath, paint)

        // Pass 2: fillColor covers the glyph body; counters remain strokeColor
        paint.color = fillColor
        canvas.drawPath(textPath, paint)

        paint.strokeWidth = 0f
    }
}

package com.example.puck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import utility.Storage

/**
 * Sideways-thermometer progress bar for the ball unlock system.
 *
 * Layout: a horizontal tube filling left-to-right, with a circle bulb on the
 * right that always shows the current percentage.  Nine tick marks at 10%
 * intervals indicate each ball-type unlock threshold.
 *
 * The palette color (outline, bulb, tick marks) is randomly chosen once per
 * instance as either the high-player red or low-player blue.
 * The fill color is always the purple charge color.
 */
class UnlockProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    // Chosen once at construction; stays fixed for the lifetime of this view instance.
    private val useHighPlayer = kotlin.random.Random.nextBoolean()

    private val fillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val tickPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tubeRect   = RectF()
    private val fillRect   = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val isDark = try { Storage.darkMode } catch (_: Exception) { false }

        // Palette color: high-player red or low-player blue (outline, bulb, ticks)
        val palette = context.getColor(
            if (useHighPlayer) R.color.highPlayerDark else R.color.lowPlayerDark
        )
        // Progress fill: purple charge color
        val fillColor = context.getColor(R.color.effectColor)

        val paletteDim  = Color.argb(80,  Color.red(palette), Color.green(palette), Color.blue(palette))
        val tickOnFill  = Color.argb(200, 255, 255, 255)
        val tubeEmptyBg = Color.argb(
            if (isDark) 35 else 18,
            Color.red(palette), Color.green(palette), Color.blue(palette)
        )

        val pad     = h * 0.10f
        val strokeW = h * 0.075f

        // --- Bulb (circle on the right) ---
        val bulbR  = h / 2f - pad
        val bulbCx = w - h / 2f
        val bulbCy = h / 2f

        // --- Tube ---
        // Right edge overlaps the bulb slightly so they merge seamlessly.
        val tubeH      = h * 0.34f
        val tubeLeft   = pad * 2f
        val tubeRight  = bulbCx + bulbR * 0.35f
        val tubeTop    = bulbCy - tubeH / 2f
        val tubeBottom = bulbCy + tubeH / 2f
        val tubeCorner = tubeH / 2f
        // Logical fill width runs from tubeLeft to the bulb centre.
        val fillableWidth = bulbCx - tubeLeft

        // 1. Empty-tube background
        bgPaint.color = tubeEmptyBg
        tubeRect.set(tubeLeft, tubeTop, tubeRight, tubeBottom)
        canvas.drawRoundRect(tubeRect, tubeCorner, tubeCorner, bgPaint)

        // 2. Fill (purple charge color)
        if (progress > 0) {
            val fillRight = tubeLeft + fillableWidth * progress / 100f
            fillPaint.color = fillColor
            fillRect.set(
                tubeLeft, tubeTop,
                fillRight.coerceAtLeast(tubeLeft + tubeCorner * 2f),
                tubeBottom
            )
            canvas.drawRoundRect(fillRect, tubeCorner, tubeCorner, fillPaint)
        }

        // 3. Tick marks at 10% intervals — clipped to just inside the tube bounds
        tickPaint.strokeWidth = strokeW * 0.55f
        val tickInset = strokeW * 0.4f   // keep ticks inside the tube outline
        for (i in 1..9) {
            val tickX = tubeLeft + fillableWidth * i / 10f
            tickPaint.color = if (progress >= i * 10) tickOnFill else paletteDim
            canvas.drawLine(tickX, tubeTop + tickInset, tickX, tubeBottom - tickInset, tickPaint)
        }

        // 4. Tube outline (drawn after fill so the border sits on top)
        strokePaint.color = palette
        strokePaint.strokeWidth = strokeW
        tubeRect.set(tubeLeft, tubeTop, tubeRight, tubeBottom)
        canvas.drawRoundRect(tubeRect, tubeCorner, tubeCorner, strokePaint)

        // 5. Bulb — palette color fill
        fillPaint.color = palette
        canvas.drawCircle(bulbCx, bulbCy, bulbR, fillPaint)
        strokePaint.color = palette
        canvas.drawCircle(bulbCx, bulbCy, bulbR, strokePaint)

        // 6. Percentage label inside the bulb
        textPaint.color = Color.WHITE
        textPaint.textSize = bulbR * 0.72f
        canvas.drawText("$progress%", bulbCx, bulbCy + textPaint.textSize * 0.37f, textPaint)
    }
}

package com.example.puck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import enums.BallType
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import utility.PaintBucket
import kotlin.math.abs
import kotlin.math.max

class BallUnlockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cardBg = Paint().apply { color = Color.argb(220, 22, 22, 34); style = Paint.Style.FILL; isAntiAlias = true }
    private val cardBorder = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val sublabel = Paint().apply { color = Color.argb(160, 255, 255, 255); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val lockFill = Paint().apply { color = Color.argb(170, 0, 0, 0); style = Paint.Style.FILL; isAntiAlias = true }
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewPuck = Puck(0f, 0f, 0f, Color.WHITE, Color.WHITE)
    private var frame: Int = 0

    private var scrollY: Float = 0f
    private var dragging: Boolean = false
    private var lastTouchY: Float = 0f
    private var paintsReady: Boolean = false

    private val columns: Int = 2
    private val rows: Int = (BallType.values().size + columns - 1) / columns

    private fun ratio(): Float = max(1f, kotlin.math.min(width, height) / 18f)
    private fun cellSize(): Float = (width - ratio() * 2f) / columns - ratio() * 0.5f
    private fun gridPadX(): Float = ratio()
    private fun gridPadY(): Float = ratio() * 0.8f

    private fun cellBounds(index: Int): FloatArray {
        val col = index % columns
        val row = index / columns
        val cs = cellSize()
        val gap = ratio() * 0.5f
        val left = gridPadX() + col * (cs + gap)
        val top = gridPadY() + row * (cs + gap) - scrollY
        return floatArrayOf(left, top, left + cs, top + cs)
    }

    private fun maxScroll(): Float {
        val cs = cellSize()
        val gap = ratio() * 0.5f
        val contentH = gridPadY() * 2f + rows * cs + (rows - 1) * gap
        return max(0f, contentH - height)
    }

    private fun ensurePaints() {
        if (!paintsReady && width > 0 && height > 0) {
            PaintBucket.initialize(resources)
            paintsReady = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensurePaints()
        if (!paintsReady) return

        val originalRatio = Settings.screenRatio
        Settings.screenRatio = ratio()

        frame++
        val types = BallType.values()
        val cs = cellSize()
        val pr = cs * 0.28f

        for (i in types.indices) {
            val b = cellBounds(i)
            if (b[3] < 0 || b[1] > height) continue
            val type = types[i]
            val theme = if (i % 2 == 0) ColorTheme.Warm else ColorTheme.Cold

            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBg)
            cardBorder.color = theme.primary
            cardBorder.strokeWidth = ratio() * 0.14f
            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBorder)

            val cx = (b[0] + b[2]) / 2f
            val cy = (b[1] + b[3]) / 2f - ratio() * 0.4f

            val (skin, _) = BallStyleFactory.build(type, theme)
            previewPuck.x = cx
            previewPuck.y = cy
            previewPuck.radius = pr
            previewPuck.frame = frame
            previewPuck.setFill(theme.primary)
            previewPuck.setStroke(theme.secondary)
            previewPuck.skin = skin
            previewPuck.drawTo(canvas)

            val unlocked = BallStyleFactory.isUnlocked(type, Settings.adsLeft)
            if (!unlocked) drawLock(canvas, cx, cy, pr)

            label.textSize = ratio() * 0.7f
            canvas.drawText(type.name, cx, b[3] - ratio() * 0.85f, label)
            sublabel.textSize = ratio() * 0.45f
            val status = if (unlocked) "Unlocked" else unlockHint(type)
            canvas.drawText(status, cx, b[3] - ratio() * 0.3f, sublabel)
        }

        Settings.screenRatio = originalRatio
        postInvalidateOnAnimation()
    }

    private fun unlockHint(type: BallType): String = when (type) {
        BallType.Prism, BallType.Plasma -> "Unlock all ads"
        else -> {
            val threshold = 100 - type.ordinal * 10
            "Ads ≤ $threshold"
        }
    }

    private fun drawLock(canvas: Canvas, lx: Float, ly: Float, radius: Float) {
        canvas.drawCircle(lx, ly, radius, lockFill)
        lockPaint.strokeWidth = ratio() * 0.22f
        val bodyW = radius * 0.8f
        val bodyH = radius * 0.7f
        lockPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(lx - bodyW / 2f, ly - bodyH / 4f, lx + bodyW / 2f, ly + bodyH * 0.75f,
            ratio() * 0.15f, ratio() * 0.15f, lockPaint)
        lockPaint.style = Paint.Style.STROKE
        val shackleR = bodyW / 2.6f
        canvas.drawArc(lx - shackleR, ly - bodyH * 0.85f, lx + shackleR, ly - bodyH * 0.1f,
            180f, 180f, false, lockPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = event.y - lastTouchY
                    lastTouchY = event.y
                    scrollY -= dy
                    if (scrollY < 0f) scrollY = 0f
                    val max = maxScroll()
                    if (scrollY > max) scrollY = max
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

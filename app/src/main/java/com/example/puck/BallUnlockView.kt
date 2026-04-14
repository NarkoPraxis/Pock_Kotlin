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
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import utility.Storage
import kotlin.math.abs
import kotlin.math.max

class BallUnlockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cardBg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val cardBorder = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val sublabel = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }
    // Plan 03: lockFill circle removed — puck body is already solid black for locked balls
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewPuck = Puck(0f, 0f, 0f, Color.WHITE, Color.WHITE)

    // Plan 02: per-cell bounce state
    private var bouncingIndex: Int = -1
    private var bounceFrame: Int = 0

    // Plan 01: per-slot tail instances; rebuilt when adsLeft changes
    private var tails: Array<TailRenderer>? = null
    private var tailsBuiltForAdsLeft: Int = -1

    private var scrollY: Float = 0f
    private var dragging: Boolean = false
    private var lastTouchY: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragDistance: Float = 0f
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

    // Plan 01: rebuild per-slot tail array when adsLeft changes (new unlock happened)
    private fun ensureTails() {
        val adsLeft = Settings.adsLeft
        if (tails == null || tailsBuiltForAdsLeft != adsLeft) {
            tails?.forEach { it.clear() }
            val types = BallType.values()
            tails = Array(types.size) { i ->
                val type = types[i]
                val theme = if (i % 2 == 0) ColorTheme.Warm else ColorTheme.Cold
                BallStyleFactory.build(type, theme).second
            }
            tailsBuiltForAdsLeft = adsLeft
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensurePaints()
        if (!paintsReady) return

        // Plan 00: re-apply light/dark colors each frame
        cardBg.color = if (Storage.darkMode) Color.argb(220, 22, 22, 34) else Color.argb(220, 232, 232, 248)
        label.color = if (Storage.darkMode) Color.WHITE else Color.argb(230, 15, 15, 35)
        sublabel.color = if (Storage.darkMode) Color.argb(160, 255, 255, 255) else Color.argb(160, 20, 20, 50)

        ensureTails()

        val savedRatio = Settings.screenRatio
        Settings.screenRatio = ratio()

        // Plan 02: advance bounce frame once per draw (not per cell)
        if (bouncingIndex >= 0) bounceFrame++

        val types = BallType.values()
        val cs = cellSize()
        // Plan 01: use ratio() * 1.2f to match in-game ball size at screenRatio scale
        val pr = ratio() * 1.2f

        for (i in types.indices) {
            val b = cellBounds(i)
            if (b[3] < 0 || b[1] > height) continue
            val type = types[i]
            val theme = if (i % 2 == 0) ColorTheme.Warm else ColorTheme.Cold

            val cx = (b[0] + b[2]) / 2f
            // Plan 02: cell center Y offset upward slightly (like the popup)
            val baseCy = (b[1] + b[3]) / 2f - ratio() * 0.4f
            val bounce = if (i == bouncingIndex) {
                val period = 70f
                val amplitude = ratio() * 0.55f
                (amplitude * kotlin.math.sin(2 * Math.PI.toFloat() * bounceFrame / period)).toFloat()
            } else 0f
            val puckY = baseCy - bounce

            // 1. Card background + border
            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBg)
            cardBorder.color = theme.primary
            cardBorder.strokeWidth = ratio() * 0.14f
            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBorder)

            // Set up previewPuck for this slot
            val unlocked = BallStyleFactory.isUnlocked(type, Settings.adsLeft)
            val (skin, _) = BallStyleFactory.build(type, theme)
            previewPuck.x = cx
            previewPuck.y = puckY
            previewPuck.radius = pr
            previewPuck.frame = bounceFrame
            previewPuck.setFill(theme.primary)
            previewPuck.setStroke(theme.secondary)
            previewPuck.skin = skin
            previewPuck.isPlaceholder = !unlocked  // Plan 03

            // 2. Tail render (only when this ball is bouncing — Plan 01 + Plan 02)
            if (i == bouncingIndex) {
                tails?.get(i)?.renderForPreview(canvas, previewPuck, shielded = false, launched = false, baseFillColor = theme.primary)
            }

            // 3. Puck body
            previewPuck.drawTo(canvas)

            // 4. Lock overlay (Plan 03: no semi-transparent circle — puck is already a solid silhouette)
            if (!unlocked) drawLock(canvas, cx, puckY, pr)

            // 5. Name label + status (static — not affected by bounce)
            label.textSize = ratio() * 0.7f
            canvas.drawText(type.name, cx, b[3] - ratio() * 0.85f, label)
            sublabel.textSize = ratio() * 0.45f
            val status = if (unlocked) "Unlocked" else unlockHint(type)
            canvas.drawText(status, cx, b[3] - ratio() * 0.3f, sublabel)
        }

        Settings.screenRatio = savedRatio
        postInvalidateOnAnimation()
    }

    private fun unlockHint(type: BallType): String = when (type) {
        BallType.Prism, BallType.Plasma -> "Unlock all ads"
        else -> {
            val threshold = 100 - type.ordinal * 10
            "Ads ≤ $threshold"
        }
    }

    // Plan 03: removed canvas.drawCircle(lockFill) — puck body is already solid black for locked balls
    private fun drawLock(canvas: Canvas, lx: Float, ly: Float, radius: Float) {
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
                downX = event.x
                downY = event.y
                dragDistance = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = event.y - lastTouchY
                    dragDistance += abs(dy)
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
                val wasDragging = dragging
                dragging = false
                // Plan 02: detect tap (minimal movement) and set the tapped cell as the bouncing one
                if (wasDragging && dragDistance < ratio() * 0.5f) {
                    val types = BallType.values()
                    for (i in types.indices) {
                        val b = cellBounds(i)
                        if (downX >= b[0] && downX <= b[2] && downY >= b[1] && downY <= b[3]) {
                            if (bouncingIndex != i) {
                                tails?.getOrNull(bouncingIndex)?.clear()
                            }
                            bouncingIndex = i
                            bounceFrame = 0
                            break
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Clear particle tails when the activity pauses to prevent stale particle accumulation
    fun clearTails() {
        tails?.forEach { it.clear() }
        bouncingIndex = -1
        bounceFrame = 0
    }
}

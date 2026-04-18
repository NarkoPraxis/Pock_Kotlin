package com.example.puck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import utility.Storage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class BallUnlockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cardBg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val cardBorder = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val sublabel = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }
    // Plan 03: lockFill circle removed — puck body is already solid black for locked balls
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewRenderer = PuckRenderer()

    // Plan 02: per-cell bounce state
    private var bouncingIndex: Int = -1
    private var bounceFrame: Int = 0

    // Plan 01: per-slot skin+tail instances; rebuilt when unlockProgress changes.
    // Skins are cached so randomized seeds (e.g. GalaxySkin.starSeeds) don't re-roll each frame.
    private var tails: Array<TailRenderer>? = null
    private var skins: Array<PuckSkin>? = null
    private var tailsBuiltForProgress: Int = -1

    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            invalidate()
            animHandler.postDelayed(this, 16L)
        }
    }

    private var scrollY: Float = 0f
    private var dragging: Boolean = false
    private var lastTouchY: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragDistance: Float = 0f
    private var paintsReady: Boolean = false

    private val columns: Int = 2
    private val rows: Int = (BallType.values().size + columns - 1) / columns

    // Per-cell warm/cold toggle; default mirrors the original left=warm, right=cold layout
    private val warmFlags = BooleanArray(BallType.values().size) { i -> i % 2 == 0 }
    private fun themeForCell(i: Int): ColorTheme = if (warmFlags[i]) ColorTheme.Warm else ColorTheme.Cold

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

    // Plan 01: rebuild per-slot skin+tail arrays when unlockProgress changes (new unlock happened)
    private fun ensureTails() {
        val progress = Settings.unlockProgress
        if (tails == null || tailsBuiltForProgress != progress) {
            tails?.forEach { it.clear() }
            val types = BallType.values()
            val styles = Array(types.size) { i -> BallStyleFactory.buildStyle(types[i], themeForCell(i)) }
            tails = Array(types.size) { i -> styles[i].tail }
            skins = Array(types.size) { i -> styles[i].skin }
            tailsBuiltForProgress = progress
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val savedRatio = Settings.screenRatio
        val savedStrokeWidth = Settings.strokeWidth
        Settings.screenRatio = ratio()
        Settings.strokeWidth = ratio() / 4f
        ensurePaints()
        if (!paintsReady) { Settings.screenRatio = savedRatio; Settings.strokeWidth = savedStrokeWidth; return }

        // re-apply light/dark colors each frame
        cardBg.color = if (Storage.darkMode) Color.argb(220, 22, 22, 34) else Color.argb(220, 232, 232, 248)
        label.color = if (Storage.darkMode) Color.WHITE else Color.argb(230, 15, 15, 35)
        sublabel.color = if (Storage.darkMode) Color.argb(160, 255, 255, 255) else Color.argb(160, 20, 20, 50)

        ensureTails()

        bounceFrame++

        val types = BallType.values()
        val pr = ratio() * 1.2f

        previewRenderer.effectEnabled = false
        previewRenderer.effect = null
        // strokeWidth must be synced each frame — the renderer is constructed before Settings.strokeWidth
        // is set by initializeSettings(), so the baked-in value is 0f.
        previewRenderer.strokePaint.strokeWidth = ratio() / 4f
        previewRenderer.chargePaint.strokeWidth = ratio() / 4f

        for (i in types.indices) {
            val b = cellBounds(i)
            if (b[3] < 0 || b[1] > height) continue
            val type = types[i]
            val theme = themeForCell(i)

            val cx = (b[0] + b[2]) / 2f
            // cell center Y offset upward slightly (like the popup)
            val baseCy = (b[1] + b[3]) / 2f - ratio() * 0.4f
            val amplitude = if (i == bouncingIndex) ratio() * 1.1f else ratio() * 0.5f
            val phase = i * 0.7f
            val puckY = baseCy + amplitude * sin(2 * Math.PI.toFloat() * bounceFrame / 80f + phase)

            // 1. Card background + border
            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBg)
            cardBorder.color = theme.primary
            cardBorder.strokeWidth = ratio() * 0.24f
            canvas.drawRoundRect(b[0], b[1], b[2], b[3], ratio() * 0.4f, ratio() * 0.4f, cardBorder)

            // Configure previewRenderer for this slot
            val unlocked = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)
            previewRenderer.x = cx
            previewRenderer.y = puckY
            previewRenderer.radius = pr
            previewRenderer.frame = bounceFrame
            previewRenderer.fillColor = theme.primary
            previewRenderer.strokeColor = theme.secondary
            previewRenderer.baseFillColor = theme.primary
            previewRenderer.skin = skins?.get(i)
            previewRenderer.tail = tails?.get(i)
            previewRenderer.preview = !unlocked

            // 2. Draw puck clipped to card bounds so tail can't escape into adjacent cells
            canvas.save()
            canvas.clipRect(b[0], b[1], b[2], b[3])
            previewRenderer.draw(canvas)
            canvas.restore()

            // 3. Lock overlay
            if (!unlocked) drawLock(canvas, cx, puckY, pr)

            // 4. Name label + status (static — not affected by bounce)
            label.textSize = ratio() * 0.7f
            canvas.drawText(type.name, cx, b[3] - ratio() * 0.85f, label)
            sublabel.textSize = ratio() * 0.45f
            val status = if (unlocked) "Unlocked" else unlockHint(type)
            canvas.drawText(status, cx, b[3] - ratio() * 0.3f, sublabel)
        }

        Settings.screenRatio = savedRatio
        Settings.strokeWidth = savedStrokeWidth
    }

    private fun unlockHint(type: BallType): String = when (type) {
        BallType.Prism, BallType.Plasma -> "Reach 100%"
        else -> "Reach ${type.ordinal * 10}%"
    }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animHandler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animHandler.removeCallbacksAndMessages(null)
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
                            if (bouncingIndex == i) {
                                // Second tap on the already-bouncing cell: toggle warm/cold theme
                                warmFlags[i] = !warmFlags[i]
                                tails?.getOrNull(i)?.clear()
                                val toggledStyle = BallStyleFactory.buildStyle(types[i], themeForCell(i))
                                tails?.set(i, toggledStyle.tail)
                                skins?.set(i, toggledStyle.skin)
                                bounceFrame = 0
                            } else {
                                // First tap or switching to a new cell
                                tails?.getOrNull(bouncingIndex)?.clear()
                                tails?.getOrNull(i)?.clear()
                                bouncingIndex = i
                                bounceFrame = 0
                            }
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

package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt

class BallSelectionPopup(val isHigh: Boolean) {

    var isOpen: Boolean = false

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val slotBg = Paint().apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL; isAntiAlias = true }
    private val slotBgSel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewRenderer = PuckRenderer()

    private var snapIndex: Int = 0
    private var bounceFrame: Int = 0

    // Per-slot tails — one per BallType, rendered for all balls at all times
    private val slotTails: Array<TailRenderer?> = arrayOfNulls(BallType.values().size)
    private val slotTailTypes: Array<BallType?> = arrayOfNulls(BallType.values().size)

    val w: Float get() = Settings.screenWidth.toFloat()
    val h: Float get() = Settings.screenRatio * 3.8f
    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom + h / 2f else Settings.bottomGoalTop - h / 2f

    private val slotW: Float get() = Settings.screenRatio * 4f

    private var scrollX: Float = 0f
    private var dragging: Boolean = false
    private var lastLogicalX: Float = 0f
    private var dragDistance: Float = 0f

    // Plan 04: expose the ball nearest to center for live card label update while scrolling
    val previewType: BallType get() {
        val idx = (scrollX / slotW).roundToInt().coerceIn(0, BallType.values().size - 1)
        return BallType.values()[idx]
    }

    fun open() {
        isOpen = true
        val current = if (isHigh) Settings.highBallType else Settings.lowBallType
        scrollX = current.ordinal * slotW
        snapIndex = current.ordinal
        bounceFrame = 0
        dragging = false
        slotTails[snapIndex]?.clear()   // reseed selected tail from current puck position on open
    }

    fun close() {
        isOpen = false
        dragging = false
        utility.Logic.countDownTicker.reset()
        utility.Drawing.countDownProgressTicker.reset()
        utility.Logic.cdIndex = 0
    }

    private fun isUnlocked(type: BallType): Boolean = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)

    // Plan 04: select ball in-place without closing popup; snap/drag both call this
    private fun trySelect(type: BallType): Boolean {
        if (!isUnlocked(type)) return false
        if (isHigh) { Settings.highBallType = type; Storage.saveHighBallType(type) }
        else { Settings.lowBallType = type; Storage.saveLowBallType(type) }
        utility.Logic.applyBallStyles()
        return true
    }

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    private fun toLogicalX(screenX: Float): Float = if (isHigh) 2f * cx - screenX else screenX

    fun handleTouchEvent(action: Int, x: Float, y: Float): Boolean {
        if (!isOpen) return false
        val masked = action and MotionEvent.ACTION_MASK
        val logicalX = toLogicalX(x)
        val types = BallType.values()

        when (masked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Tap outside popup closes it (Plan 05: no close X needed)
                if (!hitTest(x, y)) { close(); return true }
                dragging = true
                lastLogicalX = logicalX
                dragDistance = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val dx = logicalX - lastLogicalX
                lastLogicalX = logicalX
                scrollX -= dx
                dragDistance += abs(dx)
                clampScroll()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return true
                dragging = false
                if (dragDistance < Settings.screenRatio * 0.6f) {
                    // Tap: snap to tapped slot, select if unlocked — popup stays open
                    val slotLogicalX = logicalX - (cx - scrollX)
                    val index = (slotLogicalX / slotW).toInt().coerceIn(0, types.size - 1)
                    scrollX = index * slotW
                    if (index != snapIndex) {
                        snapIndex = index
                        bounceFrame = 0
                        slotTails[snapIndex]?.clear()
                    }
                    trySelect(types[index])
                } else {
                    // Drag release: snap to nearest, auto-select if unlocked
                    val snap = (scrollX / slotW).roundToInt().coerceIn(0, types.size - 1)
                    scrollX = snap * slotW
                    if (snap != snapIndex) {
                        snapIndex = snap
                        bounceFrame = 0
                        slotTails[snapIndex]?.clear()
                    }
                    trySelect(types[snapIndex])
                }
                return true
            }
        }
        return true
    }

    private fun clampScroll() {
        val max = (BallType.values().size - 1) * slotW
        if (scrollX < 0f) scrollX = 0f
        if (scrollX > max) scrollX = max
    }

    private fun bounceOffset(): Float {
        val period = 40f
        val amplitude = Settings.screenRatio * 2.5f
        return abs(amplitude * kotlin.math.sin(2 * Math.PI.toFloat() * bounceFrame / period))
    }

    fun drawTo(canvas: Canvas) {
        if (!isOpen) return

        // Plan 00: re-apply light/dark colors each frame
        bg.color = if (Storage.darkMode) Color.argb(230, 10, 10, 20) else Color.argb(230, 225, 225, 240)

        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val types = BallType.values()
        val pr = slotW * 0.32f
        val centerIndex = scrollX / slotW

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, cy)

        // Popup background + border
        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, bg)
        border.color = theme.primary
        border.strokeWidth = Settings.screenRatio * 0.25f  // Plan 06: thicker border
        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, border)

        // Plan 02: increment bounce frame once per draw (not per slot)
        if (!dragging) bounceFrame++

        // Shared renderer config: no effect in popup
        // strokeWidth must be synced each frame — the renderer is constructed before Settings.strokeWidth
        // is set by initializeSettings(), so the baked-in value is 0f.
        previewRenderer.effectEnabled = false
        previewRenderer.effect = null
        previewRenderer.radius = pr
        previewRenderer.strokePaint.strokeWidth = Settings.strokeWidth
        previewRenderer.chargePaint.strokeWidth = Settings.strokeWidth

        canvas.save()
        canvas.clipRect(
            cx - halfW + Settings.screenRatio * 0.2f, cy - halfH + Settings.screenRatio * 0.2f,
            cx + halfW - Settings.screenRatio * 0.2f, cy + halfH - Settings.screenRatio * 0.2f
        )

        for (i in types.indices) {
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue

            val dist = abs(i - centerIndex)
            val isCenter = dist < 0.5f
            val type = types[i]

            if (isCenter) {
                slotBgSel.color = Color.argb(90, Color.red(theme.primary), Color.green(theme.primary), Color.blue(theme.primary))
                canvas.drawRoundRect(slotCenterX - slotW * 0.45f, cy - halfH + Settings.screenRatio * 0.4f,
                    slotCenterX + slotW * 0.45f, cy + halfH - Settings.screenRatio * 0.4f,
                    Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, slotBgSel)
            } else {
                canvas.drawRoundRect(slotCenterX - slotW * 0.42f, cy - halfH + Settings.screenRatio * 0.5f,
                    slotCenterX + slotW * 0.42f, cy + halfH - Settings.screenRatio * 0.5f,
                    Settings.screenRatio * 0.25f, Settings.screenRatio * 0.25f, slotBg)
            }

            // Build/cache per-slot tail
            if (slotTailTypes[i] != type) {
                slotTails[i]?.clear()
                slotTailTypes[i] = type
                slotTails[i] = BallStyleFactory.buildStyle(type, theme).tail
            }

            // Non-center pucks drawn inside clip without bounce; center drawn after restore.
            // When dragging, snap non-selected tail history to current position (no trailing).
            if (!isCenter || dragging) {
                previewRenderer.x = slotCenterX
                previewRenderer.y = cy
                if (dragging && i != snapIndex) slotTails[i]?.fillTo(slotCenterX, cy)
                previewRenderer.fillColor = theme.primary
                previewRenderer.strokeColor = theme.secondary
                previewRenderer.preview = !isUnlocked(type)
                previewRenderer.skin = BallStyleFactory.buildStyle(type, theme).skin
                previewRenderer.tail = slotTails[i]
                previewRenderer.draw(canvas)
                if (!isUnlocked(type)) drawLock(canvas, slotCenterX, cy, pr)
            }
        }

        canvas.restore()  // end clip

        // Plan 02: draw center (bouncing) ball AFTER clip restore so it can overflow the strip
        if (!dragging) {
            val centerType = types[snapIndex.coerceIn(0, types.size - 1)]
            val slotCenterX = cx - scrollX + snapIndex * slotW
            val puckY = cy - bounceOffset()

            previewRenderer.frame++
            previewRenderer.x = slotCenterX
            previewRenderer.y = puckY
            previewRenderer.fillColor = theme.primary
            previewRenderer.strokeColor = theme.secondary
            previewRenderer.baseFillColor = theme.primary
            previewRenderer.preview = !isUnlocked(centerType)
            previewRenderer.skin = BallStyleFactory.buildStyle(centerType, theme).skin
            // Tail injected for center ball — z-index sort handles draw order
            previewRenderer.tail = slotTails[snapIndex.coerceIn(0, types.size - 1)]
            previewRenderer.draw(canvas)
            if (!isUnlocked(centerType)) drawLock(canvas, slotCenterX, puckY, pr)
        }

        canvas.restore()
    }

    // Plan 03: removed semi-transparent circle overlay — puck body is already a solid silhouette
    private fun drawLock(canvas: Canvas, lx: Float, ly: Float, radius: Float) {
        lockPaint.strokeWidth = Settings.screenRatio * 0.2f
        val bodyW = radius * 0.8f
        val bodyH = radius * 0.7f
        lockPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(lx - bodyW / 2f, ly - bodyH / 4f, lx + bodyW / 2f, ly + bodyH * 0.75f,
            Settings.screenRatio * 0.12f, Settings.screenRatio * 0.12f, lockPaint)
        lockPaint.style = Paint.Style.STROKE
        val shackleR = bodyW / 2.6f
        canvas.drawArc(lx - shackleR, ly - bodyH * 0.85f, lx + shackleR, ly - bodyH * 0.1f,
            180f, 180f, false, lockPaint)
    }
}

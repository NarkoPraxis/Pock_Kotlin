package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import enums.BallType
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import utility.PaintBucket
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt

class BallSelectionPopup(val isHigh: Boolean) {

    var isOpen: Boolean = false

    private val bg = Paint().apply { color = Color.argb(230, 10, 10, 20); style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val closeX = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val label = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val slotBg = Paint().apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL; isAntiAlias = true }
    private val slotBgSel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val lockFill = Paint().apply { color = Color.argb(170, 0, 0, 0); style = Paint.Style.FILL; isAntiAlias = true }
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewPuck = Puck(0f, 0f, 0f, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)

    val w: Float get() = Settings.screenWidth.toFloat()
    val h: Float get() = Settings.screenRatio * 3.8f
    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom + h / 2f else Settings.bottomGoalTop - h / 2f

    private val slotW: Float get() = Settings.screenRatio * 4f
    private val closeSize: Float get() = Settings.screenRatio * 0.9f
    private val closeCx: Float get() = cx + w / 2f - closeSize * 1.2f
    private val closeCy: Float get() = cy - h / 2f + closeSize * 1.2f

    private var scrollX: Float = 0f
    private var dragging: Boolean = false
    private var dragStartLogicalX: Float = 0f
    private var lastLogicalX: Float = 0f
    private var dragDistance: Float = 0f
    private var scrollAtDragStart: Float = 0f

    fun open() {
        isOpen = true
        val current = if (isHigh) Settings.highBallType else Settings.lowBallType
        scrollX = current.ordinal * slotW
        dragging = false
    }

    fun close() { isOpen = false; dragging = false }

    private fun isUnlocked(type: BallType): Boolean = BallStyleFactory.isUnlocked(type, Settings.adsLeft)

    private fun trySelect(type: BallType): Boolean {
        if (!isUnlocked(type)) return false
        if (isHigh) { Settings.highBallType = type; Storage.saveHighBallType(type) }
        else { Settings.lowBallType = type; Storage.saveLowBallType(type) }
        utility.Logic.applyBallStyles()
        close()
        return true
    }

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    private fun toLogicalX(screenX: Float): Float = if (isHigh) 2f * cx - screenX else screenX

    fun handleTouchEvent(action: Int, x: Float, y: Float): Boolean {
        if (!isOpen) return false
        val masked = action and MotionEvent.ACTION_MASK
        val logicalX = toLogicalX(x)

        when (masked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (!hitTest(x, y)) { close(); return true }
                if (abs(x - closeCx) < closeSize * 1.3f && abs(y - closeCy) < closeSize * 1.3f) { close(); return true }
                dragging = true
                dragStartLogicalX = logicalX
                lastLogicalX = logicalX
                dragDistance = 0f
                scrollAtDragStart = scrollX
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
                    val slotLogicalX = logicalX - (cx - scrollX)
                    val index = (slotLogicalX / slotW).toInt().coerceIn(0, BallType.values().size - 1)
                    val type = BallType.values()[index]
                    trySelect(type)
                } else {
                    val snap = (scrollX / slotW).roundToInt().coerceIn(0, BallType.values().size - 1)
                    scrollX = snap * slotW
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

    fun drawTo(canvas: Canvas) {
        if (!isOpen) return
        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, cy)

        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, bg)
        border.color = theme.primary
        border.strokeWidth = Settings.screenRatio * 0.14f
        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, border)

        canvas.save()
        canvas.clipRect(cx - halfW + Settings.screenRatio * 0.2f, cy - halfH + Settings.screenRatio * 0.2f,
            cx + halfW - Settings.screenRatio * 0.2f, cy + halfH - Settings.screenRatio * 0.2f)

        val types = BallType.values()
        val pr = slotW * 0.32f
        val centerIndex = scrollX / slotW

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

            val (skin, _) = BallStyleFactory.build(type, theme)
            previewPuck.x = slotCenterX
            previewPuck.y = cy - Settings.screenRatio * 0.2f
            previewPuck.radius = pr
            previewPuck.frame++
            previewPuck.setFill(theme.primary)
            previewPuck.setStroke(theme.secondary)
            previewPuck.skin = skin
            previewPuck.drawTo(canvas)

            if (!isUnlocked(type)) drawLock(canvas, slotCenterX, cy - Settings.screenRatio * 0.2f, pr)

            label.textSize = Settings.screenRatio * 0.5f
            canvas.drawText(type.name, slotCenterX, cy + halfH - Settings.screenRatio * 0.55f, label)
        }

        canvas.restore()

        drawClose(canvas)
        canvas.restore()
    }

    private fun drawClose(canvas: Canvas) {
        closeX.strokeWidth = Settings.screenRatio * 0.18f
        val s = closeSize * 0.55f
        canvas.drawLine(closeCx - s, closeCy - s, closeCx + s, closeCy + s, closeX)
        canvas.drawLine(closeCx + s, closeCy - s, closeCx - s, closeCy + s, closeX)
    }

    private fun drawLock(canvas: Canvas, lx: Float, ly: Float, radius: Float) {
        canvas.drawCircle(lx, ly, radius, lockFill)
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

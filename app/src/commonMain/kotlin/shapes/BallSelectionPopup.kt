package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.RandomRoll
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class BallSelectionPopup(val isHigh: Boolean) {

    var isOpen: Boolean = false
    var randomRoll: RandomRoll? = null

    private var snapIndex: Int = 0

    private val renderers: Array<PuckRenderer> = Array(BallType.entries.size) { i ->
        BallStyleFactory.buildRenderer(BallType.entries.toTypedArray()[i], ColorTheme.getTheme(isHigh))
    }

    val w: Float get() = Settings.screenWidth
    val h: Float get() = Settings.screenRatio * 5f
    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom + h / 2f else Settings.bottomGoalTop - h / 2f

    private val slotW: Float get() = Settings.screenRatio * 4f

    private val ballTypes = BallType.entries.toTypedArray()

    private var scrollX: Float = 0f
    private var dragging: Boolean = false
    private var lastLogicalX: Float = 0f
    private var dragDistance: Float = 0f

    val previewType: BallType get() {
        val idx = (scrollX / slotW).roundToInt().coerceIn(0, BallType.values().size - 1)
        return BallType.entries[idx]
    }

    fun open() {
        isOpen = true
        val current = if (isHigh) Settings.highBallType else Settings.lowBallType
        scrollX = current.ordinal * slotW
        snapIndex = current.ordinal
        dragging = false
        renderers[snapIndex].tail.clear()

        if (current == BallType.Random) {
            utility.Logic.applyBallStyles()
        }
        val storedRoll = if (isHigh) Settings.highRandomRoll else Settings.lowRandomRoll
        renderers[BallType.Random.ordinal] = BallStyleFactory.buildRenderer(BallType.Random, ColorTheme.getTheme(isHigh), storedRoll)
    }

    fun close() {
        isOpen = false
        dragging = false
    }

    private fun isUnlocked(type: BallType): Boolean = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)

    private fun trySelect(type: BallType): Boolean {
        if (!isUnlocked(type)) return false
        if (isHigh) {
            Settings.highBallType = type
            Storage.saveHighBallType(type)
        } else {
            Settings.lowBallType = type
            Storage.saveLowBallType(type)
        }
        utility.Logic.applyBallStyles()
        if (type == BallType.Random) {
            val storedRoll = if (isHigh) Settings.highRandomRoll else Settings.lowRandomRoll
            renderers[BallType.Random.ordinal] = BallStyleFactory.buildRenderer(BallType.Random, ColorTheme.getTheme(isHigh), storedRoll)
        }
        return true
    }

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    private fun toLogicalX(screenX: Float): Float = if (isHigh) 2f * cx - screenX else screenX

    fun handleTouchEvent(action: Int, x: Float, y: Float): Boolean {
        if (!isOpen) return false
        val masked = action and ACTION_MASK
        val logicalX = toLogicalX(x)
        val types = ballTypes

        when (masked) {
            ACTION_DOWN, ACTION_POINTER_DOWN -> {
                if (!hitTest(x, y)) return false
                dragging = true
                lastLogicalX = logicalX
                dragDistance = 0f
                return true
            }
            ACTION_MOVE -> {
                if (!dragging) return true
                val dx = logicalX - lastLogicalX
                lastLogicalX = logicalX
                scrollX -= dx
                dragDistance += abs(dx)
                clampScroll()
                return true
            }
            ACTION_UP, ACTION_POINTER_UP, ACTION_CANCEL -> {
                if (!dragging) return true
                dragging = false
                if (dragDistance < Settings.screenRatio * 0.6f) {
                    val slotLogicalX = logicalX - (cx - scrollX)
                    val index = (slotLogicalX / slotW).roundToInt().coerceIn(0, types.size - 1)
                    scrollX = index * slotW
                    if (index != snapIndex) {
                        snapIndex = index
                        renderers[snapIndex].tail.clear()
                    }
                    trySelect(types[index])
                } else {
                    val snap = (scrollX / slotW).roundToInt().coerceIn(0, types.size - 1)
                    scrollX = snap * slotW
                    if (snap != snapIndex) {
                        snapIndex = snap
                        renderers[snapIndex].tail.clear()
                    }
                    trySelect(types[snapIndex])
                }
                return true
            }
        }
        return true
    }

    private fun clampScroll() {
        val max = (BallType.entries.size - 1) * slotW
        if (scrollX < 0f) scrollX = 0f
        if (scrollX > max) scrollX = max
    }

    fun DrawScope.drawTo() {
        if (!isOpen) return

        val bgArgb = if (Storage.darkMode) Palette.argb(230, 10, 10, 20) else Palette.WHITE
        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val types = ballTypes
        val pr = Settings.ballRadius
        val centerIndex = scrollX / slotW
        val puckYs = FloatArray(types.size)
        val outerClipMargin = Settings.screenRatio * 0.2f
        val innerClipMargin = Settings.screenRatio * 0.15f

        val canvas = drawContext.canvas

        // Apply mirror transform for high player
        canvas.save()
        if (isHigh) {
            canvas.translate(cx, cy)
            canvas.scale(-1f, -1f)
            canvas.translate(-cx, -cy)
        }

        // Background
        drawRect(
            color = Color(bgArgb),
            topLeft = Offset(cx - halfW, cy - halfH),
            size = Size(w, h)
        )

        // Border
        drawRect(
            color = Color(theme.main.primary),
            topLeft = Offset(cx - halfW, cy - halfH),
            size = Size(w, h),
            style = Stroke(Settings.screenRatio * 0.25f)
        )

        // Slot background chips and compute puck Y positions
        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            cy - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            cy + halfH - outerClipMargin
        ))

        for (i in types.indices) {
            val previewRenderer = renderers[i]
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue

            val dist = abs(i - centerIndex)
            val isCenter = dist < 0.5f

            // Slot chip background
            val chipColor = if (isCenter) {
                Color(Palette.withAlpha(theme.main.primary, 90))
            } else {
                Color(Palette.withAlpha(Palette.WHITE, 60))
            }
            val rx = Settings.screenRatio * if (isCenter) 0.3f else 0.25f
            val chipHalfW = slotW * if (isCenter) 0.45f else 0.42f
            val chipTopOff = Settings.screenRatio * if (isCenter) 1.25f else 1.35f
            val chipHeight = h - chipTopOff * 2f

            val chipPaint = Paint().apply {
                color = chipColor
                style = PaintingStyle.Fill
            }
            canvas.drawRoundRect(
                slotCenterX - chipHalfW, cy - halfH + chipTopOff,
                slotCenterX + chipHalfW, cy - halfH + chipTopOff + chipHeight,
                rx, rx, chipPaint
            )

            // Compute puck Y for second pass
            previewRenderer.frame++
            previewRenderer.effectEnabled = false
            previewRenderer.radius = pr
            previewRenderer.strokeWidth = Settings.strokeWidth
            val amplitude = if (isCenter) Settings.screenRatio * 0.9f else Settings.screenRatio * 0.45f
            val phase = i * 0.7f
            puckYs[i] = cy + amplitude * sin(2 * kotlin.math.PI.toFloat() * previewRenderer.frame / 80f + phase)
        }

        canvas.restore() // restore slot chip clip

        // Draw puck renderers per slot (with per-slot clip)
        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            cy - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            cy + halfH - outerClipMargin
        ))

        for (i in types.indices) {
            val previewRenderer = renderers[i]
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue
            val type = types[i]
            val puckY = puckYs[i]

            canvas.save()
            canvas.clipRect(Rect(
                slotCenterX - slotW / 2f,
                cy - halfH + innerClipMargin,
                slotCenterX + slotW / 2f,
                cy + halfH - innerClipMargin
            ))

            previewRenderer.x = slotCenterX
            previewRenderer.y = puckY
            previewRenderer.fillColor = theme.main.primary
            previewRenderer.strokeColor = theme.main.secondary

            with(previewRenderer) { draw() }

            if (!isUnlocked(type)) drawLock(slotCenterX, puckY, pr)

            canvas.restore()
        }

        canvas.restore() // restore puck clip
        canvas.restore() // restore mirror transform
    }

    private fun DrawScope.drawLock(lx: Float, ly: Float, radius: Float) {
        val strokeWidth = Settings.screenRatio * 0.2f
        val bodyW = radius * 0.8f
        val bodyH = radius * 0.7f

        // Lock body — filled white rounded rect
        val bodyPaint = Paint().apply {
            color = Color.White
            style = PaintingStyle.Fill
        }
        drawContext.canvas.drawRoundRect(
            lx - bodyW / 2f, ly - bodyH / 4f, lx + bodyW / 2f, ly + bodyH * 0.75f,
            Settings.screenRatio * 0.12f, Settings.screenRatio * 0.12f,
            bodyPaint
        )

        // Shackle — stroked white arc
        val shackleR = bodyW / 2.6f
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lx - shackleR, ly - bodyH * 0.85f),
            size = Size(shackleR * 2f, bodyH * 0.75f),
            style = Stroke(strokeWidth)
        )
    }

    companion object {
        const val ACTION_MASK = 0xff
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }
}

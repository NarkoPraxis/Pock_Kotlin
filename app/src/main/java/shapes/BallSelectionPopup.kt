package shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.RandomRoll
import gameobjects.puckstyle.PuckRenderer
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class BallSelectionPopup(val isHigh: Boolean) {

    var isOpen: Boolean = false
    var randomRoll: RandomRoll? = null

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val slotBg = Paint().apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL; isAntiAlias = true }
    private val slotBgSel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
    private val stubFillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val stubStrokePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }

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
        val masked = action and MotionEvent.ACTION_MASK
        val logicalX = toLogicalX(x)
        val types = ballTypes

        when (masked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (!hitTest(x, y)) return false
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

        val bgArgb = if (Storage.darkMode) Color.argb(230, 10, 10, 20) else Color.WHITE
        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val types = ballTypes
        val pr = Settings.ballRadius
        val centerIndex = scrollX / slotW

        drawIntoCanvas { outerCanvas ->
            val canvas = outerCanvas.nativeCanvas

            canvas.save()
            if (isHigh) canvas.scale(-1f, -1f, cx, cy)

            // Background
            bg.color = bgArgb
            canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, bg)

            // Border
            border.color = theme.main.primary
            border.strokeWidth = Settings.screenRatio * 0.25f
            canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, border)

            canvas.save()
            canvas.clipRect(
                cx - halfW + Settings.screenRatio * 0.2f,
                cy - halfH + Settings.screenRatio * 0.2f,
                cx + halfW - Settings.screenRatio * 0.2f,
                cy + halfH - Settings.screenRatio * 0.2f
            )

            for (i in types.indices) {
                val previewRenderer = renderers[i]
                val slotCenterX = cx - scrollX + i * slotW
                if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue

                val dist = abs(i - centerIndex)
                val isCenter = dist < 0.5f
                val type = types[i]

                if (isCenter) {
                    slotBgSel.color = Color.argb(90, Color.red(theme.main.primary), Color.green(theme.main.primary), Color.blue(theme.main.primary))
                    canvas.drawRoundRect(
                        RectF(slotCenterX - slotW * 0.45f, cy - halfH + Settings.screenRatio * 1.25f,
                            slotCenterX + slotW * 0.45f, cy + halfH - Settings.screenRatio * 1.25f),
                        Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, slotBgSel
                    )
                } else {
                    canvas.drawRoundRect(
                        RectF(slotCenterX - slotW * 0.42f, cy - halfH + Settings.screenRatio * 1.35f,
                            slotCenterX + slotW * 0.42f, cy + halfH - Settings.screenRatio * 1.35f),
                        Settings.screenRatio * 0.25f, Settings.screenRatio * 0.25f, slotBg
                    )
                }

                previewRenderer.frame++
                previewRenderer.effectEnabled = false
                previewRenderer.radius = pr
                previewRenderer.strokePaint.strokeWidth = Settings.strokeWidth
                previewRenderer.chargePaint.strokeWidth = Settings.strokeWidth

                val amplitude = if (isCenter) Settings.screenRatio * 0.9f else Settings.screenRatio * 0.45f
                val phase = i * 0.7f
                val puckY = cy + amplitude * sin(2 * Math.PI.toFloat() * previewRenderer.frame / 80f + phase)

                canvas.save()
                canvas.clipRect(
                    slotCenterX - slotW / 2f,
                    cy - halfH + Settings.screenRatio * 0.15f,
                    slotCenterX + slotW / 2f,
                    cy + halfH - Settings.screenRatio * 0.15f
                )

                // Step 10 stub: solid-color circle until skins are migrated in step 11
                stubFillPaint.color = theme.main.primary
                stubStrokePaint.color = theme.main.secondary
                stubStrokePaint.strokeWidth = Settings.strokeWidth
                canvas.drawCircle(slotCenterX, puckY, pr, stubFillPaint)
                canvas.drawCircle(slotCenterX, puckY, pr, stubStrokePaint)

                if (!isUnlocked(type)) drawLock(canvas, slotCenterX, puckY, pr)
                canvas.restore()
            }

            canvas.restore()
            canvas.restore()
        }
    }

    private fun drawLock(canvas: android.graphics.Canvas, lx: Float, ly: Float, radius: Float) {
        lockPaint.strokeWidth = Settings.screenRatio * 0.2f
        val bodyW = radius * 0.8f
        val bodyH = radius * 0.7f
        lockPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            RectF(lx - bodyW / 2f, ly - bodyH / 4f, lx + bodyW / 2f, ly + bodyH * 0.75f),
            Settings.screenRatio * 0.12f, Settings.screenRatio * 0.12f, lockPaint
        )
        lockPaint.style = Paint.Style.STROKE
        val shackleR = bodyW / 2.6f
        canvas.drawArc(
            RectF(lx - shackleR, ly - bodyH * 0.85f, lx + shackleR, ly - bodyH * 0.1f),
            180f, 180f, false, lockPaint
        )
    }
}

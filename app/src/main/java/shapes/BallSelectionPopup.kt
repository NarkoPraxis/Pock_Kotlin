package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyle
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.RandomRoll
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.graphics.withClip

class BallSelectionPopup(val isHigh: Boolean) {

    var isOpen: Boolean = false
    var randomRoll: RandomRoll? = null

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val slotBg = Paint().apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL; isAntiAlias = true }
    private val slotBgSel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val lockPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

    private val previewRenderer = PuckRenderer()

    private var snapIndex: Int = 0

    // Per-slot skins+tails — one per BallType, skin cached so randomized seeds don't re-roll each frame
    private val slotTails: Array<TailRenderer?> = arrayOfNulls(BallType.values().size)
    private val slotSkins: Array<PuckSkin?> = arrayOfNulls(BallType.values().size)
    private val slotStyles: Array<BallStyle?> = arrayOfNulls(BallType.values().size)
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
        dragging = false
        slotTails[snapIndex]?.clear()   // reseed selected tail from current puck position on open

        val randomIdx = BallType.Random.ordinal
        val popupTheme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        slotTails[randomIdx]?.clear()
        randomRoll = BallStyleFactory.rollRandom()
        val randomStyle = BallStyleFactory.buildFromRoll(randomRoll!!, popupTheme, previewRenderer)
        slotTails[randomIdx]     = randomStyle.tail
        slotSkins[randomIdx]     = randomStyle.skin
        slotStyles[randomIdx]    = randomStyle
        slotTailTypes[randomIdx] = BallType.Random

        if (current == BallType.Random) {
            utility.Logic.applyBallStyles()
        }
    }

    fun close() {
        isOpen = false
        dragging = false
    }

    private fun isUnlocked(type: BallType): Boolean = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)

    // Plan 04: select ball in-place without closing popup; snap/drag both call this
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
                    // Tap: snap to tapped slot, select if unlocked — popup stays open
                    val slotLogicalX = logicalX - (cx - scrollX)
                    val index = (slotLogicalX / slotW).roundToInt().coerceIn(0, types.size - 1)
                    scrollX = index * slotW
                    if (index != snapIndex) {
                        snapIndex = index
                        slotTails[snapIndex]?.clear()
                    }
                    trySelect(types[index])
                } else {
                    // Drag release: snap to nearest, auto-select if unlocked
                    val snap = (scrollX / slotW).roundToInt().coerceIn(0, types.size - 1)
                    scrollX = snap * slotW
                    if (snap != snapIndex) {
                        snapIndex = snap
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

    fun drawTo(canvas: Canvas) {
        if (!isOpen) return

        // Plan 00: re-apply light/dark colors each frame
        bg.color = if (Storage.darkMode) Color.argb(230, 10, 10, 20) else Color.argb(230, 225, 225, 240)

        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val types = BallType.values()
        val pr = Settings.ballRadius
        val centerIndex = scrollX / slotW

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, cy)

        // Popup background + border
        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, bg)
        border.color = theme.main.primary
        border.strokeWidth = Settings.screenRatio * 0.25f  // Plan 06: thicker border
        canvas.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, border)

        previewRenderer.frame++

        // Shared renderer config: effects gated by effectEnabled=false; alwaysVisible paddles still draw.
        // strokeWidth must be synced each frame — the renderer is constructed before Settings.strokeWidth
        // is set by initializeSettings(), so the baked-in value is 0f.
        previewRenderer.effectEnabled = false
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
                slotBgSel.color = Color.argb(90, Color.red(theme.main.primary), Color.green(theme.main.primary), Color.blue(theme.main.primary))
                canvas.drawRoundRect(slotCenterX - slotW * 0.45f, cy - halfH + Settings.screenRatio * 0.4f,
                    slotCenterX + slotW * 0.45f, cy + halfH - Settings.screenRatio * 0.4f,
                    Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, slotBgSel)
            } else {
                canvas.drawRoundRect(slotCenterX - slotW * 0.42f, cy - halfH + Settings.screenRatio * 0.5f,
                    slotCenterX + slotW * 0.42f, cy + halfH - Settings.screenRatio * 0.5f,
                    Settings.screenRatio * 0.25f, Settings.screenRatio * 0.25f, slotBg)
            }

            // Build/cache per-slot skin+tail
            if (slotTailTypes[i] != type) {
                slotTails[i]?.clear()
                slotTailTypes[i] = type
                val style = if (type == BallType.Random) {
                    val roll = randomRoll ?: BallStyleFactory.rollRandom().also { randomRoll = it }
                    BallStyleFactory.buildFromRoll(roll, theme, previewRenderer)
                } else {
                    BallStyleFactory.buildStyle(type, theme, previewRenderer)
                }
                slotTails[i]  = style.tail
                slotSkins[i]  = style.skin
                slotStyles[i] = style
            }

            val amplitude = if (isCenter) Settings.screenRatio * 0.9f else Settings.screenRatio * 0.45f
            val phase = i * 0.7f
            val puckY = cy + amplitude * sin(2 * Math.PI.toFloat() * previewRenderer.frame / 80f + phase)

            canvas.withClip(
                slotCenterX - slotW / 2f, cy - halfH + Settings.screenRatio * 0.15f,
                slotCenterX + slotW / 2f, cy + halfH - Settings.screenRatio * 0.15f
            ) {
                previewRenderer.x = slotCenterX
                previewRenderer.y = puckY
                previewRenderer.fillColor = theme.main.primary
                previewRenderer.strokeColor = theme.main.secondary
                previewRenderer.baseFillColor = theme.main.primary
                previewRenderer.preview = !isUnlocked(type)
                previewRenderer.skin = slotSkins[i]
                previewRenderer.tail = slotTails[i]
                previewRenderer.effect = slotStyles[i]?.effect
                previewRenderer.draw(this)
                if (!isUnlocked(type)) drawLock(this, slotCenterX, puckY, pr)
            }
        }

        canvas.restore()  // end global clip

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

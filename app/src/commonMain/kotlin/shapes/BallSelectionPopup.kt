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
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.RandomRoll
import utility.Storage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class BallSelectionPopup(val isHigh: Boolean) : ScrollSnapCarousel() {

    var isOpen: Boolean = false
    var randomRoll: RandomRoll? = null

    private data class Slot(val type: BallType, val customStorageIndex: Int? = null)

    private var slots: List<Slot> = emptyList()
    private val rendererList: MutableList<PuckRenderer> = mutableListOf()

    override val itemCount: Int get() = slots.size

    val w: Float get() = Settings.screenWidth
    val h: Float get() = Settings.screenRatio * 5f
    override val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom + h / 2f else Settings.bottomGoalTop - h / 2f

    override val slotW: Float get() = Settings.screenRatio * 4f

    override fun toLogicalX(screenX: Float): Float = if (isHigh) 2f * Settings.middleX - screenX else screenX

    val previewType: BallType get() = slots.getOrNull(snapIndex)?.type ?: BallType.Classic
    val previewCustomIndex: Int? get() = slots.getOrNull(snapIndex)?.customStorageIndex

    override fun onSnappedTo(index: Int) {
        rendererList.getOrNull(index)?.tail?.clear()
        val slot = slots.getOrNull(index) ?: return
        trySelect(slot)
    }

    fun open() {
        isOpen = true

        // Only show custom slots that are both unlocked and have a saved ball. No full ball-type
        // list and no locked entries — ads are accessed only in the Custom Ball Creator.
        slots = (0 until Storage.SLOT_COUNT).mapNotNull { i ->
            if (Storage.isSlotUnlocked(i) && Storage.loadCustomBall(i) != null) Slot(BallType.Random, i) else null
        }

        rendererList.clear()
        for (slot in slots) {
            val config = Storage.loadCustomBall(slot.customStorageIndex!!)!!
            rendererList.add(BallStyleFactory.buildCustomRenderer(config, ColorTheme.getTheme(isHigh)))
        }

        val currentCustomIdx = if (isHigh) Settings.highCustomBallIndex else Settings.lowCustomBallIndex
        val initialIndex = (currentCustomIdx
            ?.let { idx -> slots.indexOfFirst { it.customStorageIndex == idx }.takeIf { it >= 0 } }
            ?: 0)
            .coerceIn(0, (slots.size - 1).coerceAtLeast(0))

        scrollToIndex(initialIndex)
        rendererList.getOrNull(snapIndex)?.tail?.clear()
    }

    fun close() {
        isOpen = false
        cancelDrag()
    }

    private fun trySelect(slot: Slot): Boolean {
        val idx = slot.customStorageIndex ?: return false
        if (isHigh) {
            Settings.highBallType = BallType.Random
            Settings.highCustomBallIndex = idx
            Storage.saveHighCustomBallIndex(idx)
            Storage.saveHighBallType(BallType.Random)
        } else {
            Settings.lowBallType = BallType.Random
            Settings.lowCustomBallIndex = idx
            Storage.saveLowCustomBallIndex(idx)
            Storage.saveLowBallType(BallType.Random)
        }
        utility.Logic.applyBallStyles()
        return true
    }

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    fun handleTouchEvent(action: Int, x: Float, y: Float): Boolean {
        if (!isOpen) return false
        val masked = action and 0xff
        if (masked == ACTION_DOWN || masked == ACTION_POINTER_DOWN) {
            if (!hitTest(x, y)) return false
        }
        return handleScrollTouchEvent(action, x, y)
    }

    fun DrawScope.drawTo() {
        if (!isOpen) return

        val bgArgb = if (Storage.darkMode) Palette.argb(230, 10, 10, 20) else Palette.WHITE
        val halfW = w / 2f
        val halfH = h / 2f
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val pr = Settings.ballRadius
        val centerIndex = scrollX / slotW
        val puckYs = FloatArray(slots.size)
        val outerClipMargin = Settings.screenRatio * 0.2f
        val innerClipMargin = Settings.screenRatio * 0.15f

        val canvas = drawContext.canvas

        canvas.save()
        if (isHigh) {
            canvas.translate(cx, cy)
            canvas.scale(-1f, -1f)
            canvas.translate(-cx, -cy)
        }

        drawRect(
            color = Color(bgArgb),
            topLeft = Offset(cx - halfW, cy - halfH),
            size = Size(w, h)
        )
        drawRect(
            color = Color(theme.main.primary),
            topLeft = Offset(cx - halfW, cy - halfH),
            size = Size(w, h),
            style = Stroke(Settings.screenRatio * 0.25f)
        )

        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            cy - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            cy + halfH - outerClipMargin
        ))

        for (i in slots.indices) {
            val previewRenderer = rendererList.getOrNull(i) ?: continue
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue

            val dist = abs(i - centerIndex)
            val isCenter = dist < 0.5f

            val chipColor = if (isCenter) Color(Palette.withAlpha(theme.main.primary, 90))
                           else Color(Palette.withAlpha(Palette.WHITE, 60))
            val rx = Settings.screenRatio * if (isCenter) 0.3f else 0.25f
            val chipHalfW = slotW * if (isCenter) 0.45f else 0.42f
            val chipTopOff = Settings.screenRatio * if (isCenter) 1.25f else 1.35f
            val chipHeight = h - chipTopOff * 2f

            val chipPaint = Paint().apply { color = chipColor; style = PaintingStyle.Fill }
            canvas.drawRoundRect(
                slotCenterX - chipHalfW, cy - halfH + chipTopOff,
                slotCenterX + chipHalfW, cy - halfH + chipTopOff + chipHeight,
                rx, rx, chipPaint
            )

            previewRenderer.frame++
            previewRenderer.effectEnabled = false
            previewRenderer.radius = pr
            previewRenderer.strokeWidth = Settings.strokeWidth
            val amplitude = if (isCenter) Settings.screenRatio * 0.9f else Settings.screenRatio * 0.45f
            val phase = i * 0.7f
            puckYs[i] = cy + amplitude * sin(2 * kotlin.math.PI.toFloat() * previewRenderer.frame / 80f + phase)
        }

        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            cy - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            cy + halfH - outerClipMargin
        ))

        for (i in slots.indices) {
            val previewRenderer = rendererList.getOrNull(i) ?: continue
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue
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

            canvas.restore()
        }

        canvas.restore()
        canvas.restore()
    }

    companion object {
        const val ACTION_MASK        = 0xff
        const val ACTION_DOWN        = 0
        const val ACTION_UP          = 1
        const val ACTION_MOVE        = 2
        const val ACTION_CANCEL      = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP  = 6
    }
}

package shapes

import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.abs
import kotlin.math.sin

abstract class ComponentCarousel : ScrollSnapCarousel() {

    val availableTypes: List<BallType> = BallType.entries.filter { it != BallType.Random }
    override val itemCount: Int get() = availableTypes.size

    private val renderers: Array<PuckRenderer?> = arrayOfNulls(availableTypes.size)

    val selectedType: BallType get() = availableTypes.getOrElse(snapIndex) { BallType.Classic }

    protected val theme: ColorTheme = ColorTheme.Warm

    val w: Float get() = Settings.screenWidth
    val h: Float get() = Settings.screenRatio * 5f
    override val cx: Float get() = Settings.middleX

    fun initRenderers() {
        for (i in availableTypes.indices) {
            val r = PuckRenderer(theme)
            r.isHigh = true
            renderers[i] = buildRendererForType(availableTypes[i], r)
        }
    }

    fun clearTailAt(index: Int) {
        renderers.getOrNull(index)?.tail?.clear()
    }

    abstract fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer

    fun isUnlocked(type: BallType): Boolean = BallStyleFactory.isUnlocked(type, Settings.unlockProgress)

    fun DrawScope.drawTo(centerY: Float, frame: Int) {
        if (Settings.screenRatio == 0f) return
        val halfW = w / 2f
        val halfH = h / 2f
        val pr = Settings.ballRadius
        val slotW = this@ComponentCarousel.slotW
        val centerIndex = scrollX / slotW
        val outerClipMargin = Settings.screenRatio * 0.2f
        val innerClipMargin = Settings.screenRatio * 0.15f
        val canvas = drawContext.canvas
        val isDark = utility.Storage.darkMode

        val bgArgb = if (isDark) Palette.argb(230, 10, 10, 20) else Palette.WHITE
        drawRect(
            color = Color(bgArgb),
            topLeft = Offset(cx - halfW, centerY - halfH),
            size = Size(w, h)
        )
        drawRect(
            color = Color(theme.main.primary),
            topLeft = Offset(cx - halfW, centerY - halfH),
            size = Size(w, h),
            style = Stroke(Settings.screenRatio * 0.25f)
        )

        val puckYs = FloatArray(availableTypes.size)

        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            centerY - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            centerY + halfH - outerClipMargin
        ))

        for (i in availableTypes.indices) {
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue

            val dist = abs(i - centerIndex)
            val isCenter = dist < 0.5f

            val chipColor = if (isCenter) Color(Palette.withAlpha(theme.main.primary, 90))
                           else Color(Palette.withAlpha(Palette.WHITE, 60))
            val chipRx = Settings.screenRatio * if (isCenter) 0.3f else 0.25f
            val chipHalfW = slotW * if (isCenter) 0.45f else 0.42f
            val chipTopOff = Settings.screenRatio * if (isCenter) 1.25f else 1.35f
            val chipHeight = h - chipTopOff * 2f

            val chipPaint = Paint().apply { color = chipColor; style = PaintingStyle.Fill }
            canvas.drawRoundRect(
                slotCenterX - chipHalfW, centerY - halfH + chipTopOff,
                slotCenterX + chipHalfW, centerY - halfH + chipTopOff + chipHeight,
                chipRx, chipRx, chipPaint
            )

            val renderer = renderers.getOrNull(i) ?: continue
            val amplitude = if (isCenter) Settings.screenRatio * 0.6f else Settings.screenRatio * 0.3f
            val phase = i * 0.7f
            puckYs[i] = centerY + amplitude * sin(2 * kotlin.math.PI.toFloat() * frame / 80f + phase)
        }

        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect(
            cx - halfW + outerClipMargin,
            centerY - halfH + outerClipMargin,
            cx + halfW - outerClipMargin,
            centerY + halfH - outerClipMargin
        ))

        for (i in availableTypes.indices) {
            val renderer = renderers.getOrNull(i) ?: continue
            val slotCenterX = cx - scrollX + i * slotW
            if (slotCenterX < cx - halfW - slotW || slotCenterX > cx + halfW + slotW) continue
            val type = availableTypes[i]
            val puckY = puckYs[i]

            canvas.save()
            canvas.clipRect(Rect(
                slotCenterX - slotW / 2f,
                centerY - halfH + innerClipMargin,
                slotCenterX + slotW / 2f,
                centerY + halfH - innerClipMargin
            ))

            renderer.x = slotCenterX
            renderer.y = puckY
            renderer.frame = frame
            renderer.radius = pr
            renderer.strokeWidth = Settings.strokeWidth
            renderer.fillColor = theme.main.primary
            renderer.strokeColor = theme.main.secondary
            renderer.baseFillColor = theme.main.primary

            prepareForDraw(renderer, frame)

            with(renderer) { draw() }

            if (!isUnlocked(type)) drawLock(slotCenterX, puckY, pr)

            canvas.restore()
        }

        canvas.restore()
    }

    open fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = false
    }

    private fun DrawScope.drawLock(lx: Float, ly: Float, radius: Float) {
        val lockColor = utility.PaintBucket.shieldPrimary
        val strokeW = Settings.screenRatio * 0.2f
        val bodyW = radius * 0.8f
        val bodyH = radius * 0.7f

        drawRoundRect(
            color = lockColor,
            topLeft = Offset(lx - bodyW / 2f, ly - bodyH / 4f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(Settings.screenRatio * 0.12f)
        )
        val shackleR = bodyW / 2.6f
        drawArc(
            color = lockColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lx - shackleR, ly - bodyH * 0.85f),
            size = Size(shackleR * 2f, bodyH * 0.75f),
            style = Stroke(strokeW)
        )
    }
}

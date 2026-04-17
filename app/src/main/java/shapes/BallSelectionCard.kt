package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.Storage
import kotlin.math.abs

class BallSelectionCard(val isHigh: Boolean, private val popup: BallSelectionPopup) {

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val previewRenderer = PuckRenderer()

    // Plan 02: per-card tail; rebuilt when selected ball type changes
    private var cachedType: BallType? = null
    private var tail: TailRenderer? = null
    // Track popup open→closed transition to reseed tail at correct position
    private var wasPopupOpen: Boolean = false

    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom / 2f + Settings.screenRatio * 2
                          else (Settings.screenHeight + Settings.bottomGoalTop) / 2f - Settings.screenRatio * 2

    val labelcy: Float get() = if (isHigh) Settings.topGoalBottom / 2f
                               else (Settings.screenHeight + Settings.bottomGoalTop) / 2f

    // Plan 04: narrow+tall when popup closed; wide+short pill label when popup open
    val w: Float get() = if (popup.isOpen) Settings.screenRatio * 8f else Settings.screenRatio * 2.8f
    val h: Float get() = if (popup.isOpen) Settings.screenRatio * 2.2f else Settings.topGoalBottom * 1.4f

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    // Plan 02 + 04: abs(sin) for snappy bounce; doubled amplitude; faster period
    private fun bounceOffset(): Float {
        val period = 40f
        val amplitude = Settings.screenRatio * 1.1f
        return abs(amplitude * kotlin.math.sin(2 * Math.PI.toFloat() * previewRenderer.frame / period))
    }

    fun drawTo(canvas: Canvas) {
        // Plan 00: re-apply light/dark colors each frame
        bg.color = if (Storage.darkMode) Color.argb(180, 18, 18, 28) else Color.argb(200, 235, 235, 245)
        label.color = if (Storage.darkMode) Color.WHITE else Color.argb(230, 15, 15, 35)

        // Plan 04: read live preview type from popup while it's open (live label update)
        val type = if (popup.isOpen) popup.previewType
                   else if (isHigh) Settings.highBallType else Settings.lowBallType
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val halfW = w / 2f
        val halfH = h / 2f

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, if (popup.isOpen) labelcy else cy)

        if (popup.isOpen) {
            // Label-only pill: just show the selected ball name, no puck
            label.textSize = Settings.screenRatio * 0.8f
            canvas.drawRoundRect(cx - halfW, labelcy - halfH, cx + halfW, labelcy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
            border.color = theme.primary
            border.strokeWidth = Settings.screenRatio * 0.22f  // Plan 06
            canvas.drawRoundRect(cx - halfW, labelcy - halfH, cx + halfW, labelcy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)
            canvas.drawText(type.name, cx, labelcy + label.textSize / 3f, label)
            wasPopupOpen = true
        } else {
            // Full tall card: puck (bouncing) + name label at bottom

            // Clear tail when transitioning from popup-open so it reseeds at current position
            if (wasPopupOpen) { tail?.clear(); wasPopupOpen = false }

            // Rebuild tail only when type changes (preserves particle history during animation)
            if (cachedType != type) {
                tail?.clear()
                cachedType = type
                tail = BallStyleFactory.buildStyle(type, theme).tail
            }

            val pr = halfW * 0.6f
            previewRenderer.frame++
            val puckY = cy - bounceOffset()
            previewRenderer.x = cx
            previewRenderer.y = puckY
            previewRenderer.radius = pr
            previewRenderer.fillColor = theme.primary
            previewRenderer.strokeColor = theme.secondary
            previewRenderer.baseFillColor = theme.primary
            previewRenderer.skin = BallStyleFactory.buildStyle(type, theme).skin
            previewRenderer.tail = tail
            previewRenderer.effect = null
            previewRenderer.effectEnabled = false

            // Card background + border
            canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
            border.color = theme.primary
            border.strokeWidth = Settings.screenRatio * 0.22f  // Plan 06: thicker border
            canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)

            // z-index sort in renderer handles tail-behind-body draw order
            previewRenderer.draw(canvas)

            label.textSize = Settings.screenRatio * 0.6f
            canvas.drawText(type.name, cx, cy + halfH - Settings.screenRatio * 0.4f, label)
        }

        canvas.restore()
    }
}

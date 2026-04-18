package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.TailRenderer
import utility.Storage
import kotlin.math.sin

class BallSelectionCard(val isHigh: Boolean, private val popup: BallSelectionPopup) {

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val previewRenderer = PuckRenderer()

    // Plan 02: per-card skin+tail; rebuilt when selected ball type changes
    private var cachedType: BallType? = null
    private var tail: TailRenderer? = null
    private var skin: PuckSkin? = null
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
            // Full tall card: puck (slow hover float) + name label at bottom

            // Clear tail when transitioning from popup-open so it reseeds at current position
            if (wasPopupOpen) { tail?.clear(); wasPopupOpen = false }

            // Rebuild tail only when type changes (preserves particle history during animation)
            if (cachedType != type) {
                tail?.clear()
                cachedType = type
                val style = BallStyleFactory.buildStyle(type, theme)
                tail = style.tail
                skin = style.skin
            }

            val pr = halfW * 0.6f
            previewRenderer.frame++
            // Gentle hover float (smooth sin, not snap-bounce) keeps tails visible.
            // Amplitude must exceed ball radius (~screenRatio*0.84) so trail clears the ball boundary.
            val hoverOffset = Settings.screenRatio * 1.2f * sin(2 * Math.PI.toFloat() * previewRenderer.frame / 90f)
            val puckY = cy - hoverOffset
            previewRenderer.x = cx
            previewRenderer.y = puckY
            previewRenderer.radius = pr
            // strokeWidth must be synced each frame — renderer constructed before Settings.strokeWidth
            // is set by initializeSettings(), so the baked-in value is 0f.
            previewRenderer.strokePaint.strokeWidth = Settings.strokeWidth
            previewRenderer.chargePaint.strokeWidth = Settings.strokeWidth
            previewRenderer.fillColor = theme.primary
            previewRenderer.strokeColor = theme.secondary
            previewRenderer.baseFillColor = theme.primary
            previewRenderer.skin = skin
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

            // Clip tail to card bounds so particles don't escape into the play area
            canvas.save()
            canvas.clipRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            previewRenderer.draw(canvas)
            canvas.restore()

            label.textSize = Settings.screenRatio * 0.6f
            canvas.drawText(type.name, cx, cy + halfH - Settings.screenRatio * 0.4f, label)
        }

        canvas.restore()
    }
}

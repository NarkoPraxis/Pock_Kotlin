package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.BallType
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import utility.Storage
import kotlin.math.sin

class BallSelectionCard(val isHigh: Boolean, private val popup: BallSelectionPopup, private val player: () -> Player) {

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private var frame = 0

    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom / 2f + Settings.screenRatio * 2
                          else (Settings.screenHeight + Settings.bottomGoalTop) / 2f - Settings.screenRatio * 2

    val labelcy: Float get() = if (isHigh) Settings.topGoalBottom / 2f
                               else (Settings.screenHeight + Settings.bottomGoalTop) / 2f

    val w: Float get() = if (popup.isOpen) Settings.screenRatio * 8f
                         else Settings.ballRadius * 2f + Settings.screenRatio * 1.5f
    val h: Float get() = if (popup.isOpen) Settings.screenRatio * 2.2f else Settings.topGoalBottom * 1.4f

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    fun drawTo(canvas: Canvas) {
        bg.color = if (Storage.darkMode) Color.argb(180, 18, 18, 28) else Color.argb(200, 235, 235, 245)
        label.color = if (Storage.darkMode) Color.WHITE else Color.argb(230, 15, 15, 35)

        val type = if (popup.isOpen) popup.previewType
                   else if (isHigh) Settings.highBallType else Settings.lowBallType
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val halfW = w / 2f
        val halfH = h / 2f
        val progress = Settings.readyProgress
        val cardAlpha = ((1f - progress * 2f).coerceIn(0f, 1f) * 255f).toInt()
        val p = player()

        if (!popup.isOpen) {
            if (progress == 0f) {
                val hoverOffset = Settings.screenRatio * 1.0f * sin(2 * Math.PI.toFloat() * frame / 90f)
                p.puck.x = cx
                p.puck.y = cy - hoverOffset
                frame++
            } else {
                val flyT = smoothStep((progress / 0.4f).coerceAtMost(1f))
                p.puck.x = cx + (p.resetLocation.x - cx) * flyT
                p.puck.y = cy + (p.resetLocation.y - cy) * flyT
            }
        }

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, if (popup.isOpen) labelcy else cy)

        if (popup.isOpen) {
            label.textSize = Settings.screenRatio * 0.8f
            bg.alpha = 255
            border.alpha = 255
            label.alpha = 255
            canvas.drawRoundRect(cx - halfW, labelcy - halfH, cx + halfW, labelcy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
            border.color = theme.primary
            border.strokeWidth = Settings.screenRatio * 0.22f
            canvas.drawRoundRect(cx - halfW, labelcy - halfH, cx + halfW, labelcy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)
            canvas.drawText(type.name, cx, labelcy + label.textSize / 3f, label)
        } else {
            bg.alpha = cardAlpha
            canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
            border.color = theme.primary
            border.alpha = cardAlpha
            border.strokeWidth = Settings.screenRatio * 0.22f
            canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)
            label.textSize = Settings.screenRatio * 0.6f
            label.alpha = cardAlpha
            canvas.drawText(type.name, cx, cy + halfH - Settings.screenRatio * 0.4f, label)
        }

        canvas.restore()

        if (!popup.isOpen) {
            if (progress >= 0.4f) {
                p.drawTo(canvas)
            } else {
                // Show ball without charge/shield/effect indicators until it reaches game position
                val r = p.puck.renderer
                r.frame++
                r.currentCharge = 0f
                r.shielded = false
                r.launched = false
                r.baseFillColor = p.puckFillColor
                r.chargePowerLocked = false
                r.isHigh = p.isHigh
                r.isFlingHeld = false
                r.effectEnabled = false
                if (progress == 0f) {
                    canvas.save()
                    canvas.clipRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                    p.puck.drawTo(canvas)
                    canvas.restore()
                } else {
                    p.puck.drawTo(canvas)
                }
            }
        }
    }

    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
}

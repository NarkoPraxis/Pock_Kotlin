package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.BallType
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import utility.Storage

class BallSelectionCard(val isHigh: Boolean) {

    private val bg = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val hint = Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val previewPuck = Puck(0f, 0f, 0f, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)

    // Plan 02: per-card tail cache; rebuilt when the selected ball type changes
    private var cachedType: BallType? = null
    private var tail: TailRenderer? = null

    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom / 2f
                          else (Settings.screenHeight + Settings.bottomGoalTop) / 2f
    val w: Float get() = Settings.screenRatio * 8f
    val h: Float get() = Settings.screenRatio * 2.2f

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    // Plan 02: smooth sine bounce — card ball always bounces (it's the single selected preview)
    private fun bounceOffset(): Float {
        val period = 70f
        val amplitude = Settings.screenRatio * 0.55f
        return (amplitude * kotlin.math.sin(2 * Math.PI.toFloat() * previewPuck.frame / period)).toFloat()
    }

    fun drawTo(canvas: Canvas) {
        // Plan 00: re-apply light/dark colors each frame
        bg.color = if (Storage.darkMode) Color.argb(180, 18, 18, 28) else Color.argb(200, 235, 235, 245)
        label.color = if (Storage.darkMode) Color.WHITE else Color.argb(230, 15, 15, 35)
        hint.color = if (Storage.darkMode) Color.argb(140, 255, 255, 255) else Color.argb(140, 20, 20, 50)

        val type = if (isHigh) Settings.highBallType else Settings.lowBallType
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val halfW = w / 2f
        val halfH = h / 2f
        label.textSize = Settings.screenRatio * 0.7f
        hint.textSize = Settings.screenRatio * 0.45f

        // Rebuild tail only when type changes (keeps particle history intact during animation)
        if (cachedType != type) {
            tail?.clear()
            cachedType = type
            tail = BallStyleFactory.build(type, theme).second
        }

        val pr = halfH * 0.75f
        previewPuck.frame++
        val puckX = cx - halfW + halfH
        val puckY = cy - bounceOffset()   // Plan 02: apply bounce
        previewPuck.x = puckX
        previewPuck.y = puckY
        previewPuck.radius = pr
        previewPuck.setFill(theme.primary)
        previewPuck.setStroke(theme.secondary)
        val (skin, _) = BallStyleFactory.build(type, theme)
        previewPuck.skin = skin
        // Selected card ball is always unlocked — no placeholder needed

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, cy)

        // Card background + border
        canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
            Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
        border.color = theme.primary
        border.strokeWidth = Settings.screenRatio * 0.12f
        canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
            Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)

        // Plan 02 draw order: tail → puck → labels (no clip in this widget, so no clip-overflow needed)
        tail?.renderForPreview(canvas, previewPuck, shielded = false, launched = false, baseFillColor = theme.primary)
        previewPuck.drawTo(canvas)

        canvas.drawText(type.name, cx + halfH * 0.4f, cy + label.textSize / 3f, label)
        canvas.drawText("tap", cx + halfW - Settings.screenRatio * 0.8f, cy + hint.textSize / 3f, hint)

        canvas.restore()
    }
}

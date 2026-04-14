package shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.BallType
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import utility.PaintBucket

class BallSelectionCard(val isHigh: Boolean) {

    private val bg = Paint().apply { color = Color.argb(180, 18, 18, 28); style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val hint = Paint().apply {
        color = Color.argb(140, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val previewPuck = Puck(0f, 0f, 0f, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)

    val cx: Float get() = Settings.middleX
    val cy: Float get() = if (isHigh) Settings.topGoalBottom / 2f
                          else (Settings.screenHeight + Settings.bottomGoalTop) / 2f
    val w: Float get() = Settings.screenRatio * 8f
    val h: Float get() = Settings.screenRatio * 2.2f

    fun hitTest(x: Float, y: Float): Boolean =
        x > cx - w / 2f && x < cx + w / 2f && y > cy - h / 2f && y < cy + h / 2f

    fun drawTo(canvas: Canvas) {
        val type = if (isHigh) Settings.highBallType else Settings.lowBallType
        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val halfW = w / 2f
        val halfH = h / 2f
        label.textSize = Settings.screenRatio * 0.7f
        hint.textSize = Settings.screenRatio * 0.45f

        canvas.save()
        if (isHigh) canvas.scale(-1f, -1f, cx, cy)

        canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
            Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, bg)
        border.color = theme.primary
        border.strokeWidth = Settings.screenRatio * 0.12f
        canvas.drawRoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH,
            Settings.screenRatio * 0.3f, Settings.screenRatio * 0.3f, border)

        val pr = halfH * 0.75f
        previewPuck.x = cx - halfW + halfH
        previewPuck.y = cy
        previewPuck.radius = pr
        previewPuck.frame++
        previewPuck.setFill(theme.primary)
        previewPuck.setStroke(theme.secondary)
        val (skin, _) = BallStyleFactory.build(type, theme)
        previewPuck.skin = skin
        previewPuck.drawTo(canvas)

        canvas.drawText(type.name, cx + halfH * 0.4f, cy + label.textSize / 3f, label)
        canvas.drawText("tap", cx + halfW - Settings.screenRatio * 0.8f, cy + hint.textSize / 3f, hint)

        canvas.restore()
    }
}

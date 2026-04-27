package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class NeonSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    // Subtle dark fill so the hollow center reads as a translucent tube, not empty space
    private val subtleFill = Paint().apply {
        color = Color.argb(35, 0, 0, 0)
        isAntiAlias = true; isDither = true; style = Paint.Style.FILL
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, responsivePrimary))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, theme.shield.primary))
    }

    private class NeonRingScar(
        private val x: Float, private val y: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val len = radius * 1.5f
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(100, 255)
            // Outer glow
            paint.color = color
            paint.alpha = (alpha * 0.5f).toInt()
            paint.strokeWidth = radius * 0.7f
            canvas.drawCircle(x, y, radius, paint)
            // Bright inner core
            paint.alpha = alpha
            paint.strokeWidth = radius * 0.35f
            canvas.drawCircle(x, y, radius, paint)
            paint.alpha = 255
        }
    }

    override fun drawBody(canvas: Canvas) {
        val sw = renderer.strokePaint.strokeWidth
        val primary = resolvedColors().primary

        // 4 glow rings, outermost first — body always stays theme color, charging shown via chargePaint
        glowPaint.color = Palette.withAlpha(primary, 25);  glowPaint.strokeWidth = sw * 5.0f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 45);  glowPaint.strokeWidth = sw * 3.2f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 110); glowPaint.strokeWidth = sw * 1.8f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 220); glowPaint.strokeWidth = sw
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)

        renderer.chargePaint.color = theme.shield.primary
    }
}

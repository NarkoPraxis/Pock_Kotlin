package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import androidx.core.graphics.withTranslation
import physics.Point
import utility.Effects

class MetalSkin(override val renderer: PuckRenderer) : CachedShaderSkin(renderer) {

    private val grey = Color.rgb(140, 140, 150)
    private val lightGrey = Color.rgb(220, 220, 230)
    private val darkGrey = Color.rgb(70, 70, 80)
    private val accentTint = theme.main.primary

    private val edgePaint = Paint().apply { color = darkGrey; isAntiAlias = false; style = Paint.Style.STROKE }

    init {
        fill.isAntiAlias = false
    }

    override fun createShader(radius: Float): Shader =
        LinearGradient(0f, -radius, 0f, radius,
            intArrayOf(theme.inert.primary, responsivePrimary, grey, darkGrey),
            floatArrayOf(0f, 0.25f, 0.75f, 1f),
            Shader.TileMode.CLAMP)

    private var cachedEdgeStrokeWidth = -1f

    override fun drawBody(canvas: Canvas) {
        ensureShader(renderer.radius)
        // Cache strokeWidth — only changes when radius changes (same cadence as shader rebuild)
        val sw = renderer.strokePaint.strokeWidth * 0.9f
        if (cachedEdgeStrokeWidth != sw) {
            cachedEdgeStrokeWidth = sw
            edgePaint.strokeWidth = sw
        }
        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius, fill)
        }
        edgePaint.color = responsiveSecondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, edgePaint)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(DynamiteCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false))
    }

    private class DynamiteCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean
    ) : Effects.PersistentEffect {
        private val outerPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL
            color = Color.rgb(255, 180, 40)
        }
        private val innerPaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.FILL
            color = Color.rgb(255, 240, 150)
        }
        private val clipPath = if (!fullCircle) Path().also { p ->
            if (highGoal)
                p.addRect(cx - radius * 20f, cy, cx + radius * 20f, cy + radius * 20f, Path.Direction.CW)
            else
                p.addRect(cx - radius * 20f, cy - radius * 20f, cx + radius * 20f, cy, Path.Direction.CW)
        } else null
        private var frame = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        override fun step() { frame++; if (frame > 60) _isDone = true }

        override fun draw(canvas: Canvas) {
            val progress = (frame / 60f).coerceIn(0f, 1f)
            val r = radius * 2f * (1f + progress * 4f)
            val outerAlpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            if (outerAlpha <= 0) return
            canvas.save()
            clipPath?.let { canvas.clipPath(it) }
            outerPaint.alpha = outerAlpha
            canvas.drawCircle(cx, cy, r, outerPaint)
            innerPaint.alpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 0.55f, innerPaint)
            canvas.restore()
        }
    }
}

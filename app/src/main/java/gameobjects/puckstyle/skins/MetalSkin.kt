package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.graphics.withSave
import gameobjects.puckstyle.CachedBrushSkin
import gameobjects.puckstyle.PuckRenderer
import physics.Point
import utility.Effects
import gameobjects.puckstyle.paddles.MetalLaunch
import kotlin.math.sin

class MetalSkin(override val renderer: PuckRenderer) : CachedBrushSkin(renderer) {

    private val grey = AndroidColor.rgb(140, 140, 150)
    private val darkGrey = AndroidColor.rgb(70, 70, 80)

    private var lastPrimary = -1
    private var cachedEdgeSw = -1f
    private var edgeStroke = Stroke(width = 0f)

    override fun buildBrush(radius: Float): Brush =
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(theme.inert.primary),
                0.25f to Color(responsivePrimary),
                0.75f to Color(grey),
                1f to Color(darkGrey)
            ),
            start = Offset(0f, -radius),
            end = Offset(0f, radius)
        )

    override fun DrawScope.drawBody() {
        val primary = responsivePrimary
        if (primary != lastPrimary) {
            lastPrimary = primary
            invalidateBrush()
        }
        ensureBrush(renderer.radius)

        val sw = renderer.strokePaint.strokeWidth * 0.9f
        if (cachedEdgeSw != sw) {
            cachedEdgeSw = sw
            edgeStroke = Stroke(width = sw)
        }

        withTransform({ translate(renderer.x, renderer.y) }) {
            drawCircle(brush = cachedBrush!!, radius = renderer.radius, center = Offset.Zero)
        }
        drawCircle(Color(responsiveSecondary), renderer.radius, Offset(renderer.x, renderer.y), style = edgeStroke)
    }

    override val explosionFrequency get() = 15
    override val scatterDensity get() = 0.7f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        val clip = if (highGoal)
            Path().also { p -> p.addRect(position.x - renderer.radius * 20f, position.y, position.x + renderer.radius * 20f, position.y + renderer.radius * 20f, Path.Direction.CW) }
        else
            Path().also { p -> p.addRect(position.x - renderer.radius * 20f, position.y - renderer.radius * 20f, position.x + renderer.radius * 20f, position.y, Path.Direction.CW) }

        Effects.addPersistentEffect(DynamiteExplosion(
            renderer.x, renderer.y, renderer.radius,
            theme.main.secondary, theme.main.primary, theme.main.secondary,
            leaveScorch = false, clipPath = clip
        ))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(DynamiteExplosion(
            x, y, renderer.radius * 1.5f,
            theme.main.secondary, theme.main.secondary, theme.main.primary,
            leaveScorch = false
        ))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(DynamiteExplosion(
            position.x, position.y, renderer.radius,
            theme.shield.secondary, theme.shield.secondary, theme.shield.primary,
            leaveScorch = true
        ))
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(DynamiteExplosion(
            position.x, position.y, renderer.radius,
            theme.main.secondary, theme.main.secondary, theme.main.primary,
            leaveScorch = true
        ))
    }

    private class DynamiteExplosion(
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
        private val bodyColor: Int,
        private val sparkColor: Int,
        private val fillColor: Int,
        private val leaveScorch: Boolean,
        private val clipPath: Path? = null
    ) : Effects.PersistentEffect {

        private val FUSE_FRAMES = 30
        private val EXPLODE_FRAMES = 4

        private val explosionOuter = AndroidColor.rgb(255, 180, 40)
        private val explosionInner = AndroidColor.rgb(255, 240, 150)

        private val stickPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val fusePaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            color = AndroidColor.rgb(70, 50, 30)
            strokeWidth = radius * 0.07f
        }
        private val flashPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val rect = RectF()

        private var frame = 0
        private var _isDone = false
        override val isDone get() = _isDone

        override fun step() {
            frame++
            if (frame >= FUSE_FRAMES + EXPLODE_FRAMES) {
                _isDone = true
                if (leaveScorch) {
                    Effects.addPersistentEffect(MetalLaunch.BlastScorch(cx, cy, radius, sparkColor))
                }
            }
        }

        override fun draw(canvas: Canvas) {
            canvas.save()
            clipPath?.let { canvas.clipPath(it) }
            if (frame < FUSE_FRAMES) {
                drawStick(canvas)
            } else {
                val progress = (frame - FUSE_FRAMES).toFloat() / EXPLODE_FRAMES
                drawExplosion(canvas, progress)
            }
            canvas.restore()
        }

        private fun drawStick(canvas: Canvas) {
            canvas.withSave {
                rotate(-30f, cx, cy)
                val halfLen = radius * 0.85f
                val halfThick = radius * 0.25f

                stickPaint.color = bodyColor
                rect.set(cx - halfLen, cy - halfThick, cx + halfLen, cy + halfThick)
                drawRoundRect(rect, halfThick * 0.4f, halfThick * 0.4f, stickPaint)

                stickPaint.color = fillColor
                rect.set(cx - halfLen * 0.9f, cy - halfThick * 0.6f, cx + halfLen * 0.9f, cy + halfThick * 0.6f)
                drawRoundRect(rect, halfThick, halfThick, stickPaint)

                val fuseBaseX = cx + halfLen
                val fuseBaseY = cy
                val fuseTipX = fuseBaseX + halfThick * 1.4f
                val fuseTipY = fuseBaseY - halfThick * -1.2f
                drawLine(fuseBaseX, fuseBaseY, fuseTipX, fuseTipY, fusePaint)

                val flicker = 0.6f + 0.4f * sin(frame * 0.9f)
                stickPaint.color = sparkColor
                stickPaint.alpha = (255 * flicker).toInt().coerceIn(0, 255)
                drawCircle(fuseTipX, fuseTipY, halfThick, stickPaint)
                stickPaint.alpha = 255
            }
        }

        private fun drawExplosion(canvas: Canvas, progress: Float) {
            val r = radius * (1f + progress * 5f)
            flashPaint.color = explosionOuter
            flashPaint.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, flashPaint)
            flashPaint.color = explosionInner
            flashPaint.alpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 0.55f, flashPaint)
            flashPaint.alpha = 255
        }
    }
}

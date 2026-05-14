package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import gameobjects.puckstyle.CachedBrushSkin
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.PaintBucket
import physics.Point
import utility.Effects
import gameobjects.puckstyle.paddles.MetalLaunch
import kotlin.math.sin

class MetalSkin(override val renderer: PuckRenderer) : CachedBrushSkin(renderer) {

    private val grey = Palette.argb(255, 140, 140, 150)
    private val darkGrey = Palette.argb(255, 70, 70, 80)

    private var lastPrimary = -1
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var cachedEdgeSw = -1f
    private var edgeStroke = Stroke(width = 0f)

    override fun buildBrush(radius: Float): Brush =
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(PaintBucket.inertPrimaryColor.toArgb()),
                0.25f to Color(responsivePrimary),
                0.75f to Color(grey),
                1f to Color(darkGrey)
            ),
            start = Offset(renderer.x, renderer.y - radius),
            end = Offset(renderer.x, renderer.y + radius)
        )

    override fun DrawScope.drawBody() {
        val primary = responsivePrimary
        if (primary != lastPrimary) {
            lastPrimary = primary
            invalidateBrush()
        }
        if (renderer.x != lastX || renderer.y != lastY) {
            lastX = renderer.x
            lastY = renderer.y
            invalidateBrush()
        }
        ensureBrush(renderer.radius)

        val sw = renderer.strokeWidth * 0.9f
        if (cachedEdgeSw != sw) {
            cachedEdgeSw = sw
            edgeStroke = Stroke(width = sw)
        }

        drawCircle(brush = cachedBrush!!, radius = renderer.radius, center = Offset(renderer.x, renderer.y))
        drawCircle(Color(responsiveSecondary), renderer.radius, Offset(renderer.x, renderer.y), style = edgeStroke)
    }

    override val explosionFrequency get() = 15
    override val scatterDensity get() = 0.7f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        val clip = if (highGoal)
            Path().also { p ->
                p.moveTo(position.x - renderer.radius * 20f, position.y)
                p.lineTo(position.x + renderer.radius * 20f, position.y)
                p.lineTo(position.x + renderer.radius * 20f, position.y + renderer.radius * 20f)
                p.lineTo(position.x - renderer.radius * 20f, position.y + renderer.radius * 20f)
                p.close()
            }
        else
            Path().also { p ->
                p.moveTo(position.x - renderer.radius * 20f, position.y - renderer.radius * 20f)
                p.lineTo(position.x + renderer.radius * 20f, position.y - renderer.radius * 20f)
                p.lineTo(position.x + renderer.radius * 20f, position.y)
                p.lineTo(position.x - renderer.radius * 20f, position.y)
                p.close()
            }

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

        private val explosionOuter = Palette.argb(255, 255, 180, 40)
        private val explosionInner = Palette.argb(255, 255, 240, 150)
        private val fuseColor = Palette.argb(255, 70, 50, 30)

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

        override fun draw(scope: DrawScope) {
            if (clipPath != null) {
                scope.withTransform({ clipPath(clipPath) }) {
                    drawContent(scope)
                }
            } else {
                drawContent(scope)
            }
        }

        private fun drawContent(scope: DrawScope) {
            if (frame < FUSE_FRAMES) {
                scope.drawStick()
            } else {
                val progress = (frame - FUSE_FRAMES).toFloat() / EXPLODE_FRAMES
                scope.drawExplosion(progress)
            }
        }

        private fun DrawScope.drawStick() {
            withTransform({
                rotate(-30f, pivot = Offset(cx, cy))
            }) {
                val halfLen = radius * 0.85f
                val halfThick = radius * 0.25f

                drawRoundRect(
                    Color(bodyColor),
                    topLeft = Offset(cx - halfLen, cy - halfThick),
                    size = Size(halfLen * 2f, halfThick * 2f),
                    cornerRadius = CornerRadius(halfThick * 0.4f)
                )

                drawRoundRect(
                    Color(fillColor),
                    topLeft = Offset(cx - halfLen * 0.9f, cy - halfThick * 0.6f),
                    size = Size(halfLen * 0.9f * 2f, halfThick * 0.6f * 2f),
                    cornerRadius = CornerRadius(halfThick)
                )

                val fuseBaseX = cx + halfLen
                val fuseBaseY = cy
                val fuseTipX = fuseBaseX + halfThick * 1.4f
                val fuseTipY = fuseBaseY - halfThick * -1.2f
                drawLine(
                    Color(fuseColor),
                    Offset(fuseBaseX, fuseBaseY),
                    Offset(fuseTipX, fuseTipY),
                    strokeWidth = radius * 0.07f
                )

                val flicker = 0.6f + 0.4f * sin(frame * 0.9f)
                val sparkAlpha = (255 * flicker).toInt().coerceIn(0, 255)
                drawCircle(
                    Color(sparkColor).copy(alpha = sparkAlpha / 255f),
                    halfThick,
                    Offset(fuseTipX, fuseTipY)
                )
            }
        }

        private fun DrawScope.drawExplosion(progress: Float) {
            val r = radius * (1f + progress * 5f)
            val outerAlpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            val innerAlpha = (220 * (1f - progress)).toInt().coerceIn(0, 255)
            drawCircle(
                Color(Palette.withAlpha(explosionOuter, outerAlpha)),
                r,
                Offset(cx, cy)
            )
            drawCircle(
                Color(Palette.withAlpha(explosionInner, innerAlpha)),
                r * 0.55f,
                Offset(cx, cy)
            )
        }
    }
}

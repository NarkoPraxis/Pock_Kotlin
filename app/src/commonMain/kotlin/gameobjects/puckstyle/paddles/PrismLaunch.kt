package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.THEME_SATURATION
import gameobjects.puckstyle.Palette.THEME_VALUE
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PrismLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val path = Path()
    private val defaultStrokeWidth = Settings.strokeWidth * .5f
    private val edgeStroke = Stroke(width = defaultStrokeWidth, join = StrokeJoin.Round)

    override fun drawChargingPaddle(scope: DrawScope) =
        drawPrism(scope, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawPrism(scope, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
        if (sweet) drawRefraction(scope, cx, cy, aX, aY, progress)
    }

    private fun drawPrism(
        scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float,
        ph: ChargePhase, ratio: Float
    ) {
        val half = paddleHalfLength() * 0.85f
        val depth = renderer.radius * 0.5f
        val pX = -aY; val pY = aX
        path.reset()
        path.moveTo(cx + pX * half, cy + pY * half)
        path.lineTo(cx - pX * half, cy - pY * half)
        path.lineTo(cx - aX * depth, cy - aY * depth)
        path.close()

        val fillColor = if (ph == ChargePhase.Inert) theme.inert.primary else Palette.WHITE
        scope.drawPath(path, Color(Palette.withAlpha(fillColor, 200)))

        if (ratio > 0f) {
            scope.drawPath(
                path,
                Color(Palette.withAlpha(Palette.cyclingHue(frame, 4f), (180 * ratio).toInt().coerceIn(0, 255)))
            )
        }

        val edgeColor = if (renderer.isInert || ph == ChargePhase.Inert) theme.inert.secondary else responsivePrimary
        scope.drawPath(
            path,
            Color(edgeColor),
            style = edgeStroke
        )
    }

    private fun drawRefraction(scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float, progress: Float) {
        val pX = -aY; val pY = aX
        val half = paddleHalfLength()
        val px = renderer.x; val py = renderer.y
        val alpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
        val strokeW = renderer.radius
        for (i in 0 until 6) {
            val offset = (i - 2.5f) / 2.5f * half
            val hue = i * 60f + frame * 3f
            scope.drawLine(
                color = Color(Palette.withAlpha(Palette.hsv(hue, THEME_SATURATION, THEME_VALUE), alpha)),
                start = Offset(cx + pX * offset, cy + pY * offset),
                end = Offset(px + pX * offset * 0.6f, py + pY * offset * 0.6f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val baseHue = Palette.themeHue(theme)
        Effects.addPersistentEffect(PrismScatter(rx, ry, renderer.radius, spawnRotDeg, spawnOsc, baseHue))
    }

    companion object {
        fun scatterTriangles(x: Float, y: Float, puckRadius: Float, spawnRotation: Float, spawnOscillation: Float, baseHue: Float) {
            Effects.addPersistentEffect(PrismScatter(x, y, puckRadius, spawnRotation, spawnOscillation, baseHue))
        }
    }

    private class PrismScatter(
        private val cx: Float,
        private val cy: Float,
        private val radius: Float,
        spawnRotDeg: Float,
        spawnOsc: Float,
        baseHue: Float
    ) : Effects.PersistentEffect {

        private val edgeStrokeWidth = Settings.strokeWidth * 0.4f
        private val edgeStroke = Stroke(width = edgeStrokeWidth, join = StrokeJoin.Round)
        private val path = Path()
        private var frame = 0
        override var isDone = false

        private val hueOffsets = floatArrayOf(0f, 40f, -30f, 20f, 60f, -15f)

        private inner class TrianglePiece(
            val v0x: Float, val v0y: Float,
            val v1x: Float, val v1y: Float,
            val v2x: Float, val v2y: Float,
            val dirX: Float, val dirY: Float,
            val frozenHue: Float
        ) {
            var offsetX = 0f
            var offsetY = 0f
            var stopped = false
            var pieceDone = false
            var alpha = 255
            private val travelDist = radius * 2.2f
            private val speed = radius * 0.2f
            private var traveled = 0f

            fun step() {
                if (stopped) return
                offsetX += dirX * speed
                offsetY += dirY * speed
                traveled += speed
                val ratio = (traveled / travelDist).coerceIn(0f, 1f)
                alpha = (255 - (155f * ratio)).toInt().coerceIn(50, 255)
                if (traveled >= travelDist) {
                    stopped = true
                    alpha = 50
                    val pcx = (v0x + v1x + v2x) / 3f + offsetX
                    val pcy = (v0y + v1y + v2y) / 3f + offsetY
                    if (pcx < -radius || pcx > Settings.screenWidth + radius ||
                        pcy < -radius || pcy > Settings.screenHeight + radius) {
                        pieceDone = true
                    }
                }
            }

            fun draw(scope: DrawScope) {
                val hue = if (stopped) frozenHue
                           else frozenHue + kotlin.math.sin(frame * 0.04).toFloat() * 30f
                path.reset()
                path.moveTo(v0x + offsetX, v0y + offsetY)
                path.lineTo(v1x + offsetX, v1y + offsetY)
                path.lineTo(v2x + offsetX, v2y + offsetY)
                path.close()
                scope.drawPath(path, Color(Palette.withAlpha(Palette.hsvThemed(hue), alpha)))
                scope.drawPath(
                    path,
                    Color(Palette.withAlpha(Palette.hsvHighlight(hue), alpha)),
                    style = edgeStroke
                )
            }
        }

        private val pieces: Array<TrianglePiece>

        init {
            val sides = 6
            val rotRad = spawnRotDeg * PI.toFloat() / 180f
            val twoPiOverSides = 2f * PI.toFloat() / sides
            pieces = Array(sides) { i ->
                val a1 = i * twoPiOverSides + rotRad
                val a2 = (i + 1) * twoPiOverSides + rotRad
                val v1x = cx + cos(a1) * radius
                val v1y = cy + sin(a1) * radius
                val v2x = cx + cos(a2) * radius
                val v2y = cy + sin(a2) * radius
                val midX = (v1x + v2x) / 2f - cx
                val midY = (v1y + v2y) / 2f - cy
                val len = sqrt(midX * midX + midY * midY)
                val dirX = if (len > 0f) midX / len else 0f
                val dirY = if (len > 0f) midY / len else 0f
                val hue = baseHue + hueOffsets[i] + spawnOsc
                TrianglePiece(cx, cy, v1x, v1y, v2x, v2y, dirX, dirY, hue)
            }
        }

        override fun step() {
            frame++
            var allDone = true
            for (i in pieces.indices) {
                val p = pieces[i]
                p.step()
                if (!p.pieceDone) allDone = false
            }
            if (allDone) isDone = true
        }

        override fun draw(scope: DrawScope) {
            for (i in pieces.indices) {
                val p = pieces[i]
                if (!p.pieceDone) p.draw(scope)
            }
        }
    }
}

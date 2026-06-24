package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class IceLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private val path = Path()
    private val shardStrokeWidth = Settings.strokeWidth * 0.6f
    // Hoisted: width is fixed for the paddle's lifetime, so the Stroke never needs rebuilding.
    private val shardStroke = Stroke(width = shardStrokeWidth, join = StrokeJoin.Round)

    override fun drawChargingPaddle(scope: DrawScope) {
        drawShard(scope, paddleX, paddleY, aimX, aimY, phase, chargeFillRatio)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        val ph = if (sweet) ChargePhase.SweetSpot else if (fatigued) ChargePhase.Inert else ChargePhase.Building
        drawShard(scope, cx, cy, aX, aY, ph, if (sweet) 1f else if (fatigued) 0f else 1f)
    }

    private fun drawShard(scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float, ph: ChargePhase, fill: Float) {
        val half = paddleHalfLength()
        val pX = -aY
        val pY = aX
        val longR = half
        val shortR = half * 0.45f

        path.reset()
        path.moveTo(cx + pX * longR, cy + pY * longR)
        path.lineTo(cx + aX * shortR, cy + aY * shortR)
        path.lineTo(cx - pX * longR, cy - pY * longR)
        path.lineTo(cx - aX * shortR, cy - aY * shortR)
        path.close()

        scope.drawPath(path, Color(responsivePrimary))

        if (fill > 0f) {
            scope.drawPath(
                path,
                Color(Palette.withAlpha(renderer.invertedChargeColor(theme.shield.primary), (180 * fill).toInt().coerceIn(0, 255)))
            )
        }

        scope.drawPath(
            path,
            Color(Palette.WHITE),
            style = shardStroke
        )
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(IcePuddle(rx, ry, renderer.radius, renderer.bakedPrimary(theme.main.primary)))
    }

    companion object {
        // primary is the puck's current colour baked at spawn (rainbow-resolved when strobing).
        fun spawnImpact(cx: Float, cy: Float, radius: Float, primary: Int) {
            Effects.addPersistentEffect(IcePuddle(cx, cy, radius, primary))
        }

        private val CRYSTAL_ANGLES = FloatArray(8) { i -> (i * 2.0 * PI / 8).toFloat() }
    }

    private class IcePuddle(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val primary: Int
    ) : Effects.PersistentEffect {
        private val crystalPath = Path()
        private val strokeColor = Palette.withAlpha(primary, 130)
        private val strokeWidth = Settings.strokeWidth * 0.5f
        // Hoisted: fixed width for this effect's lifetime — avoids a Stroke alloc every draw frame.
        private val crystalStroke = Stroke(width = strokeWidth)
        private var frame = 0
        private val evaporateDuration = 120f

        private var postScoreFrame = -1
        private var startCrystalT = 0f
        private val meltDuration = 30
        private val postCrystalHold = 30
        private val fadeDuration = 30
        private val totalPostScore = meltDuration + postCrystalHold + fadeDuration

        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        override fun onScoreSignal(): Boolean {
            postScoreFrame = 0
            startCrystalT = (frame / evaporateDuration).coerceIn(0f, 1f)
            return true
        }

        override fun step() {
            if (postScoreFrame >= 0) {
                postScoreFrame++
                if (postScoreFrame >= totalPostScore) _isDone = true
            } else {
                frame++
            }
        }

        override fun draw(scope: DrawScope) {
            if (postScoreFrame >= 0) {
                if (postScoreFrame < meltDuration) {
                    val t = startCrystalT + (postScoreFrame.toFloat() / meltDuration) * (1f - startCrystalT)
                    drawCrystal(scope, t)
                }
                val growFrames = (meltDuration + postCrystalHold).toFloat()
                val growT = (postScoreFrame.toFloat() / growFrames).coerceIn(0f, 1f)
                val fadeT = ((postScoreFrame - growFrames) / fadeDuration).coerceIn(0f, 1f)
                val alpha = (120 * (1f - fadeT)).toInt().coerceIn(0, 255)
                if (alpha > 0) {
                    scope.drawCircle(
                        Color(Palette.withAlpha(primary, alpha)),
                        radius * growT * 1.5f,
                        Offset(cx, cy)
                    )
                }
            } else {
                val uncappedTime = frame / evaporateDuration
                val alpha = (120 - (60 * uncappedTime)).toInt().coerceIn(0, 255)
                if (alpha > 0) {
                    scope.drawCircle(
                        Color(Palette.withAlpha(primary, alpha)),
                        radius * uncappedTime * 1.5f,
                        Offset(cx, cy)
                    )
                }
                drawCrystal(scope, uncappedTime.coerceIn(0f, 1f))
            }
        }

        private fun drawCrystal(scope: DrawScope, t: Float) {
            val crystalR = radius * (1.4f - t * 1.1f)
            crystalPath.reset()
            for (i in 0 until 8) {
                val angle = CRYSTAL_ANGLES[i]
                val outerR = crystalR * (if (i % 2 == 0) 2.3f else 1f)
                val x = cx + cos(angle) * outerR
                val y = cy + sin(angle) * outerR
                if (i == 0) crystalPath.moveTo(x, y) else crystalPath.lineTo(x, y)
            }
            crystalPath.close()
            scope.drawPath(crystalPath, Color(Palette.WHITE))
            scope.drawPath(crystalPath, Color(strokeColor), style = crystalStroke)
        }
    }
}

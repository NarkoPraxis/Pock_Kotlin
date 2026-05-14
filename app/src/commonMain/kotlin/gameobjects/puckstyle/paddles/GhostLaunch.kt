package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.sin
import kotlin.math.sqrt

class GhostLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    override var minDist: Float = 0f
        get() = 0f

    override val zIndex: Int
        get() = 2

    private val auraRings = listOf(
        AuraConfig(1.10f, 0.06f, 0.0f, 70, 1.6f),
        AuraConfig(1.20f, 0.08f, 1.0f, 45, 1.2f),
        AuraConfig(1.35f, 0.10f, 2.2f, 25, 2.0f),
        AuraConfig(1.55f, 0.12f, 3.7f, 12, 2.8f)
    )

    private val bodyWhiteColor = Palette.argb(120, 255, 255, 255)
    private val glowWhite200 = Palette.argb(200, 255, 255, 255)
    private val glowWhite160 = Palette.argb(160, 255, 255, 255)
    private val baseSw = Settings.strokeWidth * 0.7f

    private val tailCapacity = 9
    private val tailXs = FloatArray(tailCapacity)
    private val tailYs = FloatArray(tailCapacity)
    private var tailSize = 0

    private val orbRadius: Float get() = renderer.radius * .6f

    override fun drawChargingPaddle(scope: DrawScope) {
        if (tailSize < tailCapacity) {
            for (i in tailSize downTo 1) { tailXs[i] = tailXs[i - 1]; tailYs[i] = tailYs[i - 1] }
            tailSize++
        } else {
            for (i in tailCapacity - 1 downTo 1) { tailXs[i] = tailXs[i - 1]; tailYs[i] = tailYs[i - 1] }
        }
        tailXs[0] = paddleX; tailYs[0] = paddleY

        drawGhostOrb(scope, paddleX, paddleY)
        drawGhostTail(scope, tailXs, tailYs, tailSize, orbRadius, responsivePrimary, chargeFillRatio, theme.shield.primary)
    }

    override fun drawStrikingPaddle(
        scope: DrawScope, cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawGhostOrb(scope, cx, cy)
    }

    override fun drawIdlePaddle(scope: DrawScope) {
        if (tailSize > 0) {
            tailSize--
            for (i in 0 until tailSize) { tailXs[i] = tailXs[i + 1]; tailYs[i] = tailYs[i + 1] }
        }
    }

    private fun drawGhostOrb(scope: DrawScope, cx: Float, cy: Float) {
        val frameF = frame.toFloat()
        val pulse = 0.88f + 0.12f * sin(frameF * 0.18f)
        val r = orbRadius * pulse
        val glowColor = if (phase == ChargePhase.SweetSpot) theme.shield.primary else responsivePrimary
        val sw = baseSw
        val center = Offset(cx, cy)

        val auraFramePhase = frameF * 0.04f
        for (ring in auraRings) {
            val auraR = r * ring.baseMult + r * ring.amp * sin(auraFramePhase + ring.phase)
            scope.drawCircle(
                color = Color(Palette.withAlpha(glowColor, ring.alpha)),
                radius = auraR,
                center = center,
                style = Stroke(width = sw * ring.strokeMult)
            )
        }

        scope.drawCircle(Color(bodyWhiteColor), r, center)
        scope.drawCircle(Color(glowWhite200), r, center, style = Stroke(width = sw))

        val innerR = r * 0.75f + r * 0.1f * sin(frameF * 0.025f + 5f)
        scope.drawCircle(Color(glowWhite160), innerR, center, style = Stroke(width = sw * 0.7f))

        if (chargeFillRatio > 0f) {
            val chargeColor = Palette.lerpColor(theme.shield.primary, theme.shield.secondary, sin(frameF * 0.25f) * 0.5f + 0.5f)
            scope.drawCircle(Color(chargeColor), r * chargeFillRatio, center)
        }
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(GhostSpirit(rx, ry, renderer.radius * 0.5f, theme.shield.primary, renderer))
    }

    companion object {
        fun spawnImpact(cx: Float, cy: Float, radius: Float, color: Int, renderer: PuckRenderer) {
            Effects.addPersistentEffect(GhostSpirit(cx, cy, radius, color, renderer))
        }

        fun drawGhostTail(
            scope: DrawScope,
            xs: FloatArray, ys: FloatArray,
            count: Int,
            baseR: Float,
            glowColor: Int,
            chargeFill: Float,
            chargeColor: Int
        ) {
            if (count < 2) return
            val sw = Settings.strokeWidth * 0.7f
            val outlineR = baseR * 1.15f
            val countF = count.toFloat()
            for (i in 0 until count) {
                val ratio = i.toFloat() / countF
                val r = outlineR * (1f - ratio * 0.9f)
                scope.drawCircle(
                    color = Color(glowColor),
                    radius = r,
                    center = Offset(xs[i], ys[i]),
                    style = Stroke(width = sw * 1.2f)
                )
            }
            val hasCharge = chargeFill > 0f
            for (i in 0 until count) {
                val ratio = i.toFloat() / countF
                val r = baseR * (1f - ratio * 0.9f)
                scope.drawCircle(Color(Palette.WHITE), r, Offset(xs[i], ys[i]))
                if (hasCharge) {
                    scope.drawCircle(
                        Color(Palette.withAlpha(chargeColor, 120)),
                        r * chargeFill,
                        Offset(xs[i], ys[i])
                    )
                }
            }
        }
    }

    private class GhostSpirit(
        private var cx: Float, private var cy: Float,
        private val baseRadius: Float,
        private val color: Int,
        private val renderer: PuckRenderer
    ) : Effects.PersistentEffect {

        private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)
        private val auraRings = listOf(
            AuraConfig(1.10f, 0.06f, 0.0f, 70, 1.6f),
            AuraConfig(1.20f, 0.08f, 1.0f, 45, 1.2f),
            AuraConfig(1.35f, 0.10f, 2.2f, 25, 2.0f),
            AuraConfig(1.55f, 0.12f, 3.7f, 12, 2.8f)
        )

        private val bodyWhiteColor = Palette.argb(120, 255, 255, 255)
        private val glowWhite200 = Palette.argb(200, 255, 255, 255)
        private val glowWhite160 = Palette.argb(160, 255, 255, 255)
        private val baseSw = Settings.strokeWidth * 0.7f

        private var frame = 0
        private var returning = false
        private var returnFrames = 0
        private val maxReturnFrames = 300

        private val tailCapacity = 20
        private val tailXs = FloatArray(tailCapacity)
        private val tailYs = FloatArray(tailCapacity)
        private var tailSize = 0

        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        override fun onScoreSignal(): Boolean {
            returning = true
            return true
        }

        override fun step() {
            frame++
            if (!returning) return
            returnFrames++
            if (returnFrames > maxReturnFrames) { _isDone = true; return }
            val tx = renderer.x; val ty = renderer.y
            val dx = tx - cx; val dy = ty - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < baseRadius * 0.2f) {
                if (tailSize > 0) {
                    cx = renderer.x; cy = renderer.y
                    tailSize--
                } else {
                    _isDone = true; return
                }
            } else {
                if (tailSize < tailCapacity) {
                    for (i in tailSize downTo 1) { tailXs[i] = tailXs[i - 1]; tailYs[i] = tailYs[i - 1] }
                    tailSize++
                } else {
                    for (i in tailCapacity - 1 downTo 1) { tailXs[i] = tailXs[i - 1]; tailYs[i] = tailYs[i - 1] }
                }
                tailXs[0] = cx; tailYs[0] = cy
                val speed = (dist * 0.08f)
                    .coerceAtLeast(Settings.screenRatio * 0.12f)
                    .coerceAtMost(Settings.screenRatio * 0.5f)
                cx += (dx / dist) * speed
                cy += (dy / dist) * speed
            }
        }

        override fun draw(scope: DrawScope) {
            val frameF = frame.toFloat()
            val sizePulse = 1.0f + 0.25f * sin(frameF * 0.052f)
            val r = baseRadius * sizePulse
            val glowColor = color
            val sw = baseSw
            val center = Offset(cx, cy)

            val auraFramePhase = frameF * 0.04f
            for (ring in auraRings) {
                val auraR = r * ring.baseMult + r * ring.amp * sin(auraFramePhase + ring.phase)
                scope.drawCircle(
                    color = Color(Palette.withAlpha(glowColor, ring.alpha)),
                    radius = auraR,
                    center = center,
                    style = Stroke(width = sw * ring.strokeMult)
                )
            }

            scope.drawCircle(Color(bodyWhiteColor), r, center)
            scope.drawCircle(Color(glowWhite200), r, center, style = Stroke(width = sw))

            val innerR = r * 0.75f + r * 0.1f * sin(frameF * 0.025f + 5f)
            scope.drawCircle(Color(glowWhite160), innerR, center, style = Stroke(width = sw * 0.7f))

            if (returning && tailSize > 1) {
                drawGhostTail(scope, tailXs, tailYs, tailSize, r, glowColor, 0f, bodyWhiteColor)
            }
        }
    }
}

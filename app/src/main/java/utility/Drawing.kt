package utility

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb

object Drawing {

    var wallWidthParticleCount = 0
    var wallHeightParticleCount = 0

    private var canScoreListener: ((Unit) -> Unit)? = null
    private var cantScoreListener: ((Unit) -> Unit)? = null

    fun initialize() {
        wallHeightParticleCount = (Settings.screenHeight.toInt() - Settings.topGoalBottom.toInt() * 2) / Settings.longParticleSide.toInt()
        wallWidthParticleCount = Settings.screenWidth.toInt() / Settings.longParticleSide.toInt()

        canScoreListener?.let { GameEvents.canScore.disconnect(it) }
        cantScoreListener?.let { GameEvents.cantScore.disconnect(it) }
        canScoreListener = { Settings.canScore = true }
        cantScoreListener = { Settings.canScore = false }
        GameEvents.canScore.connect(canScoreListener!!)
        GameEvents.cantScore.connect(cantScoreListener!!)
        Effects.clearPersistentEffects()
        Effects.clearCollisionEffects()
    }

    // Score zone geometry (computed from Settings)
    private val highZoneLeft   get() = 0f
    private val highZoneTop    get() = 0f
    private val highZoneRight  get() = Settings.screenWidth
    private val highZoneBottom get() = Settings.topGoalBottom

    private val lowZoneLeft   get() = 0f
    private val lowZoneTop    get() = Settings.bottomGoalTop
    private val lowZoneRight  get() = Settings.screenWidth
    private val lowZoneBottom get() = Settings.screenHeight

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    fun DrawScope.drawFrame() {
        drawArenaBackground()
        drawChargeFill()
        drawIntoCanvas { nativeCanvas ->
            Effects.drawEffects(nativeCanvas.nativeCanvas)
        }
        drawPlayersCompose()
        drawWalls()
        drawAimArrows()
        drawArenaForeground()
        drawScoreFlash()
        drawScores(Logic.highPlayer, Logic.lowPlayer)
    }

    // -------------------------------------------------------------------------
    // Arena background
    // -------------------------------------------------------------------------

    fun DrawScope.drawArenaBackground() {
        drawRect(
            color = androidIntToComposeColor(PaintBucket.backgroundPaint.color),
            topLeft = Offset.Zero,
            size = Size(Settings.screenWidth, Settings.screenHeight)
        )
        drawTouchHighlights(Logic.highPlayer, Logic.lowPlayer)
    }

    fun DrawScope.drawTouchHighlights(highPlayer: Player, lowPlayer: Player) {
        if (highPlayer.isTouching) {
            val c = androidIntToComposeColor(PaintBucket.highPlayerHighlightPaint.color)
                .copy(alpha = PaintBucket.highPlayerHighlightPaint.alpha / 255f)
            drawRect(
                color = c,
                topLeft = Offset(0f, 0f),
                size = Size(Settings.screenWidth, Settings.middleY)
            )
        }
        if (lowPlayer.isTouching) {
            val c = androidIntToComposeColor(PaintBucket.lowPlayerHighlightPaint.color)
                .copy(alpha = PaintBucket.lowPlayerHighlightPaint.alpha / 255f)
            drawRect(
                color = c,
                topLeft = Offset(0f, Settings.middleY),
                size = Size(Settings.screenWidth, Settings.screenHeight - Settings.middleY)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Arena foreground (goal zones + canScore walls)
    // -------------------------------------------------------------------------

    fun DrawScope.drawArenaForeground() {
        val goalColor = androidIntToComposeColor(PaintBucket.goalPaint.color)
        // High goal zone
        drawRect(
            color = goalColor,
            topLeft = Offset(highZoneLeft, highZoneTop),
            size = Size(highZoneRight - highZoneLeft, highZoneBottom - highZoneTop)
        )
        // Low goal zone
        drawRect(
            color = goalColor,
            topLeft = Offset(lowZoneLeft, lowZoneTop),
            size = Size(lowZoneRight - lowZoneLeft, lowZoneBottom - lowZoneTop)
        )
        drawCanScoreWalls()
        drawGoalMenuHints()
    }

    fun DrawScope.drawCanScoreWalls() {
        val minDistance = Settings.screenRatio * 6f
        fun getAlpha(location: Float) = (1 - (location / minDistance)) * 200
        val highPlayer = Logic.highPlayer
        val lowPlayer = Logic.lowPlayer
        val baseAlpha = 255
        val defaultColorInt = PaintBucket.canScoreWallColor.toArgb()

        for (x in 0..wallWidthParticleCount) {
            val xPos = x * Settings.longParticleSide

            // Top canScore wall
            val highDist = highPlayer.puck.distanceTo(xPos, Settings.topGoalBottom) - Settings.screenRatio
            val lowDist  = lowPlayer.puck.distanceTo(xPos, Settings.topGoalBottom)  - Settings.screenRatio
            var wallColorInt = defaultColorInt
            var proximityAlpha = 0f
            if (highDist < minDistance) {
                proximityAlpha = getAlpha(highDist)
                wallColorInt = if (lowDist < highDist) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDist < minDistance) {
                val a = getAlpha(lowDist)
                if (a > proximityAlpha) {
                    proximityAlpha = a
                    wallColorInt = if (highDist < lowDist) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val topAlpha = maxOf(baseAlpha, proximityAlpha.toInt())
            val resolvedTop = if (proximityAlpha > baseAlpha) wallColorInt else defaultColorInt
            val topColor = androidIntToComposeColor(resolvedTop).copy(alpha = topAlpha.coerceIn(0, 255) / 255f)
            drawRect(
                color = topColor,
                topLeft = Offset(xPos, Settings.canScoreTopWallTop),
                size = Size(Settings.longParticleSide, Settings.canScoreTopWallBottom - Settings.canScoreTopWallTop)
            )

            // Bottom canScore wall
            val highDistB = highPlayer.puck.distanceTo(xPos, Settings.bottomGoalTop) - Settings.screenRatio
            val lowDistB  = lowPlayer.puck.distanceTo(xPos, Settings.bottomGoalTop)  - Settings.screenRatio
            var wallColorIntB = defaultColorInt
            var proximityAlphaB = 0f
            if (highDistB < minDistance) {
                proximityAlphaB = getAlpha(highDistB)
                wallColorIntB = if (lowDistB < highDistB) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistB < minDistance) {
                val a = getAlpha(lowDistB)
                if (a > proximityAlphaB) {
                    proximityAlphaB = a
                    wallColorIntB = if (highDistB < lowDistB) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val botAlpha = maxOf(baseAlpha, proximityAlphaB.toInt())
            val resolvedBot = if (proximityAlphaB > baseAlpha) wallColorIntB else defaultColorInt
            val botColor = androidIntToComposeColor(resolvedBot).copy(alpha = botAlpha.coerceIn(0, 255) / 255f)
            drawRect(
                color = botColor,
                topLeft = Offset(xPos, Settings.canScoreBottomWallTop),
                size = Size(Settings.longParticleSide, Settings.canScoreBottomWallBottom - Settings.canScoreBottomWallTop)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Charge fill
    // -------------------------------------------------------------------------

    private var chargeFillFrame = 0

    fun DrawScope.drawChargeFill() {
        chargeFillFrame++
        if (Settings.highPlayerChargeFill) drawPlayerChargeFill(Logic.highPlayer, isHigh = true)
        if (Settings.lowPlayerChargeFill) drawPlayerChargeFill(Logic.lowPlayer, isHigh = false)
    }

    private fun DrawScope.drawPlayerChargeFill(player: Player, isHigh: Boolean) {
        val effect = player.puck.renderer.effect ?: return
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return
        val theme = effect.theme
        val rawColor = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> theme.main.primary
            ChargePhase.SweetSpot -> theme.shield.primary
            else -> return
        }
        val alpha = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> 128 / 255f
            ChargePhase.SweetSpot -> {
                val pulse = 0.7f + 0.3f * sin(chargeFillFrame * 0.35f)
                (pulse).coerceIn(0f, 1f)
            }
            else -> return
        }
        val color = androidIntToComposeColor(rawColor).copy(alpha = alpha)
        if (isHigh) {
            val bottom = Settings.topGoalBottom + ratio * (Settings.middleY - Settings.topGoalBottom)
            drawRect(
                color = color,
                topLeft = Offset(0f, Settings.topGoalBottom),
                size = Size(Settings.screenWidth, bottom - Settings.topGoalBottom)
            )
        } else {
            val top = Settings.bottomGoalTop - ratio * (Settings.bottomGoalTop - Settings.middleY)
            drawRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size(Settings.screenWidth, Settings.bottomGoalTop - top)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Players — must use Android Canvas because Player.drawTo() takes Canvas
    // -------------------------------------------------------------------------

    fun DrawScope.drawPlayersCompose() {
        drawIntoCanvas { canvas ->
            Logic.highPlayer.drawTo(canvas.nativeCanvas)
            Logic.lowPlayer.drawTo(canvas.nativeCanvas)
        }
    }

    // -------------------------------------------------------------------------
    // Walls
    // -------------------------------------------------------------------------

    fun DrawScope.drawWalls() {
        val minDistance = Settings.screenRatio * 6f
        fun getAlpha(location: Float) = (1 - (location / minDistance)) * 200
        val highPlayer = Logic.highPlayer
        val lowPlayer = Logic.lowPlayer

        for (x in 0 until wallWidthParticleCount) {
            val position = x * Settings.longParticleSide
            val highDistanceToHigh = highPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.shortParticleSide) - Settings.screenRatio
            val lowDistanceToHigh  = lowPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.longParticleSide)  - Settings.screenRatio

            var wallColorInt = 0
            var particleAlpha = 0f
            if (highDistanceToHigh < minDistance) {
                particleAlpha = getAlpha(highDistanceToHigh)
                wallColorInt = if (lowDistanceToHigh < highDistanceToHigh) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistanceToHigh < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToHigh)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistanceToHigh < lowDistanceToHigh) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val topWallColor = androidIntToComposeColor(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            drawRect(
                color = topWallColor,
                topLeft = Offset(position, 0f),
                size = Size(Settings.longParticleSide, Settings.topGoalBottom)
            )

            particleAlpha = 0f

            val highDistanceToLow = highPlayer.puck.distanceTo(position, Settings.bottomGoalTop + Settings.longParticleSide) - Settings.screenRatio
            val lowDistanceToLow  = lowPlayer.puck.distanceTo(position, Settings.bottomGoalTop + Settings.longParticleSide)  - Settings.screenRatio
            if (highDistanceToLow < minDistance) {
                particleAlpha = getAlpha(highDistanceToLow)
                wallColorInt = if (lowDistanceToLow < highDistanceToLow) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistanceToLow < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToLow)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistanceToLow < lowDistanceToLow) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val botWallColor = androidIntToComposeColor(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            drawRect(
                color = botWallColor,
                topLeft = Offset(position, Settings.bottomGoalTop),
                size = Size(Settings.longParticleSide, Settings.screenHeight - Settings.bottomGoalTop)
            )
        }

        for (y in 0 until (wallHeightParticleCount + 1)) {
            val position = y * Settings.longParticleSide + Settings.topGoalBottom
            if (position < Settings.topGoalBottom || position > Settings.bottomGoalTop) continue

            // Left wall
            val highDistLeft = highPlayer.puck.distanceTo(Settings.shortParticleSide, position) - Settings.screenRatio
            val lowDistLeft  = lowPlayer.puck.distanceTo(Settings.shortParticleSide, position)  - Settings.screenRatio
            var wallColorInt = 0
            var particleAlpha = 0f
            if (highDistLeft < minDistance) {
                particleAlpha = getAlpha(highDistLeft)
                wallColorInt = if (lowDistLeft < highDistLeft) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistLeft < minDistance) {
                val tempAlpha = getAlpha(lowDistLeft)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistLeft < lowDistLeft) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val leftColor = androidIntToComposeColor(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            val leftBottom = if (y < wallHeightParticleCount) position + Settings.longParticleSide else Settings.bottomGoalTop
            drawRect(
                color = leftColor,
                topLeft = Offset(0f, position),
                size = Size(Settings.shortParticleSide, leftBottom - position)
            )

            particleAlpha = 0f

            // Right wall
            val highDistRight = highPlayer.puck.distanceTo(Settings.screenWidth - Settings.shortParticleSide, position) - Settings.screenRatio
            val lowDistRight  = lowPlayer.puck.distanceTo(Settings.screenWidth - Settings.shortParticleSide, position)  - Settings.screenRatio
            if (highDistRight < minDistance) {
                particleAlpha = getAlpha(highDistRight)
                wallColorInt = if (lowDistRight < highDistRight) lowPlayer.puck.strokeColor else highPlayer.puck.strokeColor
            }
            if (lowDistRight < minDistance) {
                val tempAlpha = getAlpha(lowDistRight)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistRight < lowDistRight) highPlayer.puck.strokeColor else lowPlayer.puck.strokeColor
                }
            }
            val rightColor = androidIntToComposeColor(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            val rightBottom = if (y < wallHeightParticleCount) position + Settings.longParticleSide else Settings.bottomGoalTop
            drawRect(
                color = rightColor,
                topLeft = Offset(Settings.screenWidth - Settings.shortParticleSide, position),
                size = Size(Settings.shortParticleSide, rightBottom - position)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Aim arrows
    // -------------------------------------------------------------------------

    private val aimArrowPath = Path()
    private var aimArrowFrame = 0

    fun DrawScope.drawAimArrows() {
        aimArrowFrame++
        if (Settings.lowPlayerArrow) drawAimArrow(Logic.lowPlayer, isHigh = false)
        if (Settings.highPlayerArrow) drawAimArrow(Logic.highPlayer, isHigh = true)
    }

    private fun DrawScope.drawAimArrow(player: Player, isHigh: Boolean) {
        if (!player.isFlingHeld) return

        val tailX = player.flingCurrent.x
        val tailY = player.flingCurrent.y
        val headX = player.flingStart.x
        val headY = player.flingStart.y
        val dx = headX - tailX
        val dy = headY - tailY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < Settings.screenRatio * 0.3f) return

        val themeColor = if (isHigh) PaintBucket.highBallStroke else PaintBucket.lowBallStroke
        val chargeColor = PaintBucket.effectColor

        val range = (Settings.sweetSpotMin - Settings.chargeStart).toFloat()
        val ratio = ((player.charge - Settings.chargeStart) / range).coerceIn(0f, 1f)
        val inSweetSpot = player.charge >= Settings.sweetSpotMin && player.charge <= Settings.sweetSpotMax
        val overcharged = player.chargePowerLocked

        val ux = dx / dist
        val uy = dy / dist

        val maxArrowLength = Settings.screenWidth / 3f
        val visualDist = dist.coerceAtMost(maxArrowLength)
        val visualTailX = headX - ux * visualDist
        val visualTailY = headY - uy * visualDist

        var themeAlpha = 1f
        var chargeAlpha = 1f
        var fillLen = visualDist * ratio
        var fillColor = chargeColor

        if (inSweetSpot) {
            val pulse = 0.7f + 0.3f * sin(aimArrowFrame * 0.35f)
            chargeAlpha = pulse.coerceIn(0f, 1f)
            themeAlpha = chargeAlpha
            fillLen = visualDist
        }
        if (overcharged) {
            val fade = (player.overchargeFrames / 12f).coerceIn(0f, 1f)
            fillColor = lerpColor(chargeColor, themeColor, fade)
        }

        val strokeWidth = Settings.strokeWidth * 1.3f

        val fillEndX = visualTailX + ux * fillLen
        val fillEndY = visualTailY + uy * fillLen

        if (fillLen < visualDist) {
            drawLine(
                color = themeColor.copy(alpha = themeAlpha),
                start = Offset(fillEndX, fillEndY),
                end = Offset(headX, headY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        if (fillLen > 0f) {
            drawLine(
                color = fillColor.copy(alpha = chargeAlpha),
                start = Offset(visualTailX, visualTailY),
                end = Offset(fillEndX, fillEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        val headFilled = fillLen >= visualDist
        val headColor = (if (headFilled) fillColor else themeColor)
            .copy(alpha = if (headFilled) chargeAlpha else themeAlpha)
        val headSize = Settings.screenRatio * 0.8f
        val perpX = -uy
        val perpY = ux
        aimArrowPath.reset()
        aimArrowPath.moveTo(headX + ux * headSize, headY + uy * headSize)
        aimArrowPath.lineTo(
            headX - ux * headSize * 0.2f + perpX * headSize * 0.75f,
            headY - uy * headSize * 0.2f + perpY * headSize * 0.75f
        )
        aimArrowPath.lineTo(
            headX - ux * headSize * 0.2f - perpX * headSize * 0.75f,
            headY - uy * headSize * 0.2f - perpY * headSize * 0.75f
        )
        aimArrowPath.close()
        drawPath(aimArrowPath, color = headColor, style = Fill)
    }

    private fun lerpColor(from: Color, to: Color, t: Float): Color {
        return Color(
            red   = from.red   + (to.red   - from.red)   * t,
            green = from.green + (to.green - from.green) * t,
            blue  = from.blue  + (to.blue  - from.blue)  * t,
            alpha = 1f
        )
    }

    // -------------------------------------------------------------------------
    // Scores
    // -------------------------------------------------------------------------

    private val scoreTextPaint = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun DrawScope.drawScores(highPlayer: Player, lowPlayer: Player) {
        scoreTextPaint.textSize = Settings.topGoalBottom * 0.85f
        val xMargin = Settings.screenRatio * 3f
        val yMargin = Settings.topGoalBottom * 0.2f
        val scoreY = Settings.screenHeight - yMargin

        // High player — mirrored
        val highX = xMargin + Settings.scoreOffsetHigh
        val midX = Settings.screenWidth / 2f
        val midY = Settings.screenHeight / 2f
        val highPop = Settings.highScorePopTicker
        withTransform({ scale(-1f, -1f, pivot = Offset(midX, midY)) }) {
            if (Settings.scorePopEnabled && !highPop.finished) {
                highPop.tick
                val scale = 1f + sin(highPop.ratio * Math.PI.toFloat())
                withTransform({ scale(scale, scale, pivot = Offset(highX, scoreY)) }) {
                    drawIntoCanvas { c -> c.nativeCanvas.drawText(highPlayer.cachedScoreText, highX, scoreY, scoreTextPaint) }
                }
            } else {
                drawIntoCanvas { c -> c.nativeCanvas.drawText(highPlayer.cachedScoreText, highX, scoreY, scoreTextPaint) }
            }
        }

        // Low player — normal
        val lowX = xMargin + Settings.scoreOffsetLow
        val lowPop = Settings.lowScorePopTicker
        if (Settings.scorePopEnabled && !lowPop.finished) {
            lowPop.tick
            val scale = 1f + sin(lowPop.ratio * Math.PI.toFloat())
            withTransform({ scale(scale, scale, pivot = Offset(lowX, scoreY)) }) {
                drawIntoCanvas { c -> c.nativeCanvas.drawText(lowPlayer.cachedScoreText, lowX, scoreY, scoreTextPaint) }
            }
        } else {
            drawIntoCanvas { c -> c.nativeCanvas.drawText(lowPlayer.cachedScoreText, lowX, scoreY, scoreTextPaint) }
        }
    }

    fun DrawScope.drawScoreFlash() {
        if (!Settings.scoreFlashEnabled || Settings.scoreFlashAlpha <= 0f) return
        val flashColor = androidIntToComposeColor(Settings.scoreFlashColor)
            .copy(alpha = (Settings.scoreFlashAlpha / 255f).coerceIn(0f, 1f))
        drawRect(
            color = flashColor,
            topLeft = Offset.Zero,
            size = Size(Settings.screenWidth, Settings.screenHeight)
        )
        Settings.scoreFlashAlpha -= 8f
    }

    // -------------------------------------------------------------------------
    // Mirror text helpers (kept for callers in PlayView / TutorialView)
    // -------------------------------------------------------------------------

    fun DrawScope.mirrorText(text: String, x: Float, y: Float, textPaint: Paint) {
        withTransform({ scale(-1f, -1f, pivot = Offset(Settings.screenWidth / 2f, Settings.screenHeight / 2f)) }) {
            drawIntoCanvas { c -> c.nativeCanvas.drawText(text, x, y, textPaint) }
        }
        drawIntoCanvas { c -> c.nativeCanvas.drawText(text, x, y, textPaint) }
    }

    fun DrawScope.mirrorText(topText: String, bottomText: String, x: Float, y: Float, textPaint: Paint) {
        withTransform({ scale(-1f, -1f, pivot = Offset(Settings.screenWidth / 2f, Settings.screenHeight / 2f)) }) {
            drawIntoCanvas { c -> c.nativeCanvas.drawText(topText, x, y, textPaint) }
        }
        drawIntoCanvas { c -> c.nativeCanvas.drawText(bottomText, x, y, textPaint) }
    }

    // -------------------------------------------------------------------------
    // Tip / hint UI
    // -------------------------------------------------------------------------

    var highTipIndex: Int = 0
        private set
    var lowTipIndex: Int = 0
        private set

    fun resetTipIndices() {
        highTipIndex = 0
        lowTipIndex = 0
    }

    fun cycleHighTip() { highTipIndex = (highTipIndex + 1) % 5 }
    fun cycleLowTip()  { lowTipIndex  = (lowTipIndex  + 1) % 5 }

    fun DrawScope.drawGoalMenuHints() { /* placeholder */ }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private fun androidIntToComposeColor(color: Int): Color {
        return Color(
            red   = AndroidColor.red(color)   / 255f,
            green = AndroidColor.green(color) / 255f,
            blue  = AndroidColor.blue(color)  / 255f,
            alpha = AndroidColor.alpha(color) / 255f
        )
    }
}

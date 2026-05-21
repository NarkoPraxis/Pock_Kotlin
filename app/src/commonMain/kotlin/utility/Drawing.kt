package utility

import enums.ChargeMeterStyle
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

object Drawing {

    var wallWidthParticleCount = 0
    var wallHeightParticleCount = 0

    private var canScoreListener: ((Unit) -> Unit)? = null
    private var cantScoreListener: ((Unit) -> Unit)? = null

    private var textMeasurer: TextMeasurer? = null

    fun initializeTextMeasurer(m: TextMeasurer) {
        textMeasurer = m
    }

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
        if (!Logic.isInitialized) return
        drawChargeFill()
        if (!Settings.isDemoMode) with(Effects) { drawEffects() }
        drawPlayersCompose()
        drawWalls()
        if (!Settings.isDemoMode) drawTimer()
        drawAimArrows()
        drawArenaForeground()
        drawBallPopups()
        drawScoreFlash()
        if (!Settings.isDemoMode) drawScores(Logic.highPlayer, Logic.lowPlayer)
        Logic.updateTimer()

    }

    fun DrawScope.drawBallPopups() {
        with(Logic.highBallPopup) { drawTo() }
        with(Logic.lowBallPopup) { drawTo() }
    }

    // -------------------------------------------------------------------------
    // Arena background
    // -------------------------------------------------------------------------

    fun DrawScope.drawArenaBackground() {
        drawRect(
            color = PaintBucket.backgroundColor,
            topLeft = Offset.Zero,
            size = Size(Settings.screenWidth, Settings.screenHeight)
        )
        if (Logic.isInitialized) drawTouchHighlights(Logic.highPlayer, Logic.lowPlayer)
    }

    fun DrawScope.drawTouchHighlights(highPlayer: Player, lowPlayer: Player) {
        if (highPlayer.isTouching) {
            drawRect(
                color = PaintBucket.highPlayerHighlightColor,
                topLeft = Offset(0f, 0f),
                size = Size(Settings.screenWidth, Settings.middleY)
            )
        }
        if (lowPlayer.isTouching) {
            drawRect(
                color = PaintBucket.lowPlayerHighlightColor,
                topLeft = Offset(0f, Settings.middleY),
                size = Size(Settings.screenWidth, Settings.screenHeight - Settings.middleY)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Arena foreground (goal zones + canScore walls)
    // -------------------------------------------------------------------------

    fun DrawScope.drawArenaForeground() {
        val canScore = Settings.canScore
        val highGoalColor = if (canScore) PaintBucket.highShieldPrimary else PaintBucket.highShieldSecondary
        val lowGoalColor = if (canScore) PaintBucket.lowShieldPrimary else PaintBucket.lowShieldSecondary
        drawRect(
            color = highGoalColor,
            topLeft = Offset(highZoneLeft, highZoneTop),
            size = Size(highZoneRight - highZoneLeft, highZoneBottom - highZoneTop)
        )
        drawRect(
            color = lowGoalColor,
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
        val highDefaultColorInt = PaintBucket.highShieldSecondary.toArgb()
        val lowDefaultColorInt = PaintBucket.lowShieldSecondary.toArgb()

        for (x in 0..wallWidthParticleCount) {
            val xPos = x * Settings.longParticleSide

            val highDist = highPlayer.puck.distanceTo(xPos, Settings.topGoalBottom) - Settings.screenRatio
            val lowDist  = lowPlayer.puck.distanceTo(xPos, Settings.topGoalBottom)  - Settings.screenRatio
            var wallColorInt = highDefaultColorInt
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
            val resolvedTop = if (proximityAlpha > baseAlpha) wallColorInt else highDefaultColorInt
            val topColor = Color(resolvedTop).copy(alpha = topAlpha.coerceIn(0, 255) / 255f)
            drawRect(
                color = topColor,
                topLeft = Offset(xPos, Settings.canScoreTopWallTop),
                size = Size(Settings.longParticleSide, Settings.canScoreTopWallBottom - Settings.canScoreTopWallTop)
            )

            val highDistB = highPlayer.puck.distanceTo(xPos, Settings.bottomGoalTop) - Settings.screenRatio
            val lowDistB  = lowPlayer.puck.distanceTo(xPos, Settings.bottomGoalTop)  - Settings.screenRatio
            var wallColorIntB = lowDefaultColorInt
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
            val resolvedBot = if (proximityAlphaB > baseAlpha) wallColorIntB else lowDefaultColorInt
            val botColor = Color(resolvedBot).copy(alpha = botAlpha.coerceIn(0, 255) / 255f)
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
        when (Settings.highPlayerChargeMeterStyle) {
            ChargeMeterStyle.FullScreen -> drawPlayerChargeFill(Logic.highPlayer, isHigh = true)
            ChargeMeterStyle.SideBar    -> drawPlayerSideBarCharge(Logic.highPlayer, isHigh = true)
            ChargeMeterStyle.None       -> Unit
        }
        when (Settings.lowPlayerChargeMeterStyle) {
            ChargeMeterStyle.FullScreen -> drawPlayerChargeFill(Logic.lowPlayer, isHigh = false)
            ChargeMeterStyle.SideBar    -> drawPlayerSideBarCharge(Logic.lowPlayer, isHigh = false)
            ChargeMeterStyle.None       -> Unit
        }
    }

    private fun DrawScope.resolveChargeColor(player: Player): Pair<Int, Float>? {
        val effect = player.puck.renderer.effect ?: return null
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return null
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return null
        val theme = effect.theme
        val rawColor = when (ph) {
            ChargePhase.Building -> theme.main.primary
            ChargePhase.Draining -> theme.inert.secondary
            ChargePhase.SweetSpot -> theme.shield.secondary
            else -> return null
        }
        val alpha = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> 255f
            ChargePhase.SweetSpot -> (0.7f + 0.3f * sin(chargeFillFrame * 0.35f)).coerceIn(0f, 1f)
            else -> return null
        }
        return rawColor to alpha
    }

    private fun DrawScope.drawPlayerChargeFill(player: Player, isHigh: Boolean) {
        val effect = player.puck.renderer.effect ?: return
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return
        val (rawColor, alpha) = resolveChargeColor(player) ?: return
        val color = Color(rawColor).copy(alpha = alpha)
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

    private fun DrawScope.drawPlayerSideBarCharge(player: Player, isHigh: Boolean) {
        val effect = player.puck.renderer.effect ?: return
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return
        val (rawColor, alpha) = resolveChargeColor(player) ?: return
        val color = Color(rawColor).copy(alpha = alpha)
        val barWidth = Settings.shortParticleSide
        if (isHigh) {
            val bottom = Settings.topGoalBottom + ratio * (Settings.middleY - Settings.topGoalBottom)
            val barHeight = bottom - Settings.topGoalBottom
            drawRect(color = color, topLeft = Offset(0f, Settings.topGoalBottom), size = Size(barWidth, barHeight))
            drawRect(color = color, topLeft = Offset(Settings.screenWidth - barWidth, Settings.topGoalBottom), size = Size(barWidth, barHeight))
        } else {
            val top = Settings.bottomGoalTop - ratio * (Settings.bottomGoalTop - Settings.middleY)
            val barHeight = Settings.bottomGoalTop - top
            drawRect(color = color, topLeft = Offset(0f, top), size = Size(barWidth, barHeight))
            drawRect(color = color, topLeft = Offset(Settings.screenWidth - barWidth, top), size = Size(barWidth, barHeight))
        }
    }

    // -------------------------------------------------------------------------
    // Players
    // -------------------------------------------------------------------------

    fun DrawScope.drawPlayersCompose() {
        with(Logic.highPlayer) { drawTo() }
        with(Logic.lowPlayer) { drawTo() }
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
            val topWallColor = Color(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
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
            val botWallColor = Color(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            drawRect(
                color = botWallColor,
                topLeft = Offset(position, Settings.bottomGoalTop),
                size = Size(Settings.longParticleSide, Settings.screenHeight - Settings.bottomGoalTop)
            )
        }

        for (y in 0 until (wallHeightParticleCount + 1)) {
            val position = y * Settings.longParticleSide + Settings.topGoalBottom
            if (position < Settings.topGoalBottom || position > Settings.bottomGoalTop) continue

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
            val leftColor = Color(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
            val leftBottom = if (y < wallHeightParticleCount) position + Settings.longParticleSide else Settings.bottomGoalTop
            drawRect(
                color = leftColor,
                topLeft = Offset(0f, position),
                size = Size(Settings.shortParticleSide, leftBottom - position)
            )

            particleAlpha = 0f

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
            val rightColor = Color(wallColorInt).copy(alpha = (particleAlpha / 255f).coerceIn(0f, 1f))
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
        val chargeColor = if (isHigh) PaintBucket.highShieldSecondary else PaintBucket.lowShieldSecondary

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

    fun DrawScope.drawScores(highPlayer: Player, lowPlayer: Player) {
        val tm = textMeasurer ?: return
        val density = drawContext.density.density
        val fontSizeSp = (PaintBucket.scoreFontSize / density)
        val style = TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
            color = PaintBucket.black
        )

        val xMargin = Settings.screenRatio * 3f
        val yMargin = 0
        val scoreY = Settings.screenHeight - yMargin

        val highX = xMargin + Settings.scoreOffsetHigh
        val midX = Settings.screenWidth / 2f
        val midY = Settings.screenHeight / 2f
        val highResult = tm.measure(highPlayer.cachedScoreText, style)

        withTransform({ scale(-1f, -1f, pivot = Offset(midX, midY)) }) {
            drawText(highResult, color=PaintBucket.white, topLeft = Offset(highX, scoreY - highResult.size.height))
        }

        val lowX = xMargin + Settings.scoreOffsetLow
        val lowResult = tm.measure(lowPlayer.cachedScoreText, style)

        drawText(lowResult, color=PaintBucket.white, topLeft = Offset(lowX, scoreY - lowResult.size.height))

    }

    fun DrawScope.drawTimer() {
        if (Settings.timeLimitMinutes == 0) return
        if (!Logic.timerStarted || Logic.timerHidden) return
        val tm = textMeasurer ?: return

        val density = drawContext.density.density
        val fontSizePx = Settings.screenRatio * 1.2f
        val fontSizeSp = fontSizePx / density
        val style = androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
            color = PaintBucket.timerColor
        )
        val result = tm.measure(Logic.timerSecondsRemaining.toString(), style)

        val midX = Settings.screenWidth / 2f
        val midY = Settings.screenHeight / 2f
        val timerX = midX - result.size.width / 2f
        val timerPad = Settings.screenRatio * 0.4f
        val timerY = Settings.bottomGoalTop - timerPad - result.size.height

        // High player — mirrored to appear above the top goal from their perspective
        withTransform({ scale(-1f, -1f, pivot = Offset(midX, midY)) }) {
            drawText(result, topLeft = Offset(timerX, timerY))
        }
        // Low player — just above the bottom goal zone
        drawText(result, topLeft = Offset(timerX, timerY))
    }

    fun DrawScope.drawScoreFlash() {
        if (!Settings.scoreFlashEnabled || Settings.scoreFlashAlpha <= 0f) return
        val flashColor = Color(Settings.scoreFlashColor)
            .copy(alpha = (Settings.scoreFlashAlpha / 255f).coerceIn(0f, 1f))
        drawRect(
            color = flashColor,
            topLeft = Offset.Zero,
            size = Size(Settings.screenWidth, Settings.screenHeight)
        )
        Settings.scoreFlashAlpha -= 8f
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
}

package utility

import enums.ChargeMeterStyle
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.RainbowOverride
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
    // Rainbow override helpers (for arena elements drawn outside the puck renderer)
    //
    // These elements read PaintBucket.high*/low* (the same custom-colour source that feeds
    // ColorTheme) rather than the puck's responsiveColorGroup, so they need to resolve the
    // strobe themselves. The strobe tick for a player is its renderer.frame, so they stay in
    // lockstep with that player's ball. Gating per element (see answers): goals → shield flag,
    // walls/arena/charge-meters/aim-arrows → main flag.
    // -------------------------------------------------------------------------

    private fun playerFrame(isHigh: Boolean): Int =
        (if (isHigh) Logic.highPlayer else Logic.lowPlayer).puck.renderer.frame

    // ---- Arena background tint (slow strobe, latched during an alert flash) ----
    private var highArenaFlashColor: Int? = null
    private var lowArenaFlashColor: Int? = null

    /**
     * The half-screen background tint colour for a player. Under a main-rainbow override it strobes
     * at a quarter speed (the hue only advances every 4th frame) so the faint full-screen tint never
     * becomes a fast flash. While an alert flash is active the colour is latched at the frame the
     * flash began and held for the whole flash (the flash pulses alpha, not hue).
     */
    private fun arenaTintColor(isHigh: Boolean, flashing: Boolean): Color {
        val configured = if (isHigh) PaintBucket.highBallFill else PaintBucket.lowBallFill
        if (!RainbowOverride.mainActive(isHigh)) {
            if (isHigh) highArenaFlashColor = null else lowArenaFlashColor = null
            return configured
        }
        val slow = RainbowOverride.primaryColor(RainbowOverride.hue(isHigh, playerFrame(isHigh) / 4))
        return if (flashing) {
            val latched = (if (isHigh) highArenaFlashColor else lowArenaFlashColor)
                ?: slow.toArgb().also { if (isHigh) highArenaFlashColor = it else lowArenaFlashColor = it }
            Color(latched)
        } else {
            if (isHigh) highArenaFlashColor = null else lowArenaFlashColor = null
            slow
        }
    }

    // ---- Goal zones (shield-gated, full-speed strobe) ----
    private fun goalColor(isHigh: Boolean, canScore: Boolean): Color {
        if (RainbowOverride.shieldActive(isHigh)) {
            val hue = RainbowOverride.hue(isHigh, playerFrame(isHigh))
            return if (canScore) RainbowOverride.primaryColor(hue) else RainbowOverride.secondaryColor(hue)
        }
        return if (canScore) {
            if (isHigh) PaintBucket.highShieldPrimary else PaintBucket.lowShieldPrimary
        } else {
            if (isHigh) PaintBucket.highShieldSecondary else PaintBucket.lowShieldSecondary
        }
    }

    // ---- canScore (goal-open) wall, the closing layer over each goal. Shield-gated like the goal
    // zone, but strobes at the INVERTED hue so the open zone and the closing wall stay a contrasting
    // pair and the opening animation remains visible. ----
    private fun canScoreWallDefaultColor(isHigh: Boolean): Int {
        if (RainbowOverride.shieldActive(isHigh)) {
            val hue = RainbowOverride.invertedHue(RainbowOverride.hue(isHigh, playerFrame(isHigh)))
            return RainbowOverride.primaryColor(hue).toArgb()
        }
        return (if (isHigh) PaintBucket.highShieldSecondary else PaintBucket.lowShieldSecondary).toArgb()
    }

    // ---- Wall highlight (main-gated). Mirrors the puck's stroke colour as it nears a wall. ----
    private fun playerWallStroke(isHigh: Boolean): Int {
        val player = if (isHigh) Logic.highPlayer else Logic.lowPlayer
        return if (RainbowOverride.mainActive(isHigh))
            RainbowOverride.secondaryColor(RainbowOverride.hue(isHigh, player.puck.renderer.frame)).toArgb()
        else player.puck.strokeColor
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    fun DrawScope.drawFrame() {
        drawArenaBackground()
        if (!Logic.isInitialized) return
        drawChargeFill()
        if (!Settings.isDemoMode) with(Effects) { drawEffects() }
        drawPlayersCompose()
        if (!Settings.isDemoMode) with(Effects) { drawPriorityEffects() }
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
        // Alert pulse triggers when EITHER:
        //   • the OTHER side has 2+ fingers (one finger should move here), OR
        //   • this side's player has dragged across the midline (bring it back).
        // Same theme.main.primary + sweet-spot pulse used by charge fills.
        val highFlash = Logic.lowSideHasMultiTouch || Logic.highPlayerCrossedCenter
        val lowFlash  = Logic.highSideHasMultiTouch || Logic.lowPlayerCrossedCenter
        val pulseAlpha = (0.7f + 0.3f * sin(chargeFillFrame * 0.35f)).coerceIn(0f, 1f)

        // Faded-ness preserved: alpha is unchanged (.2f at rest, pulse while flashing); only the
        // base hue is rainbow-resolved (quarter-speed) so the tint stays a subtle backdrop.
        var color = if (highFlash) arenaTintColor(isHigh = true, flashing = true).copy(alpha = pulseAlpha)
                    else arenaTintColor(isHigh = true, flashing = false).copy(alpha = .2f)
        drawRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(Settings.screenWidth, Settings.middleY)
        )

        color = if (lowFlash) arenaTintColor(isHigh = false, flashing = true).copy(alpha = pulseAlpha)
                    else arenaTintColor(isHigh = false, flashing = false).copy(alpha = .2f)
        drawRect(
            color = color,
            topLeft = Offset(0f, Settings.middleY),
            size = Size(Settings.screenWidth, Settings.screenHeight - Settings.middleY)
        )

    }

    // -------------------------------------------------------------------------
    // Arena foreground (goal zones + canScore walls)
    // -------------------------------------------------------------------------

    fun DrawScope.drawArenaForeground() {
        val canScore = Settings.canScore
        val highGoalColor = goalColor(isHigh = true, canScore = canScore)
        val lowGoalColor = goalColor(isHigh = false, canScore = canScore)
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
        val highStroke = playerWallStroke(isHigh = true)
        val lowStroke = playerWallStroke(isHigh = false)
        val baseAlpha = 255
        val highDefaultColorInt = canScoreWallDefaultColor(isHigh = true)
        val lowDefaultColorInt = canScoreWallDefaultColor(isHigh = false)

        for (x in 0..wallWidthParticleCount) {
            val xPos = x * Settings.longParticleSide

            val highDist = highPlayer.puck.distanceTo(xPos, Settings.topGoalBottom) - Settings.screenRatio
            val lowDist  = lowPlayer.puck.distanceTo(xPos, Settings.topGoalBottom)  - Settings.screenRatio
            var wallColorInt = highDefaultColorInt
            var proximityAlpha = 0f
            if (highDist < minDistance) {
                proximityAlpha = getAlpha(highDist)
                wallColorInt = if (lowDist < highDist) lowStroke else highStroke
            }
            if (lowDist < minDistance) {
                val a = getAlpha(lowDist)
                if (a > proximityAlpha) {
                    proximityAlpha = a
                    wallColorInt = if (highDist < lowDist) highStroke else lowStroke
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
                wallColorIntB = if (lowDistB < highDistB) lowStroke else highStroke
            }
            if (lowDistB < minDistance) {
                val a = getAlpha(lowDistB)
                if (a > proximityAlphaB) {
                    proximityAlphaB = a
                    wallColorIntB = if (highDistB < lowDistB) highStroke else lowStroke
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

    // FullScreen charge meter bakes a single hue per fill cycle (a strobing full-screen tint would
    // be an epilepsy risk). SideBar meters strobe live. Latches cleared when the charge ends.
    private var highFullScreenHue: Float? = null
    private var lowFullScreenHue: Float? = null

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

    /**
     * Charge-meter colour. Under a main-rainbow override the customisable phases strobe (Building →
     * primary, SweetSpot → secondary); Draining stays the grey inert colour. [frozenHue] forces a
     * fixed hue (FullScreen bake-at-fill); null uses the player's live frame (SideBar live strobe).
     */
    private fun DrawScope.resolveChargeColor(player: Player, isHigh: Boolean, frozenHue: Float?): Pair<Int, Float>? {
        val effect = player.puck.renderer.effect ?: return null
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return null
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return null
        val theme = effect.theme
        val mainRainbow = RainbowOverride.mainActive(isHigh)
        val hue = frozenHue ?: RainbowOverride.hue(isHigh, player.puck.renderer.frame)
        val rawColor = when (ph) {
            ChargePhase.Building -> if (mainRainbow) RainbowOverride.primaryColor(hue).toArgb() else theme.main.primary
            ChargePhase.Draining -> theme.inert.secondary
            ChargePhase.SweetSpot -> if (mainRainbow) RainbowOverride.secondaryColor(hue).toArgb() else theme.shield.secondary
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
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) {
            if (isHigh) highFullScreenHue = null else lowFullScreenHue = null
            return
        }
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) {
            if (isHigh) highFullScreenHue = null else lowFullScreenHue = null
            return
        }
        // Bake one hue for the whole fill so the full-screen tint holds a single colour, not a strobe.
        val frozen = (if (isHigh) highFullScreenHue else lowFullScreenHue)
            ?: RainbowOverride.hue(isHigh, player.puck.renderer.frame).also {
                if (isHigh) highFullScreenHue = it else lowFullScreenHue = it
            }
        val (rawColor, alpha) = resolveChargeColor(player, isHigh, frozen) ?: return
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
        // SideBar strobes live (thin side bars, no epilepsy concern) — no frozen hue.
        val (rawColor, alpha) = resolveChargeColor(player, isHigh, frozenHue = null) ?: return
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
        val highStroke = playerWallStroke(isHigh = true)
        val lowStroke = playerWallStroke(isHigh = false)

        for (x in 0 until wallWidthParticleCount) {
            val position = x * Settings.longParticleSide
            val highDistanceToHigh = highPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.shortParticleSide) - Settings.screenRatio
            val lowDistanceToHigh  = lowPlayer.puck.distanceTo(position, Settings.topGoalBottom - Settings.longParticleSide)  - Settings.screenRatio

            var wallColorInt = 0
            var particleAlpha = 0f
            if (highDistanceToHigh < minDistance) {
                particleAlpha = getAlpha(highDistanceToHigh)
                wallColorInt = if (lowDistanceToHigh < highDistanceToHigh) lowStroke else highStroke
            }
            if (lowDistanceToHigh < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToHigh)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistanceToHigh < lowDistanceToHigh) highStroke else lowStroke
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
                wallColorInt = if (lowDistanceToLow < highDistanceToLow) lowStroke else highStroke
            }
            if (lowDistanceToLow < minDistance) {
                val tempAlpha = getAlpha(lowDistanceToLow)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistanceToLow < lowDistanceToLow) highStroke else lowStroke
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
                wallColorInt = if (lowDistLeft < highDistLeft) lowStroke else highStroke
            }
            if (lowDistLeft < minDistance) {
                val tempAlpha = getAlpha(lowDistLeft)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistLeft < lowDistLeft) highStroke else lowStroke
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
                wallColorInt = if (lowDistRight < highDistRight) lowStroke else highStroke
            }
            if (lowDistRight < minDistance) {
                val tempAlpha = getAlpha(lowDistRight)
                if (tempAlpha > particleAlpha) {
                    particleAlpha = tempAlpha
                    wallColorInt = if (highDistRight < lowDistRight) highStroke else lowStroke
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

    // Aim arrow bakes its colours once per fling and holds them for the whole drag (so it never
    // strobes mid-aim). Under a main-rainbow override the base and fill are a complementary pair.
    private var highArrowHue: Float? = null
    private var lowArrowHue: Float? = null

    fun DrawScope.drawAimArrows() {
        aimArrowFrame++
        if (Settings.lowPlayerArrow) drawAimArrow(Logic.lowPlayer, isHigh = false)
        if (Settings.highPlayerArrow) drawAimArrow(Logic.highPlayer, isHigh = true)
    }

    private fun DrawScope.drawAimArrow(player: Player, isHigh: Boolean) {
        if (!player.isFlingHeld) {
            if (isHigh) highArrowHue = null else lowArrowHue = null
            return
        }

        val tailX = player.flingCurrent.x
        val tailY = player.flingCurrent.y
        val headX = player.flingStart.x
        val headY = player.flingStart.y
        val dx = headX - tailX
        val dy = headY - tailY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < Settings.screenRatio * 0.3f) return

        val themeColor: Color
        val chargeColor: Color
        if (RainbowOverride.mainActive(isHigh)) {
            // Bake at fling: base = baked hue, fill = inverted hue → two high-contrast colours held
            // for the whole drag.
            val h = (if (isHigh) highArrowHue else lowArrowHue)
                ?: RainbowOverride.hue(isHigh, player.puck.renderer.frame).also {
                    if (isHigh) highArrowHue = it else lowArrowHue = it
                }
            themeColor = RainbowOverride.secondaryColor(h)
            chargeColor = RainbowOverride.secondaryColor(RainbowOverride.invertedHue(h))
        } else {
            themeColor = if (isHigh) PaintBucket.highBallStroke else PaintBucket.lowBallStroke
            chargeColor = if (isHigh) PaintBucket.highShieldSecondary else PaintBucket.lowShieldSecondary
        }

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

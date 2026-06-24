package utility

import enums.ChargeMeterStyle
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.RainbowOverride
import kotlin.math.ceil
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
import androidx.compose.ui.text.TextLayoutResult
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

        // Drop cached HUD text layouts so a re-init (screen resize / new game) re-measures with the
        // current dimensions instead of reusing layouts sized for the previous screen.
        scoreStyle = null; scoreStyleKey = Float.NaN
        highScoreLayout = null; highScoreLayoutKey = null
        lowScoreLayout = null; lowScoreLayoutKey = null
        timerStyle = null; timerStyleKey = Float.NaN
        timerLayout = null; timerLayoutKey = Int.MIN_VALUE
        timerText = ""; timerTextKey = Int.MIN_VALUE
    }

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
        // Dev profiler: measure produced-frame cadence at the truest point (top of the draw call).
        // No-op when FrameProfiler.enabled is false. Settings.refreshRate is the frame INTERVAL in ms
        // (default 16 ≈ 60fps) — pass it directly as targetMs, do NOT convert to Hz.
        FrameProfiler.onFrame(nowNanos(), Settings.refreshRate.toFloat())

        FrameProfiler.begin(FrameProfiler.S_ARENA)
        drawArenaBackground()
        FrameProfiler.end(FrameProfiler.S_ARENA)

        if (!Logic.isInitialized) return

        FrameProfiler.begin(FrameProfiler.S_ARENA)
        drawChargeFill()
        FrameProfiler.end(FrameProfiler.S_ARENA)

        if (!Settings.isDemoMode) {
            FrameProfiler.begin(FrameProfiler.S_PARTICLES)
            with(Effects) { drawEffects() }
            FrameProfiler.end(FrameProfiler.S_PARTICLES)
        }

        // Skin/tail/paddle sections are timed inside PuckRenderer.draw() (both pucks fold together).
        drawPlayersCompose()

        if (!Settings.isDemoMode) {
            FrameProfiler.begin(FrameProfiler.S_PARTICLES)
            with(Effects) { drawPriorityEffects() }
            FrameProfiler.end(FrameProfiler.S_PARTICLES)
        }

        FrameProfiler.begin(FrameProfiler.S_ARENA)
        drawWalls()
        FrameProfiler.end(FrameProfiler.S_ARENA)

        FrameProfiler.begin(FrameProfiler.S_HUD)
        if (!Settings.isDemoMode) drawTimer()
        drawAimArrows()
        FrameProfiler.end(FrameProfiler.S_HUD)

        FrameProfiler.begin(FrameProfiler.S_ARENA)
        drawArenaForeground()
        FrameProfiler.end(FrameProfiler.S_ARENA)

        FrameProfiler.begin(FrameProfiler.S_HUD)
        drawBallPopups()
        drawScoreFlash()
        if (!Settings.isDemoMode) drawScores(Logic.highPlayer, Logic.lowPlayer)
        FrameProfiler.end(FrameProfiler.S_HUD)

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
    // Arena foreground (spiky goals)
    //
    // Each goal is a filled zone whose arena-facing edge is flat ("safe") or grows a row of
    // sawtooth spikes ("spiky") as Settings.spikeProgress ramps 0→1. The fill colour still swaps
    // primary/secondary via goalColor() as the goals arm, so hue and shape change together.
    // -------------------------------------------------------------------------

    // Sizing is in screenRatio units (never pixels). Tune these to match Goal Shape.png.
    private const val SPIKE_TOOTH_WIDTH_RATIO = 1.2f // tooth base width ≈ screenRatio * this
    private const val SPIKE_HEIGHT_RATIO = 1.0f      // full-extension spike height ≈ screenRatio * this

    // One reusable Path per goal — rewound/refilled only when its baked progress changes, so idle
    // frames (fully safe or fully spiky, held steady) reuse the cached path and allocate nothing.
    private val highGoalSpikePath = Path()
    private val lowGoalSpikePath = Path()
    private var highSpikeBuiltEase = Float.NaN
    private var lowSpikeBuiltEase = Float.NaN

    // Resolved layout, rebuilt only when screen dimensions change (constant during normal play).
    private var spikeLayoutWidth = -1f
    private var spikeLayoutGoalDepth = -1f
    private var spikeCount = 0
    private var spikeToothWidth = 0f
    private var spikeFullHeight = 0f

    private fun ensureSpikeLayout() {
        val width = Settings.screenWidth
        val goalDepth = Settings.topGoalBottom
        if (width == spikeLayoutWidth && goalDepth == spikeLayoutGoalDepth) return
        spikeLayoutWidth = width
        spikeLayoutGoalDepth = goalDepth
        val toothWidth = Settings.screenRatio * SPIKE_TOOTH_WIDTH_RATIO
        spikeCount = ceil(width / toothWidth).toInt().coerceAtLeast(1)
        // Even division so the teeth tile exactly across the full width (last valley lands on width).
        spikeToothWidth = width / spikeCount
        spikeFullHeight = Settings.screenRatio * SPIKE_HEIGHT_RATIO
        // Force both cached paths to rebuild against the new layout.
        highSpikeBuiltEase = Float.NaN
        lowSpikeBuiltEase = Float.NaN
    }

    // Quadratic ease-out: linear progress in, a little ease-in at the end of the extension.
    private fun easedSpike(p: Float): Float = 1f - (1f - p) * (1f - p)

    fun DrawScope.drawArenaForeground() {
        drawSpikyGoal(isHigh = true)
        drawSpikyGoal(isHigh = false)
        drawGoalMenuHints()
    }

    private fun DrawScope.drawSpikyGoal(isHigh: Boolean) {
        ensureSpikeLayout()
        val ease = easedSpike(Settings.spikeProgress)
        val path = if (isHigh) highGoalSpikePath else lowGoalSpikePath
        val built = if (isHigh) highSpikeBuiltEase else lowSpikeBuiltEase
        if (ease != built) {
            buildSpikePath(path, isHigh, ease)
            if (isHigh) highSpikeBuiltEase = ease else lowSpikeBuiltEase = ease
        }
        drawPath(path, color = goalColor(isHigh, Settings.canScore))
    }

    // Traces the goal outline with a sawtooth arena-facing edge. Valleys sit on the goal baseline;
    // peaks (tips) reach [ease] of the full spike height into the play area. The low goal mirrors the
    // high goal's Y math — no canvas mirror needed (spikes are axis-aligned, computed in screen space).
    private fun buildSpikePath(path: Path, isHigh: Boolean, ease: Float) {
        val width = Settings.screenWidth
        val tooth = spikeToothWidth
        val height = spikeFullHeight * ease
        path.rewind()
        if (isHigh) {
            val baseline = Settings.topGoalBottom
            val tipY = baseline + height
            path.moveTo(0f, 0f)
            path.lineTo(width, 0f)
            path.lineTo(width, baseline)              // down the right edge to the baseline
            var k = spikeCount                        // walk the sawtooth right→left
            while (k > 0) {
                path.lineTo((k - 0.5f) * tooth, tipY) // peak (tip) at tooth centre
                path.lineTo((k - 1) * tooth, baseline) // valley at tooth boundary
                k--
            }
        } else {
            val baseline = Settings.bottomGoalTop
            val tipY = baseline - height
            val bottom = Settings.screenHeight
            path.moveTo(0f, bottom)
            path.lineTo(width, bottom)
            path.lineTo(width, baseline)              // up the right edge to the baseline
            var k = spikeCount
            while (k > 0) {
                path.lineTo((k - 0.5f) * tooth, tipY)
                path.lineTo((k - 1) * tooth, baseline)
                k--
            }
        }
        path.close()
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

    // Scratch outputs for resolveChargeColor — avoids allocating a Pair every frame while charging.
    private var chargeColorRaw = 0
    private var chargeColorAlpha = 0f

    /**
     * Charge-meter colour. Under a main-rainbow override the customisable phases strobe (Building →
     * primary, SweetSpot → secondary); Draining stays the grey inert colour. [frozenHue] forces a
     * fixed hue (FullScreen bake-at-fill); null uses the player's live frame (SideBar live strobe).
     *
     * Returns true when a colour was resolved; the result is written to [chargeColorRaw] (ARGB int)
     * and [chargeColorAlpha] (0..1). Returns false (and leaves the scratch fields untouched) when
     * there is nothing to draw.
     */
    private fun DrawScope.resolveChargeColor(player: Player, isHigh: Boolean, frozenHue: Float?): Boolean {
        val effect = player.puck.renderer.effect ?: return false
        val ph = effect.phase
        if (ph == ChargePhase.Idle || ph == ChargePhase.Inert) return false
        val ratio = effect.chargeFillRatio
        if (ratio <= 0f) return false
        val theme = effect.theme
        val mainRainbow = RainbowOverride.mainActive(isHigh)
        val hue = frozenHue ?: RainbowOverride.hue(isHigh, player.puck.renderer.frame)
        chargeColorRaw = when (ph) {
            ChargePhase.Building -> if (mainRainbow) RainbowOverride.primaryColor(hue).toArgb() else theme.main.primary
            ChargePhase.Draining -> theme.inert.secondary
            ChargePhase.SweetSpot -> if (mainRainbow) RainbowOverride.secondaryColor(hue).toArgb() else theme.shield.secondary
            else -> return false
        }
        chargeColorAlpha = when (ph) {
            ChargePhase.Building, ChargePhase.Draining -> 255f
            ChargePhase.SweetSpot -> (0.7f + 0.3f * sin(chargeFillFrame * 0.35f)).coerceIn(0f, 1f)
            else -> return false
        }
        return true
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
        if (!resolveChargeColor(player, isHigh, frozen)) return
        val color = Color(chargeColorRaw).copy(alpha = chargeColorAlpha)
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
        if (!resolveChargeColor(player, isHigh, frozenHue = null)) return
        val color = Color(chargeColorRaw).copy(alpha = chargeColorAlpha)
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

    // ---- HUD text caches ----------------------------------------------------
    // The score/timer text changes a handful of times per match but the draw loop runs every frame.
    // Rebuilding the TextStyle and re-measuring on every frame allocated a TextStyle, an
    // AnnotatedString, a TextLayoutInput and a TextLayoutResult per draw — a steady per-frame heap
    // churn that fed the GC. These caches rebuild only when the keying state actually changes
    // (font size for the style, the text string / seconds value for the measured layout).
    private var scoreStyle: TextStyle? = null
    private var scoreStyleKey = Float.NaN
    private var highScoreLayout: TextLayoutResult? = null
    private var highScoreLayoutKey: String? = null
    private var lowScoreLayout: TextLayoutResult? = null
    private var lowScoreLayoutKey: String? = null

    private var timerStyle: TextStyle? = null
    private var timerStyleKey = Float.NaN
    private var timerLayout: TextLayoutResult? = null
    private var timerLayoutKey = Int.MIN_VALUE
    private var timerText = ""
    private var timerTextKey = Int.MIN_VALUE

    fun DrawScope.drawScores(highPlayer: Player, lowPlayer: Player) {
        val tm = textMeasurer ?: return
        val density = drawContext.density.density
        val fontSizeSp = (PaintBucket.scoreFontSize / density)
        if (scoreStyle == null || scoreStyleKey != fontSizeSp) {
            scoreStyle = TextStyle(
                fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
                color = PaintBucket.black
            )
            scoreStyleKey = fontSizeSp
            highScoreLayout = null   // style changed → measured layouts are stale
            lowScoreLayout = null
        }
        val style = scoreStyle!!

        val highText = highPlayer.cachedScoreText
        if (highScoreLayout == null || highScoreLayoutKey != highText) {
            highScoreLayout = tm.measure(highText, style)
            highScoreLayoutKey = highText
        }
        val highResult = highScoreLayout!!

        val lowText = lowPlayer.cachedScoreText
        if (lowScoreLayout == null || lowScoreLayoutKey != lowText) {
            lowScoreLayout = tm.measure(lowText, style)
            lowScoreLayoutKey = lowText
        }
        val lowResult = lowScoreLayout!!

        val xMargin = Settings.screenRatio * 3f
        val scoreY = Settings.screenHeight

        val highX = xMargin + Settings.scoreOffsetHigh
        val midX = Settings.screenWidth / 2f
        val midY = Settings.screenHeight / 2f

        withTransform({ scale(-1f, -1f, pivot = Offset(midX, midY)) }) {
            drawText(highResult, color=PaintBucket.white, topLeft = Offset(highX, scoreY - highResult.size.height))
        }

        val lowX = xMargin + Settings.scoreOffsetLow
        drawText(lowResult, color=PaintBucket.white, topLeft = Offset(lowX, scoreY - lowResult.size.height))

    }

    fun DrawScope.drawTimer() {
        if (Settings.timeLimitMinutes == 0) return
        if (!Logic.timerStarted || Logic.timerHidden) return
        val tm = textMeasurer ?: return

        val density = drawContext.density.density
        val fontSizePx = Settings.screenRatio * 1.2f
        val fontSizeSp = fontSizePx / density
        if (timerStyle == null || timerStyleKey != fontSizeSp) {
            timerStyle = TextStyle(
                fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
                color = PaintBucket.timerColor
            )
            timerStyleKey = fontSizeSp
            timerLayout = null   // style changed → measured layout is stale
        }
        val style = timerStyle!!

        val seconds = Logic.timerSecondsRemaining
        if (timerTextKey != seconds) {
            timerText = seconds.toString()
            timerTextKey = seconds
        }
        if (timerLayout == null || timerLayoutKey != seconds) {
            timerLayout = tm.measure(timerText, style)
            timerLayoutKey = seconds
        }
        val result = timerLayout!!

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

package utility

import enums.ChargeMeterStyle
import enums.ScorePhase
import gameobjects.Player
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.RainbowOverride
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
    // [openness] 0 = closed/safe edge (secondary colour), 1 = fully armed/open (primary). The colour
    // is lerped across this so it fades in lockstep with the spike extension, instead of snapping the
    // instant the binary canScore flag flips (which read as a hard "snap to saturated" on goal close).
    private fun goalColor(isHigh: Boolean, openness: Float): Color {
        val o = openness.coerceIn(0f, 1f)
        if (RainbowOverride.shieldActive(isHigh)) {
            val hue = RainbowOverride.hue(isHigh, playerFrame(isHigh))
            return lerp(RainbowOverride.secondaryColor(hue), RainbowOverride.primaryColor(hue), o)
        }
        val closed = if (isHigh) PaintBucket.highShieldSecondary else PaintBucket.lowShieldSecondary
        val open = if (isHigh) PaintBucket.highShieldPrimary else PaintBucket.lowShieldPrimary
        return lerp(closed, open, o)
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

        // Score dial — normal pass: below the persistent effects (drawn next) and the pucks, so both
        // occlude it. Skipped here while it is "lifted" above the score cinematic (drawn after it
        // instead). The time dial draws in the same layer right after it (the two never intersect).
        FrameProfiler.begin(FrameProfiler.S_HUD)
        with(ScoreDial) { drawScoreDial(lifted = false) }
        if (!Settings.isDemoMode) with(TimeDial) { drawTimeDial() }
        FrameProfiler.end(FrameProfiler.S_HUD)

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
        drawAimArrows()
        FrameProfiler.end(FrameProfiler.S_HUD)

        FrameProfiler.begin(FrameProfiler.S_ARENA)
        drawArenaForeground()
        FrameProfiler.end(FrameProfiler.S_ARENA)

        FrameProfiler.begin(FrameProfiler.S_HUD)
        drawBallPopups()
        drawScoreCinematic()
        // Score dial — lifted pass: above the cinematic's dim wash so the spin stays visible during a
        // score event. The tossed paddle and its landing burst draw inside this dial pass now (between
        // the dial face and the numerals — see ScoreDial.drawScoreDial), so each flies visibly into its
        // number without sitting on top of the digit. Only one of the two passes renders each frame.
        with(ScoreDial) { drawScoreDial(lifted = true) }
        // Pause menu draws top-most when active (the dial passes above hand off to it).
        with(ScoreDial) { drawPauseMenu() }
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
    private const val SPIKE_HEIGHT_RATIO = .75f      // full-extension spike height ≈ screenRatio * this

    // One reusable Path per goal — rewound/refilled only when its baked progress changes, so idle
    // frames (fully safe or fully spiky, held steady) reuse the cached path and allocate nothing.
    private val highGoalSpikePath = Path()
    private val lowGoalSpikePath = Path()
    // Per-goal built-state record (Plan 4): the path now depends on ease AND the shield-flatten dent
    // (centre X + strength), so the cache key is all three. Rebuild only when any changed; idle frames
    // (no shielded ball near, dent strength 0 with a canonical X) reuse the cached path → no work.
    private var highSpikeBuiltEase = Float.NaN
    private var lowSpikeBuiltEase = Float.NaN
    private var highSpikeBuiltFlattenX = Float.NaN
    private var lowSpikeBuiltFlattenX = Float.NaN
    private var highSpikeBuiltStrength = Float.NaN
    private var lowSpikeBuiltStrength = Float.NaN

    // Resolved layout, rebuilt only when screen dimensions change (constant during normal play).
    private var spikeLayoutWidth = -1f
    private var spikeLayoutGoalDepth = -1f
    private var spikeCount = 0
    private var spikeToothWidth = 0f
    private var spikeFullHeight = 0f
    private var spikeFlattenRadius = 0f

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
        spikeFlattenRadius = Settings.screenRatio * Settings.SHIELD_FLATTEN_RADIUS_RATIO
        // Force both cached paths to rebuild against the new layout.
        highSpikeBuiltEase = Float.NaN
        lowSpikeBuiltEase = Float.NaN
        highSpikeBuiltFlattenX = Float.NaN
        lowSpikeBuiltFlattenX = Float.NaN
        highSpikeBuiltStrength = Float.NaN
        lowSpikeBuiltStrength = Float.NaN
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
        // Shield-flatten dent (Plan 4). When no shielded ball qualifies, strength is 0 and the centre
        // X is irrelevant — canonicalise it to 0f so the cache key stays stable (NaN never compares
        // equal, which would force a rebuild every idle frame).
        val strength = if (isHigh) Settings.highGoalFlattenStrength else Settings.lowGoalFlattenStrength
        val flattenX = if (strength > 0f) {
            if (isHigh) Settings.highGoalFlattenX else Settings.lowGoalFlattenX
        } else 0f
        val path = if (isHigh) highGoalSpikePath else lowGoalSpikePath
        val builtEase = if (isHigh) highSpikeBuiltEase else lowSpikeBuiltEase
        val builtX = if (isHigh) highSpikeBuiltFlattenX else lowSpikeBuiltFlattenX
        val builtStrength = if (isHigh) highSpikeBuiltStrength else lowSpikeBuiltStrength
        if (ease != builtEase || flattenX != builtX || strength != builtStrength) {
            buildSpikePath(path, isHigh, ease, flattenX, strength)
            if (isHigh) {
                highSpikeBuiltEase = ease; highSpikeBuiltFlattenX = flattenX; highSpikeBuiltStrength = strength
            } else {
                lowSpikeBuiltEase = ease; lowSpikeBuiltFlattenX = flattenX; lowSpikeBuiltStrength = strength
            }
        }
        // Colour fades with the spike extension (same eased progress as the shape), so it eases open
        // and closed in step with the teeth rather than snapping when canScore toggles.
        drawPath(path, color = goalColor(isHigh, ease))
    }

    // Local tooth-height multiplier for the shield-flatten dent (Plan 4): 1 everywhere except within
    // FLATTEN_RADIUS of the dent centre, where a smooth (1 - t²) shoulder presses the teeth down by
    // up to [strength]. Returns 1 when no dent is active.
    private fun spikeLocalFactor(toothCenterX: Float, flattenX: Float, strength: Float): Float {
        if (strength <= 0f || spikeFlattenRadius <= 0f) return 1f
        val t = abs(toothCenterX - flattenX) / spikeFlattenRadius
        if (t >= 1f) return 1f
        val falloff = 1f - t * t
        return (1f - strength * falloff).coerceIn(0f, 1f)
    }

    // Traces the goal outline with a sawtooth arena-facing edge. Valleys sit on the goal baseline;
    // peaks (tips) reach [ease] of the full spike height into the play area. The low goal mirrors the
    // high goal's Y math — no canvas mirror needed (spikes are axis-aligned, computed in screen space).
    private fun buildSpikePath(path: Path, isHigh: Boolean, ease: Float, flattenX: Float, strength: Float) {
        val width = Settings.screenWidth
        val tooth = spikeToothWidth
        val height = spikeFullHeight * ease
        path.rewind()
        if (isHigh) {
            val baseline = Settings.topGoalBottom
            path.moveTo(0f, 0f)
            path.lineTo(width, 0f)
            path.lineTo(width, baseline)              // down the right edge to the baseline
            var k = spikeCount                        // walk the sawtooth right→left
            while (k > 0) {
                val cx = (k - 0.5f) * tooth           // tooth centre
                val tipY = baseline + height * spikeLocalFactor(cx, flattenX, strength)
                path.lineTo(cx, tipY)                 // peak (tip) at tooth centre (dented by shield)
                path.lineTo((k - 1) * tooth, baseline) // valley at tooth boundary
                k--
            }
        } else {
            val baseline = Settings.bottomGoalTop
            val bottom = Settings.screenHeight
            path.moveTo(0f, bottom)
            path.lineTo(width, bottom)
            path.lineTo(width, baseline)              // up the right edge to the baseline
            var k = spikeCount
            while (k > 0) {
                val cx = (k - 0.5f) * tooth
                val tipY = baseline - height * spikeLocalFactor(cx, flattenX, strength)
                path.lineTo(cx, tipY)
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

    // The match countdown is now drawn by utility/TimeDial (a mirror of the score dial on the
    // opposite edge); the old in-goal numeric readout (drawTimer) was retired with the Score Redesign.

    // Reused even-odd Path for the score-cinematic overlay (full-screen rect minus a circular
    // window). Rewound/refilled each frame — no offscreen layer, no per-frame Path allocation.
    private val scoreCinematicPath = Path()

    // Plan 5: cached Stroke for the punch-through ring. Rebuilt only when the width changes (screen
    // resize), mirroring ClassicSkin.strokeFor — so drawScoreCinematic stays allocation-free per frame.
    private var scoreWindowStroke: Stroke? = null
    private var scoreWindowStrokeWidth = -1f
    private fun scoreWindowStrokeFor(width: Float): Stroke {
        if (scoreWindowStroke == null || scoreWindowStrokeWidth != width) {
            scoreWindowStroke = Stroke(width = width)
            scoreWindowStrokeWidth = width
        }
        return scoreWindowStroke!!
    }

    // The freeze-frame dim wash with a circular window onto the pierced ball. Driven by Logic's
    // score-cinematic phase/ticker; a cheap early-return when no score cinematic is active. Drawn as
    // a single even-odd Path so the rect-minus-circle leaves a clean transparent window onto the
    // frozen scene (the pierced ball shows through; the rest dims under the wash).
    fun DrawScope.drawScoreCinematic() {
        if (!Logic.scoreCinematicActive) return
        val ratio = Logic.scoreCinematicTicker.ratio.coerceIn(0f, 1f)
        val minRadius = Settings.ballRadius * Settings.SCORE_WINDOW_MIN_RADIUS_BALLS
        val maxRadius = Logic.scoreWindowMaxRadius
        val (radius, alpha) = when (Logic.scorePhase) {
            ScorePhase.Shrink -> {
                val eased = 1f - (1f - ratio) * (1f - ratio)         // quad ease-out settle
                Pair(maxRadius + (minRadius - maxRadius) * eased, Settings.SCORE_OVERLAY_ALPHA * ratio)
            }
            ScorePhase.Hold -> Pair(minRadius, Settings.SCORE_OVERLAY_ALPHA)
            ScorePhase.Expand -> {
                val eased = ratio * ratio                            // ease-in as it swallows the screen
                Pair(minRadius + (maxRadius - minRadius) * eased, Settings.SCORE_OVERLAY_ALPHA)
            }
        }
        val cx = Logic.pierceX
        val cy = Logic.pierceY
        val path = scoreCinematicPath
        path.rewind()
        path.addRect(Rect(0f, 0f, Settings.screenWidth, Settings.screenHeight))
        path.addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        path.fillType = PathFillType.EvenOdd
        drawPath(path, color = Color(Logic.scoreOverlayColor).copy(alpha = alpha))

        // Plan 5: crisp ring framing the window (the winner's baked stroke colour, resolved once in
        // Logic.startScoreCinematic). Fades in with the wash during Shrink, then holds full opacity
        // through Hold/Expand and rides the expanding window off-screen. Cached Stroke; no per-frame alloc.
        val ringAlpha = if (Logic.scorePhase == ScorePhase.Shrink) ratio else 1f
        drawCircle(
            color = Color(Logic.scoreOutlineColor).copy(alpha = ringAlpha),
            radius = radius,
            center = Offset(cx, cy),
            style = scoreWindowStrokeFor(Settings.screenRatio * Settings.SCORE_OUTLINE_WIDTH_RATIO)
        )
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

package utility

import enums.*
import gameobjects.Player
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.ScoredPaddle
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import shapes.BallSelectionPopup
import shapes.FlashTuning
import gameobjects.BotBrain
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.time.TimeMark

object Logic {

    lateinit var highPlayer: Player
    lateinit var lowPlayer: Player
    var botBrain: BotBrain? = null

    lateinit var victoryTicker: Ticker

    val highBallPopup = BallSelectionPopup(isHigh = true)
    val lowBallPopup = BallSelectionPopup(isHigh = false)

    var isInitialized: Boolean = false

    // Plan 2 (pause menu): while true the game-loop tick skips the whole simulation (physics, charge,
    // bot, score checks, timer) but the frame still draws, so the dial's expand/collapse animation
    // keeps moving. Set true the instant the menu open animation starts; cleared only once the close
    // animation completes (see ScoreDial). Not a GameState — pause can occur from Play.
    var paused: Boolean = false

    var tempGameState = GameState.BallSelection
    var leaving = false
    var canCollide = true

    // ---- Score cinematic state (the Scored-state freeze + dim wash with a circular window). ----
    // Driven by scored(); read by Drawing.drawScoreCinematic. scoringPlayer is the winner (its colour
    // washes the screen); piercedPlayer is the loser the window frames (also used by Plan 3).
    var scoreCinematicActive = false
    var scorePhase = ScorePhase.Shrink
    val scoreCinematicTicker = Ticker(Settings.SCORE_SHRINK_FRAMES, true)
    var pierceX = 0f
    var pierceY = 0f
    var scoreOverlayColor = 0
    // Plan 5: ring colour around the punch-through window. Resolved once at cinematic start (the
    // winner's baked stroke colour) so drawScoreCinematic never allocates a ColorTheme per frame.
    var scoreOutlineColor = 0
    var scoreWindowMaxRadius = 0f
    var scoringPlayer: Player? = null
    var piercedPlayer: Player? = null

    // Plan 3 (paddle toss): the tossed stand-in paddles, keyed by the FLYING paddle's side. A single
    // score flings the loser's; Result.Both flings both (each player collects their own). Reused, not
    // reallocated per score. Drawn above the score overlay by Drawing.
    val tossHigh = ScoredPaddle()
    val tossLow = ScoredPaddle()

    // Plan 3 (pop & teleport): the pierced ball pops/vanishes then re-materializes at its start.
    // [bothScored] (Result.Both) falls back to both balls lerping home — no pop — to avoid an
    // ambiguous double-pop. [piercedPopTriggered] flips true at the Hold→Expand boundary when the
    // loser enters its disappear; used by updateSpikes to keep the spikes sharp until it has vanished.
    var bothScored = false
    private var piercedPopTriggered = false

    // Plan 7: the per-skin score burst (onUsedToScore) now fires when the ball POPS (Hold→Expand),
    // not at score time, so it reads as the ball exploding as it vanishes. checkScored swaps both
    // pucks' colours (#9/#10), so we capture each side's burst colour + which goal PRE-swap and
    // replay it at pop time. Keyed by side so Result.Both can burst both balls with their own colour.
    private var highBurstColor = 0
    private var lowBurstColor = 0
    private var highBurstHighGoal = false
    private var lowBurstHighGoal = false
    private var bothBurstsFired = false
    private val _burstPoint = Point()

    /** Called instead of doOnSizeChange when running under Compose (no GameView). */
    var composeReinitCallback: (() -> Unit)? = null

    private var highPopupDragging = false
    private var lowPopupDragging  = false

    var winnerSoundHasBeenPlayed = false
    private var victoryCelebrationFrame = 0

    private var timerMark: TimeMark? = null
    var timerStarted = false
    var timerExpired = false
    var timerHidden = false
    // Close-game overtime: set when the clock hits 0 while the score is within one (or tied) and the
    // point is still in play. The TimeDial then shows a large centred "0" instead of vanishing, until
    // the point resolves. See TimeDial.drawTimeDial.
    var timerShowFinalZero = false
    var timerSecondsRemaining = 0

    private var highInDanger = false
    private var lowInDanger  = false

    // Per-frame pressed-pointer counts per screen half. Updated by GameScreen each
    // pointer event; consumed by Drawing.drawTouchHighlights to flash the OTHER
    // side's highlight when one side has multiple fingers down. Read-only for
    // game logic — does not affect input routing, fling, or charge behavior.
    var highSideHasMultiTouch: Boolean = false
    var lowSideHasMultiTouch: Boolean = false

    // True while a player's owned pointer is currently on the OTHER side of the
    // centerline (drag-across-center). Used to flash that player's HOME side as a
    // "bring your finger back" warning. Pure visual signal — does not change
    // existing drag-across-line behavior.
    var highPlayerCrossedCenter: Boolean = false
    var lowPlayerCrossedCenter: Boolean = false

    var highStartX = 0f
    var lowStartX = 0f

    var collisionBonus = 0f

    enum class Result {
        High,
        Low,
        Both,
        Neither
    }

    fun initializeSettings(width: Int, height: Int) {
        Settings.initializeForScreen(width, height)
        highStartX = Settings.screenWidth / 4f
        lowStartX = Settings.screenWidth * (3 / 4f)
    }

    fun initialize() {
        leaving = false
        paused = false
        tossHigh.reset()
        tossLow.reset()
        Settings.gameState = GameState.BallSelection
        Settings.gameOver = false
        highPopupDragging = false
        lowPopupDragging  = false
        Drawing.resetTipIndices()
        applyBallStyles()
        botBrain = if (Settings.isSinglePlayer) BotBrain(highPlayer, lowPlayer, Settings.botConfig) else null
        registerPhaseCallbacks()

        highPlayer.resetLocation = Point(highStartX, Settings.middleY)
        lowPlayer.resetLocation = Point(lowStartX, Settings.middleY)

        victoryTicker = Ticker(Settings.victoryThreshold)

        timerMark = null
        timerStarted = false
        timerExpired = false
        timerHidden = false
        timerShowFinalZero = false
        timerSecondsRemaining = Settings.timeLimitMinutes * 60
        TimeDial.syncFromTimer()

        highBallPopup.open()
        lowBallPopup.open()

        collisionBonus = 10 + 10f * Settings.balanceRatio

        isInitialized = true
        ScoreDial.syncFromPlayers()
    }

    fun reset() {
        tempGameState = GameState.Play
    }

    fun updateTimer() {
        if (paused) return   // Plan 2: the match timer must not advance while the pause menu is open.
        val mark = timerMark ?: return
        if (timerExpired) return
        val limitMs = Settings.timeLimitMinutes.toLong() * 60_000L
        val remainingMs = (limitMs - mark.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
        timerSecondsRemaining = (remainingMs / 1000L).toInt()
        // The displayed countdown reaches 0 up to a second before the real expiry (integer seconds). In
        // a close game, start the big-0 slide-in the instant it hits 0 — sucking the last numeral out
        // with nothing ratcheting in behind it — rather than spinning a small 0 in only to replace it.
        val closeGame = (highPlayer.score - lowPlayer.score).absoluteValue <= 1
        if (timerSecondsRemaining <= 0 && Settings.gameState == GameState.Play && closeGame) {
            if (!timerShowFinalZero) {
                timerShowFinalZero = true
                TimeDial.beginFinalZero()
            }
        } else if (!timerShowFinalZero) {
            TimeDial.update(timerSecondsRemaining)   // spins the dial when it crosses a display step
        }
        if (remainingMs == 0L) {
            timerExpired = true
            val diff = (highPlayer.score - lowPlayer.score).absoluteValue
            when {
                // A winner is already decided — end the match immediately and hide the dial.
                Settings.gameState == GameState.Play && diff > 1 -> {
                    timerHidden = true
                    Settings.gameState = GameState.Scored
                }
                // Close game (within one / tied): let the current point play out, and keep the dial
                // visible showing a large centred "0" rather than vanishing.
                Settings.gameState == GameState.Play -> {
                    timerHidden = false
                    timerShowFinalZero = true
                }
                // Expired mid-cinematic: just hide; endScoreInterlude resolves the match.
                else -> timerHidden = true
            }
        }
    }

    fun applyBallStyles() {
        val highCustomIdx = Settings.highCustomBallIndex
        val lowCustomIdx = Settings.lowCustomBallIndex

        if (Settings.highBallType == BallType.Random) {
            if (highCustomIdx != null) {
                Settings.highRandomRoll = null
            } else {
                Settings.highRandomRoll = BallStyleFactory.rollRandom()
            }
        }
        if (Settings.lowBallType == BallType.Random) {
            if (lowCustomIdx != null) {
                Settings.lowRandomRoll = null
            } else {
                Settings.lowRandomRoll = BallStyleFactory.rollRandom()
            }
        }

        val highRenderer = if (Settings.highBallType == BallType.Random && highCustomIdx != null) {
            val config = Storage.loadCustomBall(highCustomIdx)
            if (config != null) BallStyleFactory.buildCustomRenderer(config, ColorTheme.getTheme(true))
            else BallStyleFactory.buildRenderer(Settings.highBallType, ColorTheme.getTheme(true), BallStyleFactory.rollRandom())
        } else {
            BallStyleFactory.buildRenderer(Settings.highBallType, ColorTheme.getTheme(true), Settings.highRandomRoll)
        }

        val lowRenderer = if (Settings.lowBallType == BallType.Random && lowCustomIdx != null) {
            val config = Storage.loadCustomBall(lowCustomIdx)
            if (config != null) BallStyleFactory.buildCustomRenderer(config, ColorTheme.getTheme(false))
            else BallStyleFactory.buildRenderer(Settings.lowBallType, ColorTheme.getTheme(false), BallStyleFactory.rollRandom())
        } else {
            BallStyleFactory.buildRenderer(Settings.lowBallType, ColorTheme.getTheme(false), Settings.lowRandomRoll)
        }

        // Rainbow colour overrides (see RainbowOverride): copy the loaded Settings flags onto each
        // puck renderer so its main/shield colours strobe in play.
        highRenderer.rainbowMain = Settings.highPlayerRainbow
        highRenderer.rainbowShield = Settings.highPlayerRainbowShield
        lowRenderer.rainbowMain = Settings.lowPlayerRainbow
        lowRenderer.rainbowShield = Settings.lowPlayerRainbowShield

        highPlayer = Player(
            Puck(Settings.ballRadius, highStartX, Settings.middleY, highRenderer),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight / 5, PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb()),
            true
        )
        lowPlayer = Player(
            Puck(Settings.ballRadius, lowStartX, Settings.middleY, lowRenderer),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight - Settings.screenHeight / 5, PaintBucket.lowBallFill.toArgb(), PaintBucket.lowBallStroke.toArgb()),
            false
        )
        botBrain?.updateReferences(highPlayer, lowPlayer)
        registerPhaseCallbacks()
    }

    fun checkBallSelectionEnd() {
        if (highPlayer.charge > Settings.chargeStart && lowPlayer.charge > Settings.chargeStart) {
            highBallPopup.isOpen = false
            lowBallPopup.isOpen = false
            highPopupDragging = false
            lowPopupDragging  = false
            canCollide = true
            Settings.gameState = GameState.Play
            Sounds.playGameStart()
            if (Settings.timeLimitMinutes > 0 && !timerStarted) {
                timerStarted = true
                timerMark = TimeSource.Monotonic.markNow()
            }
        }
    }

    fun registerPhaseCallbacks() {
        listOf(highPlayer, lowPlayer).forEach { player ->
            val effect = player.puck.renderer.effect ?: return@forEach
            effect.unregisterAllPhaseCallbacks()
            val skin = player.puck.renderer.skin
            val tail = player.puck.renderer.tail
            effect.registerPhaseCallback { phase ->
                skin.onPhaseChanged(phase)
                tail.onPhaseChanged(phase)
                if (phase == ChargePhase.Inert) player.fatigueInertLocked = true
                if (phase == ChargePhase.Idle) player.fatigueInertLocked = false
            }
        }
    }

    fun unregisterPhaseCallbacks() {
        highPlayer.puck.renderer.effect?.unregisterAllPhaseCallbacks()
        lowPlayer.puck.renderer.effect?.unregisterAllPhaseCallbacks()
    }

    fun cancelChargesOnRelease() {
        cancelChargeOnRelease(highPlayer)
        cancelChargeOnRelease(lowPlayer)
    }

    private fun cancelChargeOnRelease(player: Player) {
        if (player.shouldReleaseCharge) {
            player.shouldReleaseCharge = false
            player.clearCharge()
            player.flingReleaseDir = null
            player.flingReleaseBasePower = 0f
        }
    }

    // Plan 6 — Retract the spikes *before* the goal closes.
    // Drive Settings.spikeProgress toward a *predicted* target instead of a binary flag: while armed,
    // the target is 1 until the soonest-expiring launch is within SPIKE_ANIM_FRAMES of running out,
    // then it eases 1→0 so extension reaches 0 the exact frame the goal disarms (cantScore fires).
    // Arming still ramps in over ~SPIKE_ANIM_FRAMES frames via the ±maxStep cap; a side-wall bounce
    // that disarms abruptly just gives a graceful capped retract after the close (see Plan 6 §B).
    fun updateSpikes() {
        if (!this::highPlayer.isInitialized) return
        // Score window "Never": the goal is always open and visibly extended for the whole point —
        // it never closes. Force it here (runs every tick, ahead of the state machine) so no reset
        // path can leave it shut. Excludes the menu demo, which manages its own (never-scoring) goals.
        if (Settings.goalsAlwaysOpen && !Settings.isDemoMode) {
            Settings.canScore = true
            Settings.spikeProgress = 1f
            return
        }
        // Plan 3: during a score interlude, hold the spikes fully extended until the pierced ball has
        // actually popped/vanished — the danger reads as "consuming" the ball. Only after it's gone do
        // they begin to retract (over the next ~16 frames, in step with the pop's separation gap).
        if (scoreCinematicActive && !piercedHasVanished()) {
            Settings.spikeProgress = 1f
            return
        }

        val target = if (!Settings.canScore) 0f else {
            val frames = framesUntilGoalCloses()                       // soonest launch expiry, in frames
            (frames / Settings.SPIKE_ANIM_FRAMES).coerceIn(0f, 1f)     // 1 when far off; eases 1→0 in the last window
        }
        val maxStep = 1f / Settings.SPIKE_ANIM_FRAMES
        Settings.spikeProgress = Settings.spikeProgress +
            (target - Settings.spikeProgress).coerceIn(-maxStep, maxStep)
    }

    // Soonest frame count until SOME launched ball's launch power expires (= when cantScore will fire).
    // POSITIVE_INFINITY when nothing is winding down (keeps the target pinned at 1 while armed).
    // Inlined over the two players — no array allocation in this per-tick method.
    private fun framesUntilGoalCloses(): Float {
        val f = Settings.friction.coerceAtLeast(0.0001f)   // floor the divisor so friction=0 can't divide by zero
        // The window closes when a launch decays to scoreWindowCloseLevel (0 for Normal, half the max
        // launch for Fast), so count frames until the soonest launch reaches that level — not zero.
        val level = Settings.scoreWindowCloseLevel
        var soonest = Float.POSITIVE_INFINITY
        val hp = highPlayer.puck.launch.power
        if (hp > level) soonest = (hp - level) / f
        val lp = lowPlayer.puck.launch.power
        if (lp > level && (lp - level) / f < soonest) soonest = (lp - level) / f
        return soonest
    }

    // Plan 4 — Shielded balls flatten the spikes near them.
    // For each goal independently, find the nearest shielded puck within a vertical activation band of
    // the goal baseline and publish its X (the dent centre) + a 0→1 strength that deepens with vertical
    // closeness. Drawing.buildSpikePath reads these to dent the tooth row local to the ball. Purely
    // visual: a shielded ball already bounces at the baseline (Player.shouldBounce). No allocation —
    // plain Float fields mutated in place, both players inlined.
    fun updateShieldFlatten() {
        if (!this::highPlayer.isInitialized) return
        val activateDist = (Settings.screenRatio * Settings.SHIELD_FLATTEN_ACTIVATE_RATIO)
            .coerceAtLeast(0.0001f)
        // Distance at which the dent saturates to full flatten (strength 1). The ramp runs over
        // [fullDist, activateDist]; closer than fullDist clamps to 1, so the teeth are fully down a
        // bit before the ball reaches the goal baseline.
        val fullDist = Settings.screenRatio * Settings.SHIELD_FLATTEN_FULL_RATIO
        val rampSpan = (activateDist - fullDist).coerceAtLeast(0.0001f)

        var topX = Float.NaN; var topStrength = 0f; var topBest = Float.MAX_VALUE
        var botX = Float.NaN; var botStrength = 0f; var botBest = Float.MAX_VALUE

        if (highPlayer.shielded) {
            val dTop = highPlayer.py - Settings.topGoalBottom
            if (dTop < activateDist && dTop < topBest) {
                topBest = dTop; topX = highPlayer.px
                topStrength = ((activateDist - dTop) / rampSpan).coerceIn(0f, 1f)
            }
            val dBot = Settings.bottomGoalTop - highPlayer.py
            if (dBot < activateDist && dBot < botBest) {
                botBest = dBot; botX = highPlayer.px
                botStrength = ((activateDist - dBot) / rampSpan).coerceIn(0f, 1f)
            }
        }
        if (lowPlayer.shielded) {
            val dTop = lowPlayer.py - Settings.topGoalBottom
            if (dTop < activateDist && dTop < topBest) {
                topBest = dTop; topX = lowPlayer.px
                topStrength = ((activateDist - dTop) / rampSpan).coerceIn(0f, 1f)
            }
            val dBot = Settings.bottomGoalTop - lowPlayer.py
            if (dBot < activateDist && dBot < botBest) {
                botBest = dBot; botX = lowPlayer.px
                botStrength = ((activateDist - dBot) / rampSpan).coerceIn(0f, 1f)
            }
        }

        Settings.highGoalFlattenX = topX
        Settings.highGoalFlattenStrength = topStrength
        Settings.lowGoalFlattenX = botX
        Settings.lowGoalFlattenStrength = botStrength
    }

    // True once the pierced ball has finished shrinking out of existence. Until the pop is triggered
    // (Hold→Expand) — or while the ball is still mid-shrink — this is false so the spikes stay sharp.
    // Result.Both never triggers a pop, so its spikes stay sharp until the interlude ends.
    private fun piercedHasVanished(): Boolean {
        if (!piercedPopTriggered) return false
        val loser = piercedPlayer ?: return true
        return !loser.disappearing
    }

    fun checkCharge() {
        if (highPlayer.touch == TouchState.Down) {
            highPlayer.increaseCharge()
        }
        if (lowPlayer.touch == TouchState.Down) {
            lowPlayer.increaseCharge()
        }
    }

    fun checkShield() {
        if (highPlayer.power == 0f) {
            highPlayer.shielded = false
        }
        if (lowPlayer.power == 0f) {
            lowPlayer.shielded = false
        }
    }

    fun checkScored() : Result {
        if (Settings.isDemoMode) return Result.Neither
        val highScored = checkScored(highPlayer, lowPlayer)
        val lowScored = checkScored(lowPlayer, highPlayer)
        val result = if (highScored && !lowScored) {
            Result.High
        } else if (lowScored && !highScored) {
            Result.Low
        } else if (highScored && lowScored) {
            Result.Both
        } else {
            Result.Neither
        }
        // Captured on the scoring frame and read throughout the Scored interlude (checkScored only
        // runs during Play): Both → both balls lerp home, no pop. Single score → the loser pops.
        bothScored = result == Result.Both
        return result
    }

    private fun checkScored(winner: Player, loser: Player) : Boolean {
        if ( loser.shielded) return false
        if (Settings.canScore && (loser.py < Settings.topGoalBottom + loser.pRadius || loser.py > Settings.bottomGoalTop - loser.pRadius)) {
            val highGoal = loser.py < Settings.topGoalBottom + loser.pRadius
            winner.score()
            // Flip the winner's dial section to its secondary "updating" colour the instant the score
            // commits — the number itself still waits for the toss, but the colour change is immediate.
            ScoreDial.markScorePending(winner.isHigh)
            // Plan 3: the dial number updates only when the tossed paddle ARRIVES (fired at toss
            // landing in updateScoredPaddles). With the cinematic disabled there is no toss window,
            // so update the number immediately instead.
            if (!Settings.scoreCinematicEnabled) {
                ScoreDial.triggerScoreSpin(winner.isHigh, winner.score)
            }
            loser.clearPower()
            winner.clearPower()
            if (Settings.scoreCinematicEnabled) {
                // Bake the winner's current rainbow colour (if strobing) so the dim wash holds one
                // colour and never strobes; falls back to the configured fill otherwise.
                startScoreCinematic(winner, loser, winner.puck.renderer.bakedPrimary(winner.puckFillColor))
            }
            if (Settings.scorePopEnabled) {
                if (winner.isHigh) {
                    Settings.highScorePopTicker.reset()
                } else {
                    Settings.lowScorePopTicker.reset()
                }
            }
            // Plan 7: capture the pierced ball's burst colour + goal BEFORE the colour swap below, so
            // the deferred pop burst (triggerPiercedPop / triggerBothBursts) fires in its real colour.
            if (loser.isHigh) {
                highBurstColor = loser.puckFillColor
                highBurstHighGoal = highGoal
            } else {
                lowBurstColor = loser.puckFillColor
                lowBurstHighGoal = highGoal
            }
            // Impact Effects: a non-shielded ball entering an open goal never bounces, so this is its
            // only impact. Fire a goal-mouth Wall-on-Ball burst in the loser's real (pre-swap) colour.
            val goalDir = if (highGoal) Direction.TOP else Direction.BOTTOM
            // Loser is never shielded here (checkScored bails on a shielded loser), but it can be inert.
            val goalGroup = burstGroup(loser, false, loser.inertLocked || loser.fatigueInertLocked)
            val goalCol = burstColor(loser, goalGroup, loser.movementSpeed)
            // The scorer is never shielded and crosses an open (spiked) goal, so treat it as the hottest
            // tier: base + spikes + its real head-on angle. Read the heading non-mutatingly (netDir).
            val ndx = loser.puck.netDirX(); val ndy = loser.puck.netDirY()
            val nspeed = hypot(ndx, ndy).coerceAtLeast(0.0001f)
            val goalSpeedNorm = (loser.movementSpeed / FlashTuning.wallSpeedRef).coerceIn(0f, 1f)
            val goalAngleFactor = abs(ndy) / nspeed
            val goalTangentSign = if (ndx >= 0f) 1f else -1f
            val goalIntensity = (FlashTuning.wallBaseIntensity
                + goalSpeedNorm * FlashTuning.wallSpeedWeight
                + FlashTuning.wallSpikesBonus
                + goalAngleFactor * FlashTuning.wallAngleWeight
                ).coerceAtMost(FlashTuning.wallIntensityMax)
            Effects.addWallCollisionEffect(goalDir, goalCol, loser.puck, goalIntensity, goalAngleFactor, goalTangentSign)
            setPuckColor(loser, PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb())
            setPuckColor(winner, PaintBucket.lowBallFill.toArgb(), PaintBucket.lowBallStroke.toArgb())
            Settings.gameState = GameState.Scored
            Settings.canScore = false
            // NB: spikeProgress is intentionally NOT zeroed here — updateSpikes holds the spikes fully
            // sharp through the interlude until the pierced ball has popped, then retracts them.
            botBrain?.reset()
            Sounds.playScoreSound(loser.py)
            Effects.signalScored()
            // Plan 7: the pierced ball's onUsedToScore burst is deferred to the pop (triggerPiercedPop
            // / triggerBothBursts). The winner-side onScored() still fires now.
            winner.puck.renderer.skin.onScored()
            return true
        }
        return false
    }

    // Initializes the score-cinematic interlude at the moment of a score (called from checkScored).
    // [winner] is the scoring player (its colour washes the screen); [loser] is the pierced player
    // the window frames. [overlayColor] is the winner's baked fill (already rainbow-resolved).
    private fun startScoreCinematic(winner: Player, loser: Player, overlayColor: Int) {
        scoreCinematicActive = true
        scorePhase = ScorePhase.Shrink
        piercedPopTriggered = false
        bothBurstsFired = false
        tossHigh.reset()
        tossLow.reset()
        scoreCinematicTicker.reset(Settings.SCORE_SHRINK_FRAMES)
        pierceX = loser.px
        pierceY = loser.py
        scoreOverlayColor = overlayColor
        // Plan 5: the spotlight ring is the winner's stroke colour, baked so a rainbow ball doesn't
        // strobe the ring during the freeze. Resolved once here — no per-frame theme alloc.
        scoreOutlineColor = winner.puck.renderer.bakedSecondary(ColorTheme.getTheme(winner.isHigh).main.secondary)
        scoringPlayer = winner
        piercedPlayer = loser
        // Max window radius = distance from the pierce point to the farthest screen corner, so the
        // expanding window fully clears the screen even from an edge-of-screen pierce.
        val farX = if (pierceX < Settings.middleX) Settings.screenWidth else 0f
        val farY = if (pierceY < Settings.middleY) Settings.screenHeight else 0f
        scoreWindowMaxRadius = hypot(pierceX - farX, pierceY - farY)
    }

    fun scored() {
        lowPlayer.disableEffects = true
        highPlayer.disableEffects = true

        if (Settings.scoreCinematicEnabled && scoreCinematicActive) {
            when (scorePhase) {
                // Shrink / Hold: physics frozen — the pucks hold position while the window animates.
                ScorePhase.Shrink -> if (scoreCinematicTicker.tick) {
                    scorePhase = ScorePhase.Hold
                    scoreCinematicTicker.reset(Settings.SCORE_HOLD_FRAMES)
                }
                ScorePhase.Hold -> if (scoreCinematicTicker.tick) {
                    scorePhase = ScorePhase.Expand
                    scoreCinematicTicker.reset(Settings.SCORE_EXPAND_FRAMES)
                    // Window is tight on the ball — pop the pierced ball now and fire its score burst
                    // on the same frame (Plan 7), so it reads as the ball exploding as it vanishes.
                    // Exactly one of these fires: triggerPiercedPop no-ops for Result.Both, and
                    // triggerBothBursts no-ops otherwise.
                    triggerPiercedPop()
                    triggerBothBursts()
                }
                // Expand: the winner lerps home and the pierced ball pops + re-materializes at its
                // start while the window expands away. Play resumes only once the window has cleared,
                // the winner has arrived, AND the new ball has finished re-materializing.
                ScorePhase.Expand -> {
                    // Plan 3: advance the toss(es). Each fires the dial spin on arrival.
                    updateScoredPaddles()
                    // The resume gate is now purely cosmetic: window cleared AND toss landed AND the
                    // spin finished, so play never resumes on a half-spun numeral. Win detection
                    // already ran at score time (checkScored), so this only delays the visual release.
                    val gate = scoreCinematicTicker.finished && scoredPaddlesIdle() && !ScoreDial.isSpinning
                    if (bothScored) returnPucksHome(gate) else returnPucksHomeWithPop(gate)
                    if (!scoreCinematicTicker.finished) scoreCinematicTicker.tick
                }
            }
        } else {
            // Cinematic disabled → original instant both-balls-home reset.
            returnPucksHome()
        }
        resetPlayerStates(highPlayer, lowPlayer)
    }

    // Lerps both pucks toward their reset positions; once both have arrived (and [gate] is satisfied)
    // transitions to Play/GameOver, re-enables effects, and ends the cinematic. Returns true on the
    // frame the transition happens.
    private fun returnPucksHome(gate: Boolean = true): Boolean {
        val lowIsReady = lowPlayer.moveTowardPoint(lowPlayer.resetLocation)
        val highIsReady = highPlayer.moveTowardPoint(highPlayer.resetLocation)
        if (lowIsReady && highIsReady && gate) {
            endScoreInterlude()
            return true
        }
        return false
    }

    // Plan 3 return path: the winner lerps home as usual; the pierced ball is driven by its teleport
    // pop/regrow (in drawTeleport) and counts as "home" only once fully re-materialized (!isTeleporting).
    private fun returnPucksHomeWithPop(gate: Boolean): Boolean {
        val winner = scoringPlayer
        val loser = piercedPlayer
        val winnerReady = winner?.moveTowardPoint(winner.resetLocation) ?: true
        val loserReady = loser?.let { !it.isTeleporting } ?: true
        if (winnerReady && loserReady && gate) {
            endScoreInterlude()
            return true
        }
        return false
    }

    // Pop the pierced ball: jump straight into the teleport disappear (no prepare ring) and re-target
    // its reappearance to its own start. Skipped for Result.Both and idempotent within an interlude.
    private fun triggerPiercedPop(): Unit {
        if (bothScored || piercedPopTriggered) return
        piercedPopTriggered = true
        val loser = piercedPlayer ?: return
        // Plan 7: spawn the pierced ball's score burst (its pre-swap colour/goal) on the same frame it
        // begins to shrink out — reused _burstPoint avoids a per-pop Point allocation.
        val burstColor = if (loser.isHigh) highBurstColor else lowBurstColor
        val burstHighGoal = if (loser.isHigh) highBurstHighGoal else lowBurstHighGoal
        _burstPoint.setLocation(pierceX, pierceY)
        loser.puck.renderer.skin.onUsedToScore(burstColor, _burstPoint, burstHighGoal)
        loser.popAndTeleportTo(loser.resetLocation)
        // Plan 3: fling the loser's (static) paddle from the pop point into the winner's dial number.
        val winner = scoringPlayer
        if (winner != null) {
            val target = ScoreDial.numberCenter(winner.isHigh)
            val toss = if (loser.isHigh) tossHigh else tossLow
            toss.spawn(loser.puck.renderer.effect, pierceX, pierceY, target.x, target.y, winner.isHigh)
        }
    }

    // Plan 7, Result.Both: both balls scored simultaneously, so neither teleports (Plan 3 rule) — but
    // both still burst. Fires each player's onUsedToScore with its own pre-swap colour/goal at the
    // Hold→Expand boundary. Idempotent within an interlude.
    private fun triggerBothBursts() {
        if (!bothScored || bothBurstsFired) return
        bothBurstsFired = true
        _burstPoint.setLocation(highPlayer.px, highPlayer.py)
        highPlayer.puck.renderer.skin.onUsedToScore(highBurstColor, _burstPoint, highBurstHighGoal)
        _burstPoint.setLocation(lowPlayer.px, lowPlayer.py)
        lowPlayer.puck.renderer.skin.onUsedToScore(lowBurstColor, _burstPoint, lowBurstHighGoal)
        // Plan 3: a simultaneous score — each player collects their own paddle into their own number.
        val targetHigh = ScoreDial.numberCenter(true)
        tossHigh.spawn(highPlayer.puck.renderer.effect, highPlayer.px, highPlayer.py, targetHigh.x, targetHigh.y, true)
        val targetLow = ScoreDial.numberCenter(false)
        tossLow.spawn(lowPlayer.puck.renderer.effect, lowPlayer.px, lowPlayer.py, targetLow.x, targetLow.y, false)
    }

    // ---- Plan 3 toss helpers ----

    // Advance the active toss(es); when one lands, spin its target number to the (already incremented)
    // logical score. Called each Expand frame.
    private fun updateScoredPaddles() {
        if (tossHigh.active && tossHigh.update()) {
            ScoreDial.triggerScoreSpin(tossHigh.targetIsHigh, scoreFor(tossHigh.targetIsHigh))
        }
        if (tossLow.active && tossLow.update()) {
            ScoreDial.triggerScoreSpin(tossLow.targetIsHigh, scoreFor(tossLow.targetIsHigh))
        }
    }

    private fun scoredPaddlesIdle(): Boolean = !tossHigh.active && !tossLow.active

    private fun scoreFor(isHigh: Boolean): Int = if (isHigh) highPlayer.score else lowPlayer.score

    // Shared tail of both return paths: pick Play vs GameOver, re-enable effects, end the cinematic.
    private fun endScoreInterlude() {
        val scoreWin = Settings.pointsToWin > 0 && (lowPlayer.score >= Settings.pointsToWin || highPlayer.score >= Settings.pointsToWin)
        val timerGameOver = timerExpired && highPlayer.score != lowPlayer.score
        Settings.gameState = if (!Settings.gameOver && (scoreWin || timerGameOver)) {
            Settings.gameOver = true
            victoryTicker.reset(Settings.victoryThreshold)
            GameState.GameOver
        } else {
            canCollide = true
            GameState.Play
        }
        highPlayer.disableEffects = false
        lowPlayer.disableEffects = false
        scoreCinematicActive = false
    }

    fun gameOver() {
        lowPlayer.disableEffects = true
        highPlayer.disableEffects = true
        if (!winnerSoundHasBeenPlayed) {
            winnerSoundHasBeenPlayed = true
            Sounds.playWeHaveAWinner()
            GameEvents.gameOver.emit(Unit)
            startVictoryCelebration()
        }
        updateVictoryCelebration()
        if (victoryTicker.tick) {
            winnerSoundHasBeenPlayed = false
            victoryTicker.reset()
            lowPlayer.score = 0
            highPlayer.score = 0
            ScoreDial.syncFromPlayers()
            lowPlayer.disableEffects = false
            highPlayer.disableEffects = false
            Settings.canScore = false
            Settings.spikeProgress = 0f
            lowPlayer.shielded = false
            highPlayer.shielded = false
            lowPlayer.inertLocked = false
            highPlayer.inertLocked = false
            lowPlayer.fatigueInertLocked = false
            highPlayer.fatigueInertLocked = false
            lowPlayer.clearCharge()
            highPlayer.clearCharge()
            Settings.gameOver = false
            highPopupDragging = false
            lowPopupDragging  = false
            GameEvents.gameReset.emit(Unit)
            timerMark = null
            timerStarted = false
            timerExpired = false
            timerHidden = false
            timerShowFinalZero = false
            timerSecondsRemaining = Settings.timeLimitMinutes * 60
            TimeDial.syncFromTimer()
            Settings.gameState = GameState.BallSelection
            Drawing.cycleHighTip()
            Drawing.cycleLowTip()
            highPlayer.puck.x = highPlayer.resetLocation.x
            highPlayer.puck.y = highPlayer.resetLocation.y
            lowPlayer.puck.x = lowPlayer.resetLocation.x
            lowPlayer.puck.y = lowPlayer.resetLocation.y
            highBallPopup.open()
            lowBallPopup.open()
        }
    }

    // Plan 2 (Restart button): reset both scores and start a fresh match at ball selection. Mirrors
    // the reset tail of gameOver() but without the victory bookkeeping — the match simply restarts.
    fun restartMatch() {
        if (!isInitialized) return
        highPlayer.score = 0
        lowPlayer.score = 0
        ScoreDial.syncFromPlayers()
        highPlayer.clearCharge(); lowPlayer.clearCharge()
        highPlayer.clearPower(); lowPlayer.clearPower()
        highPlayer.shielded = false; lowPlayer.shielded = false
        highPlayer.inertLocked = false; lowPlayer.inertLocked = false
        highPlayer.fatigueInertLocked = false; lowPlayer.fatigueInertLocked = false
        Settings.canScore = false
        Settings.spikeProgress = 0f
        Settings.gameOver = false
        winnerSoundHasBeenPlayed = false
        scoreCinematicActive = false
        tossHigh.reset()
        tossLow.reset()
        highPopupDragging = false
        lowPopupDragging = false
        timerMark = null
        timerStarted = false
        timerExpired = false
        timerHidden = false
        timerShowFinalZero = false
        timerSecondsRemaining = Settings.timeLimitMinutes * 60
        TimeDial.syncFromTimer()
        highPlayer.puck.x = highPlayer.resetLocation.x
        highPlayer.puck.y = highPlayer.resetLocation.y
        lowPlayer.puck.x = lowPlayer.resetLocation.x
        lowPlayer.puck.y = lowPlayer.resetLocation.y
        Settings.gameState = GameState.BallSelection
        highBallPopup.open()
        lowBallPopup.open()
        GameEvents.gameReset.emit(Unit)
        Drawing.cycleHighTip()
        Drawing.cycleLowTip()
    }

    fun startVictoryCelebration() {
        victoryCelebrationFrame = 0
    }

    var previousAngle = 0f

    private fun updateVictoryCelebration() {
        val winner = when {
            Settings.pointsToWin > 0 && highPlayer.score >= Settings.pointsToWin -> highPlayer
            Settings.pointsToWin > 0 && lowPlayer.score >= Settings.pointsToWin -> lowPlayer
            highPlayer.score > lowPlayer.score -> highPlayer
            else -> lowPlayer
        }
        if (victoryCelebrationFrame % winner.puck.renderer.skin.explosionFrequency == 0) {
            val skin = winner.puck.renderer.skin
            val baseMargin = Settings.screenRatio * 2f
            val maxDist = winner.puck.renderer.radius * 5f * skin.scatterDensity
            var angle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
            if ((angle - previousAngle).absoluteValue < 60f) angle += 60f
            val dist = Random.nextFloat() * maxDist
            val cx = winner.puck.x
            val cy = winner.puck.y
            var x = cx + kotlin.math.cos(angle) * dist
            var y = cy + kotlin.math.sin(angle) * dist
            val xMin = baseMargin
            val xMax = Settings.screenWidth - baseMargin
            val yMin = Settings.topGoalBottom + baseMargin
            val yMax = Settings.bottomGoalTop - baseMargin
            if (x < xMin) x = 2f * cx - x
            if (x > xMax) x = 2f * cx - x
            x = x.coerceIn(xMin, xMax)
            y = y.coerceIn(yMin, yMax)
            skin.onVictory(x, y)
        }
        victoryCelebrationFrame++
    }

    fun calculateCollision() : Boolean {
        if (highPlayer.pucksIntersect(lowPlayer)) {
            val direction = highPlayer.puck.directionTo(lowPlayer.puck)
            // collisionPoint and the skin-callback intersection are the same point — compute it once.
            val intersection = lowPlayer.puck.intersectionPoint(highPlayer.puck)
            val collisionPoint = intersection

            // launchFrom is a reusable field; mutate it instead of allocating a Point each collision.
            highPlayer.launchFrom.setLocation(highPlayer.px, highPlayer.py)
            lowPlayer.launchFrom.setLocation(lowPlayer.px, lowPlayer.py)

            val lowPower = lowPlayer.power
            val highPower = highPlayer.power

            // Plan 03: capture each ball's PRE-collision heading + state now, before any launch() below
            // overwrites its forces. netDir reads the movement+launch direction without mutating it.
            val highHeading = atan2(highPlayer.puck.netDirY(), highPlayer.puck.netDirX())
            val lowHeading  = atan2(lowPlayer.puck.netDirY(),  lowPlayer.puck.netDirX())
            val highWasShielded = highPlayer.shielded
            val lowWasShielded  = lowPlayer.shielded
            val highWasInert = highPlayer.inertLocked || highPlayer.fatigueInertLocked
            val lowWasInert  = lowPlayer.inertLocked  || lowPlayer.fatigueInertLocked
            val highPreSpeed = highPlayer.movementSpeed
            val lowPreSpeed  = lowPlayer.movementSpeed
            // "Inert" for burst-scaling = shield-locked in place OR barely moving (near-stationary).
            val highInertBurst = highWasInert || highPreSpeed < FlashTuning.ballInertSpeed
            val lowInertBurst  = lowWasInert  || lowPreSpeed  < FlashTuning.ballInertSpeed

            highPlayer.bonusCountdown = 0f
            lowPlayer.bonusCountdown = 0f

            if (highPlayer.reappearing && lowPlayer.reappearing) {
                Sounds.playTeleportFinish(highPlayer.px)
                Sounds.playTeleportFinish(lowPlayer.px)
                highPlayer.launch(Force(-direction, collisionBonus + Settings.sweetSpotMax))
                lowPlayer.launch(Force(direction, collisionBonus + Settings.sweetSpotMax))
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
            } else if (highPlayer.reappearing) {
                Sounds.playTeleportFinish(highPlayer.px)
                lowPlayer.launch(Force(direction, collisionBonus + Settings.sweetSpotMax))
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
            } else if (lowPlayer.reappearing) {
                Sounds.playTeleportFinish(lowPlayer.px)
                highPlayer.launch(Force(-direction, collisionBonus + Settings.sweetSpotMax))
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
            } else if (highPlayer.shielded && !lowPlayer.shielded) {
                Sounds.playChargeCollision(collisionPoint.x)
                val bonusPower = if (lowPlayer.isCharging) 2f * collisionBonus else collisionBonus
                lowPlayer.launch(Force(direction, bonusPower + highPlayer.power))
                highPlayer.launch(Force(-direction, Settings.minLaunchPower))
                lowPlayer.inertLocked = true
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                lowPlayer.puck.renderer.skin.onHit()
            } else if (lowPlayer.shielded && !highPlayer.shielded) {
                Sounds.playChargeCollision(collisionPoint.x)
                val bonusPower = if (highPlayer.isCharging) 2f * collisionBonus else collisionBonus
                highPlayer.launch(Force(-direction, bonusPower + lowPlayer.power))
                lowPlayer.launch(Force(direction, Settings.minLaunchPower))
                highPlayer.inertLocked = true
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                highPlayer.puck.renderer.skin.onHit()
            } else if (lowPlayer.shielded && highPlayer.shielded) {
                Sounds.playDoubleChargeCollision(collisionPoint.x)
                highPlayer.launch(Force(-direction, collisionBonus + lowPower))
                lowPlayer.launch(Force(direction, collisionBonus + highPower))
                highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
            } else if (highPlayer.isCharging && lowPlayer.isCharging) {
                Sounds.playDoubleChargeCollision(collisionPoint.x)
                highPlayer.launch(Force(-direction, collisionBonus + lowPower))
                lowPlayer.launch(Force(direction, collisionBonus + highPower))
                highPlayer.inertLocked = true
                lowPlayer.inertLocked = true
                highPlayer.clearCharge()
                lowPlayer.clearCharge()
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                highPlayer.puck.renderer.skin.onHit()
                lowPlayer.puck.renderer.skin.onHit()
            } else if (highPlayer.isCharging) {
                Sounds.playChargeCollision(collisionPoint.x)
                highPlayer.launch(Force(-direction, collisionBonus + lowPower))
                lowPlayer.launch(Force(direction, if (highPower < Settings.minLaunchPower) Settings.minLaunchPower else highPower))
                highPlayer.inertLocked = true
                highPlayer.clearCharge()
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                highPlayer.puck.renderer.skin.onHit()
                lowPlayer.puck.renderer.skin.onCollisionWin(intersection, lowPlayer.movementSpeed)
            } else if (lowPlayer.isCharging) {
                Sounds.playChargeCollision(collisionPoint.x)
                lowPlayer.launch(Force(direction, collisionBonus + highPower))
                highPlayer.launch(Force(-direction, if (lowPower < Settings.minLaunchPower) Settings.minLaunchPower else lowPower))
                lowPlayer.inertLocked = true
                lowPlayer.clearCharge()
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                lowPlayer.puck.renderer.skin.onHit()
                highPlayer.puck.renderer.skin.onCollisionWin(intersection, highPlayer.movementSpeed)
            } else {
                highPlayer.launch(Force(-direction, if (lowPower < Settings.minLaunchPower) Settings.minLaunchPower else lowPower))
                lowPlayer.launch(Force(direction, if (highPower < Settings.minLaunchPower) Settings.minLaunchPower else highPower))
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)

                val highSpeed = highPlayer.movementSpeed
                val lowSpeed = lowPlayer.movementSpeed
                if (highSpeed >= lowSpeed && highSpeed >= Settings.minLaunchPower) {
                    highPlayer.puck.renderer.skin.onCollisionWin(intersection, highSpeed)
                    lowPlayer.puck.renderer.skin.onHit()
                } else if (lowSpeed > highSpeed && lowSpeed >= Settings.minLaunchPower) {
                    lowPlayer.puck.renderer.skin.onCollisionWin(intersection, lowSpeed)
                    highPlayer.puck.renderer.skin.onHit()
                } else {
                    highPlayer.puck.renderer.skin.onHit()
                    lowPlayer.puck.renderer.skin.onHit()
                }
            }
            resetTails(highPlayer, lowPlayer)

            // Plan 03: one forward "shotgun" flash burst per ball, along its own captured pre-collision
            // heading, in its own baked colour. Head-on (opposite headings) reads hotter + tighter;
            // a glancing (perpendicular) clip is milder + more scattered. Shielded > normal > inert.
            val dotAB = cos(highHeading) * cos(lowHeading) + sin(highHeading) * sin(lowHeading)
            val headOn = ((-dotAB).coerceIn(-1f, 1f) * 0.5f + 0.5f)   // 1 opposite, 0.5 perpendicular, 0 same
            val scatter = (1f - headOn).coerceIn(0f, 1f)
            val highBurstCol = burstColor(highPlayer,
                burstGroup(highPlayer, highWasShielded, highWasInert), highPreSpeed)
            val lowBurstCol = burstColor(lowPlayer,
                burstGroup(lowPlayer, lowWasShielded, lowWasInert), lowPreSpeed)
            Effects.addBallCollisionEffect(collisionPoint.x, collisionPoint.y, highBurstCol, highHeading,
                ballBurstIntensity(highPreSpeed, highWasShielded, highInertBurst, headOn), scatter)
            Effects.addBallCollisionEffect(collisionPoint.x, collisionPoint.y, lowBurstCol, lowHeading,
                ballBurstIntensity(lowPreSpeed, lowWasShielded, lowInertBurst, headOn), scatter)

            if (!Settings.isDemoMode) {
                GameEvents.canScore.emit(Unit)
            }
            if (highPlayer.power > lowPlayer.power) {
                Sounds.playLowPlayerSound(collisionPoint.y)
            } else {
                Sounds.playHighPlayerSound(collisionPoint.y)
            }
            return true
        }
        return false
    }

    /** The theme colour GROUP an impact burst draws from: shield if shielded, inert if inert-locked,
     *  else main. (primary-vs-secondary within it is chosen by speed in [burstColor].) */
    private fun burstGroup(player: Player, shielded: Boolean, inert: Boolean): ColorGroup {
        val theme = ColorTheme.getTheme(player.isHigh)
        return when {
            shielded -> theme.shield
            inert -> theme.inert
            else -> theme.main
        }
    }

    /** Baked impact-burst colour: a slow ball (< slowColorSpeedFraction of max speed) shows the softer
     *  PRIMARY as an extra low-intensity cue; a fast ball shows the SECONDARY. Baked so a rainbow ball's
     *  spark freezes its current hue instead of strobing. */
    private fun burstColor(player: Player, group: ColorGroup, speed: Float): Int {
        val slow = speed < Settings.maxPuckSpeed * FlashTuning.slowColorSpeedFraction
        val renderer = player.puck.renderer
        return if (slow) renderer.bakedPrimary(group.primary) else renderer.bakedSecondary(group.secondary)
    }

    /** Ball-on-Ball burst intensity: shielded > normal > inert (inert scaled way down), nudged by the
     *  ball's own speed and the shared head-on factor. Clamped so no hit runs away. */
    private fun ballBurstIntensity(speed: Float, shielded: Boolean, inert: Boolean, headOn: Float): Float {
        val speedNorm = (speed / FlashTuning.wallSpeedRef).coerceIn(0f, 1f)
        var i = FlashTuning.ballBaseIntensity +
            speedNorm * FlashTuning.ballSpeedWeight +
            (if (shielded) FlashTuning.ballShieldBonus else 0f) +
            headOn * FlashTuning.ballAngleWeight
        if (inert) i *= FlashTuning.ballInertScale
        return i.coerceAtMost(FlashTuning.ballIntensityMax)
    }

    fun adjustPlayerPositions() : Boolean {
        val highBonus = adjustPlayerPosition(highPlayer)
        val lowBonus = adjustPlayerPosition(lowPlayer)
        if (highPlayer.hitStunFramesRemaining > 0) highPlayer.hitStunFramesRemaining--
        if (lowPlayer.hitStunFramesRemaining > 0) lowPlayer.hitStunFramesRemaining--
        return highBonus && lowBonus
    }

    fun adjustPlayerPosition(player: Player) : Boolean {
        var gotBonus = player.shielded
        if (gotBonus) {
            player.inertLocked = false
        }
        if (player.shouldReleaseCharge) {
            gotBonus = player.releaseCharge()
        }
        // The score window closes when the launch decays past scoreWindowCloseLevel (0 = Normal's
        // full decay, half max launch = Fast's early cutoff). Shield/inert-lock still release only on
        // a full decay (power → 0), independent of the window, so Fast doesn't drop shields early.
        val closeLevel = Settings.scoreWindowCloseLevel
        val wasAboveCloseLevel = player.puck.launch.power > closeLevel
        val hadLaunchPower = player.puck.launch.hasPower
        val hadMovementPower = player.puck.movement.hasPower
        if (player.applyForces()) {
            val group = burstGroup(player, player.shielded, player.inertLocked || player.fatigueInertLocked)
            val flashCol = burstColor(player, group, player.movementSpeed)
            // Plan 02: contextual intensity — speed + power + shield + spikes + head-on angle, clamped.
            // Differences are deliberately slight (bigger/more/longer, never a different effect).
            val speedNorm = (player.movementSpeed / FlashTuning.wallSpeedRef).coerceIn(0f, 1f)
            val powerNorm = (player.power / Settings.sweetSpotMax).coerceIn(0f, 1f)
            val dir = player.bounceDirection
            // "Hit spikes" = bouncing at a goal mouth while the goal is armed and its spikes are out.
            val hitSpikes = (dir == Direction.TOP || dir == Direction.BOTTOM) &&
                Settings.canScore && Settings.spikeProgress >= FlashTuning.wallSpikesThreshold
            val intensity = (FlashTuning.wallBaseIntensity
                + speedNorm * FlashTuning.wallSpeedWeight
                + powerNorm * FlashTuning.wallPowerWeight
                + (if (player.shielded) FlashTuning.wallShieldBonus else 0f)
                + (if (hitSpikes) FlashTuning.wallSpikesBonus else 0f)
                + player.lastBounceAngleFactor * FlashTuning.wallAngleWeight
                ).coerceAtMost(FlashTuning.wallIntensityMax)
            Effects.addWallCollisionEffect(
                dir, flashCol, player.puck, intensity,
                player.lastBounceAngleFactor, player.lastBounceTangentSign
            )
            if (!player.shielded) player.puck.renderer.skin.onHit()
        }
        if (!Settings.goalsAlwaysOpen && wasAboveCloseLevel && player.puck.launch.power <= closeLevel) {
            GameEvents.cantScore.emit(Unit)
        }
        if (hadLaunchPower && !player.puck.launch.hasPower) {
            player.inertLocked = false
            player.shielded = false
        }
        if (hadMovementPower && !player.puck.movement.hasPower && !player.puck.launch.hasPower) {
            player.shielded = false
        }
        return gotBonus
    }

    // -------------------------------------------------------------------------
    // Compose pointer input API (platform-agnostic touch routing)
    // -------------------------------------------------------------------------

    // GameScreen passes semantic player IDs: 0 = high player, 1 = low player.
    // Pointer lifetime tracking lives in GameScreen; the per-down ownership decision is
    // resolved here (resolveTouchOwner) because the proximity scheme needs puck geometry.

    /**
     * Decide which puck a touch-down belongs to. Returns 0 (Top/high), 1 (Bottom/low), or
     * -1 (no assignment). [highAllowed] carries the single-player rule (the bot's half is
     * untouchable during Play). Both control schemes are resolved here so GameScreen stays
     * agnostic to the rule.
     */
    fun resolveTouchOwner(
        x: Float, y: Float,
        highTaken: Boolean, lowTaken: Boolean,
        highAllowed: Boolean
    ): Int {
        if (!isInitialized) return -1
        return when (Storage.touchScheme) {
            TouchScheme.BySide -> {
                // Legacy: claim the puck on whichever screen half was touched. No fallback.
                if (y < Settings.middleY) { if (!highTaken && highAllowed) 0 else -1 }
                else                      { if (!lowTaken) 1 else -1 }
            }
            TouchScheme.ByProximity -> {
                // Nearest puck wins; if it is already owned, the other puck takes the input.
                val dHigh = dist2(x, y, highPlayer.puck.x, highPlayer.puck.y)
                val dLow  = dist2(x, y, lowPlayer.puck.x,  lowPlayer.puck.y)
                val highFree = !highTaken && highAllowed
                val lowFree  = !lowTaken
                val highNearer = dHigh <= dLow
                when {
                    highNearer  && highFree -> 0
                    highNearer  && lowFree  -> 1
                    !highNearer && lowFree  -> 1
                    !highNearer && highFree -> 0
                    else -> -1
                }
            }
        }
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return dx * dx + dy * dy
    }

    fun onPointerDown(x: Float, y: Float, playerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptScoreMenuDown(x, y, playerId)) return
        if (interceptBallMenuDown(x, y, playerId)) return
        // In single-player, the high side belongs to the bot. Touches only exist there
        // to interact with the ball-selection popup (handled above); never engage fling.
        if (Settings.isSinglePlayer && playerId == 0) return
        val player = if (playerId == 0) highPlayer else lowPlayer
        if (Settings.gameState == GameState.BallSelection) {
            if (playerId == 0) Drawing.cycleHighTip() else Drawing.cycleLowTip()
            startFling(player, x, y)
            player.touch = TouchState.Down
            return
        }
        startFling(player, x, y)
        player.touch = TouchState.Down
        if (playerId == 0) Sounds.playHighPlayerSound(player.fx)
        else Sounds.playLowPlayerSound(player.fx)
    }

    fun onPointerMove(x: Float, y: Float, playerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptScoreMenuMove(x, y, playerId)) return
        if (interceptBallMenuMove(x, y, playerId)) return
        if (Settings.isSinglePlayer && playerId == 0) return
        updateFlingCurrent(if (playerId == 0) highPlayer else lowPlayer, x, y)
    }

    fun onPointerUp(x: Float, y: Float, playerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptScoreMenuUp(x, y, playerId)) return
        if (interceptBallMenuUp(x, y, playerId)) return
        if (Settings.isSinglePlayer && playerId == 0) return
        val player = if (playerId == 0) highPlayer else lowPlayer
        if (Settings.gameState == GameState.BallSelection) {
            endFling(player, x, y)
            player.touch = TouchState.Ready
            player.shouldReleaseCharge = true
            return
        }
        if (playerId == 0) Sounds.playHighPlayerSound(player.fx)
        else Sounds.playLowPlayerSound(player.fx)
        endFling(player, x, y)
        if (Settings.gameState == GameState.Play) player.shouldReleaseCharge = true
        player.touch = TouchState.Ready
    }

    private fun interceptBallMenuDown(x: Float, y: Float, playerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        if (playerId == 0) {
            if (highBallPopup.isOpen && highBallPopup.hitTest(x, y)) {
                if (!highPopupDragging) {
                    highPopupDragging = true
                    highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_DOWN, x, y)
                }
                Drawing.cycleHighTip()
                return true
            }
        } else {
            if (lowBallPopup.isOpen && lowBallPopup.hitTest(x, y)) {
                if (!lowPopupDragging) {
                    lowPopupDragging = true
                    lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_DOWN, x, y)
                }
                Drawing.cycleLowTip()
                return true
            }
        }
        return false
    }

    private fun interceptBallMenuMove(x: Float, y: Float, playerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        var consumed = false
        if (playerId == 0 && highBallPopup.isOpen && highPopupDragging) {
            highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_MOVE, x, y)
            consumed = true
        }
        if (playerId == 1 && lowBallPopup.isOpen && lowPopupDragging) {
            lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_MOVE, x, y)
            consumed = true
        }
        return consumed
    }

    private fun interceptBallMenuUp(x: Float, y: Float, playerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        var consumed = false
        if (playerId == 0 && highPopupDragging) {
            highPopupDragging = false
            if (highBallPopup.isOpen) highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_UP, x, y)
            consumed = true
        }
        if (playerId == 1 && lowPopupDragging) {
            lowPopupDragging = false
            if (lowBallPopup.isOpen) lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_UP, x, y)
            consumed = true
        }
        return consumed
    }

    // -------------------------------------------------------------------------
    // Score-dial / pause-menu touch routing (Plan 2)
    //
    // Mirrors interceptBallMenu so both the Android and iOS pointer paths feed one implementation.
    // A *tap* on the closed dial (down and up on it without leaving) opens the menu; a drag that
    // leaves the dial is released back to the edge-swipe / normal handling. While the menu is open
    // every touch is routed here: a tap on a button fires it, any off-menu tap closes (and resumes).
    // -------------------------------------------------------------------------

    private var dialTapActive = false
    private var dialTapPointer = -1

    // The dial only opens on a tap during live Play (not while a score cinematic / spin is mid-flight
    // and not during ball selection, where the dial is hidden).
    private fun canOpenScoreMenu(): Boolean =
        isInitialized && Settings.gameState == GameState.Play &&
            !scoreCinematicActive && !ScoreDial.isSpinning

    private fun interceptScoreMenuDown(x: Float, y: Float, playerId: Int): Boolean {
        if (ScoreDial.menuActive) return true   // menu open/animating: swallow downs (resolve on up)
        if (canOpenScoreMenu() && ScoreDial.hitDial(x, y)) {
            dialTapActive = true
            dialTapPointer = playerId
            return true   // consume so a dial press never starts a fling
        }
        return false
    }

    private fun interceptScoreMenuMove(x: Float, y: Float, playerId: Int): Boolean {
        if (ScoreDial.menuActive) return true   // ignore drags while the menu is open
        if (dialTapActive && playerId == dialTapPointer) {
            if (!ScoreDial.hitDial(x, y)) {
                // Left the dial → no longer a tap. Stop consuming so it can become an edge-swipe.
                dialTapActive = false
                dialTapPointer = -1
                return false
            }
            return true
        }
        return false
    }

    private fun interceptScoreMenuUp(x: Float, y: Float, playerId: Int): Boolean {
        if (ScoreDial.menuActive) {
            when {
                ScoreDial.hitReturn(x, y) -> ScoreDial.menuReturn()
                ScoreDial.hitRestart(x, y) -> ScoreDial.menuRestart()
                ScoreDial.hitMenu(x, y) -> { /* tap on the menu body but not a button: do nothing */ }
                else -> ScoreDial.requestCloseMenu()   // off-menu tap closes → resume once closed
            }
            return true
        }
        if (dialTapActive && playerId == dialTapPointer) {
            val wasOnDial = ScoreDial.hitDial(x, y)
            dialTapActive = false
            dialTapPointer = -1
            if (wasOnDial) {
                ScoreDial.requestOpenMenu()
                return true
            }
        }
        return false
    }

    /** Reset all touch state — call when the app foregrounds or pointer state is unknown. */
    fun releaseAllPointers() {
        if (!isInitialized) return
        listOf(highPlayer, lowPlayer).forEach { player ->
            player.touch = TouchState.Ready
            player.isFlingHeld = false
            player.shouldReleaseCharge = true
        }
        highPopupDragging = false
        lowPopupDragging  = false
    }

    fun closeBallPopups() {
        highBallPopup.close()
        lowBallPopup.close()
        highPopupDragging = false
        lowPopupDragging  = false
    }

    fun resetGame() {
        closeBallPopups()
        Settings.pointsToWin = Storage.loadPointsToWin()
        Settings.timeLimitMinutes = Storage.loadTimeLimit()
        Settings.highBallType = Storage.loadHighBallType(Settings.highBallType)
        Settings.lowBallType = Storage.loadLowBallType(Settings.lowBallType)
        Settings.unlockProgress = Storage.unlockProgress
        Settings.highPlayerArrow = Storage.highPlayerArrow
        Settings.lowPlayerArrow = Storage.lowPlayerArrow
        Settings.highPlayerChargeMeterStyle = Storage.highPlayerChargeMeterStyle
        Settings.lowPlayerChargeMeterStyle = Storage.lowPlayerChargeMeterStyle
        composeReinitCallback?.invoke()
        Settings.gameState = GameState.BallSelection
        Settings.gameOver = false
        Drawing.cycleHighTip()
        Drawing.cycleLowTip()
    }

    private fun startFling(player: Player, x: Float, y: Float) {
        if (Settings.gameState != GameState.Play && Settings.gameState != GameState.BallSelection) return
        player.flingStart.setLocation(x, y)
        player.flingCurrent.setLocation(x, y)
        player.isFlingHeld = true
        player.flingReleaseDir = null
        player.flingReleaseBasePower = 0f
    }

    private fun endFling(player: Player, x: Float, y: Float) {
        if (!player.isFlingHeld) return
        player.flingCurrent.setLocation(x, y)
        player.isFlingHeld = false
        if (Settings.gameState != GameState.Play && Settings.gameState != GameState.BallSelection) return
        val dx = player.flingStart.x - x
        val dy = player.flingStart.y - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val maxDist = Settings.screenRatio * 5f
        val clipped = kotlin.math.min(dist, maxDist)
        val powerRange = Settings.sweetSpotMax - Settings.chargeStart
        val basePower = Settings.chargeStart + (clipped / maxDist) * powerRange
        val dir = if (dist > 0f) Point(dx / dist, dy / dist) else Point(0f, 0f)
        player.flingReleaseDir = dir
        player.flingReleaseBasePower = basePower
    }

    private fun updateFlingCurrent(player: Player, x: Float, y: Float) {
        if (player.isFlingHeld) {
            player.flingCurrent.setLocation(x, y)
        }
    }

    private fun resetPlayerStates(highPlayer: Player, lowPlayer: Player) {
        highPlayer.motion = MotionStates.Free
        highPlayer.touch = TouchState.Ready
        lowPlayer.motion = MotionStates.Free
        lowPlayer.touch = TouchState.Ready
        highPlayer.clearPower()
        lowPlayer.clearPower()

        highPlayer.setPuckStroke(PaintBucket.highBallStroke.toArgb())
        lowPlayer.setPuckStroke(PaintBucket.lowBallStroke.toArgb())
    }

    private fun applyHitStun(struckPlayer: Player, impactPower: Float) {
        val range = Settings.hitStunMaxImpactPower - Settings.hitStunMinImpactPower
        val t = ((impactPower - Settings.hitStunMinImpactPower) / range).coerceIn(0f, 1f)
        val durationSeconds = t * Settings.hitStunMaxSeconds
        if (durationSeconds < Settings.hitStunMinSeconds) return
        val fps = 1000f / Settings.refreshRate
        val frames = (durationSeconds * fps).roundToInt()
        struckPlayer.hitStunFramesRemaining = frames
        struckPlayer.hitStunTotalFrames = frames
    }

    fun setPuckColor(player: Player, fill: Int, stroke: Int) {
        player.puck.setFill(fill)
        player.puck.setStroke(stroke)
    }

    fun resetTails(highPlayer: Player, lowPlayer: Player) {
        setPuckColor(highPlayer, PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb())
        setPuckColor(lowPlayer, PaintBucket.lowBallFill.toArgb(), PaintBucket.lowBallStroke.toArgb())
    }

    fun checkDanger() {
        updateDanger(highPlayer, lowPlayer, isHigh = true)
        updateDanger(lowPlayer, highPlayer, isHigh = false)
    }

    private fun updateDanger(player: Player, opponent: Player, isHigh: Boolean) {
        val threshold = player.puck.renderer.radius * 5f
        val px = player.px
        val py = player.py

        var nearestDist = Float.MAX_VALUE
        var nearestX = px
        var nearestY = py

        // Nearest of the four walls — left/right verticals (at py) and top/bottom goal lines (at px).
        // Evaluated inline so this per-frame hot path never allocates the array-of-pairs it used to.
        var d = hypot(px - Settings.screenLeft, 0f)
        if (d < nearestDist) { nearestDist = d; nearestX = Settings.screenLeft;  nearestY = py }
        d = hypot(px - Settings.screenRight, 0f)
        if (d < nearestDist) { nearestDist = d; nearestX = Settings.screenRight; nearestY = py }
        d = hypot(0f, py - Settings.topGoalBottom)
        if (d < nearestDist) { nearestDist = d; nearestX = px; nearestY = Settings.topGoalBottom }
        d = hypot(0f, py - Settings.bottomGoalTop)
        if (d < nearestDist) { nearestDist = d; nearestX = px; nearestY = Settings.bottomGoalTop }

        val od = hypot(px - opponent.px, py - opponent.py)
        if (od < nearestDist) { nearestDist = od; nearestX = opponent.px; nearestY = opponent.py }

        val inDanger = nearestDist < threshold
        val wasInDanger = if (isHigh) highInDanger else lowInDanger
        if (isHigh) highInDanger = inDanger else lowInDanger = inDanger

        if (inDanger && !wasInDanger) {
            player.puck.renderer.skin?.onDangerNear(nearestX, nearestY)
        } else if (!inDanger && wasInDanger) {
            player.puck.renderer.skin?.onDangerClear()
        }
    }
}

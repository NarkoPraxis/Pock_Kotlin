package utility

import enums.*
import gameobjects.Player
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import shapes.BallSelectionPopup
import gameobjects.BotBrain
import androidx.compose.ui.graphics.toArgb
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.roundToInt
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

    var tempGameState = GameState.BallSelection
    var leaving = false
    var canCollide = true

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
        timerSecondsRemaining = Settings.timeLimitMinutes * 60

        highBallPopup.open()
        lowBallPopup.open()

        collisionBonus = 10 + 10f * Settings.balanceRatio

        isInitialized = true
    }

    fun reset() {
        tempGameState = GameState.Play
    }

    fun updateTimer() {
        val mark = timerMark ?: return
        if (timerExpired) return
        val limitMs = Settings.timeLimitMinutes.toLong() * 60_000L
        val remainingMs = (limitMs - mark.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
        timerSecondsRemaining = (remainingMs / 1000L).toInt()
        if (remainingMs == 0L) {
            timerExpired = true
            timerHidden = true
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

    fun updateCanScoreWall() {
        if (!this::highPlayer.isInitialized) return
        val delta = 0.1f
        Settings.canScoreWallProgress = if (Settings.canScore)
            (Settings.canScoreWallProgress + delta).coerceAtMost(1f)
        else
            (Settings.canScoreWallProgress - delta).coerceAtLeast(0f)
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
        return if (highScored && !lowScored) {
            Result.High
        } else if (lowScored && !highScored) {
            Result.Low
        } else if (highScored && lowScored) {
            Result.Both
        } else {
            Result.Neither
        }
    }

    private fun checkScored(winner: Player, loser: Player) : Boolean {
        if ( loser.shielded) return false
        if (Settings.canScore && (loser.py < Settings.topGoalBottom + loser.pRadius || loser.py > Settings.bottomGoalTop - loser.pRadius)) {
            val highGoal = loser.py < Settings.topGoalBottom + loser.pRadius
            winner.score()
            loser.clearPower()
            winner.clearPower()
            if (Settings.scoreFlashEnabled) {
                Settings.scoreFlashAlpha = 200f
                Settings.scoreFlashColor = winner.puckFillColor
            }
            if (Settings.scorePopEnabled) {
                if (winner.isHigh) {
                    Settings.highScorePopTicker.reset()
                } else {
                    Settings.lowScorePopTicker.reset()
                }
            }
            setPuckColor(loser, PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb())
            setPuckColor(winner, PaintBucket.lowBallFill.toArgb(), PaintBucket.lowBallStroke.toArgb())
            Settings.gameState = GameState.Scored
            Settings.canScore = false
            Settings.canScoreWallProgress = 0f
            botBrain?.reset()
            Sounds.playScoreSound(loser.py)
            Effects.clearCollisionEffects()
            Effects.signalScored()
            val goalPoint = Point(loser.px, if (highGoal) Settings.topGoalBottom else Settings.bottomGoalTop)
            loser.puck.renderer.skin.onUsedToScore(loser.puckFillColor, goalPoint, highGoal)
            winner.puck.renderer.skin.onScored()
            return true
        }
        return false
    }

    fun scored() {
        lowPlayer.disableEffects = true
        highPlayer.disableEffects = true
        val lowIsReady = lowPlayer.moveTowardPoint(lowPlayer.resetLocation)
        val highIsReady = highPlayer.moveTowardPoint(highPlayer.resetLocation)
        if (lowIsReady && highIsReady) {
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
        }
        resetPlayerStates(highPlayer, lowPlayer)
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
            lowPlayer.disableEffects = false
            highPlayer.disableEffects = false
            Settings.canScore = false
            Settings.canScoreWallProgress = 0f
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
            timerSecondsRemaining = Settings.timeLimitMinutes * 60
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
            val collisionPoint = lowPlayer.puck.intersectionPoint(highPlayer.puck)

            highPlayer.launchFrom = Point(highPlayer.px, highPlayer.py)
            lowPlayer.launchFrom = Point(lowPlayer.px, lowPlayer.py)

            val lowPower = lowPlayer.power
            val highPower = highPlayer.power

            highPlayer.bonusCountdown = 0f
            lowPlayer.bonusCountdown = 0f
            val intersection = lowPlayer.puck.intersectionPoint(highPlayer.puck)

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
        val hadLaunchPower = player.puck.launch.hasPower
        val hadMovementPower = player.puck.movement.hasPower
        if (player.applyForces()) {
            Effects.addWallCollisionEffect(player.bounceDirection, player.puckFillColor, player.puck)
            if (!player.shielded) player.puck.renderer.skin.onHit()
        }
        if (hadLaunchPower && !player.puck.launch.hasPower) {
            GameEvents.cantScore.emit(Unit)
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
    // All pointer-to-player assignment and lifetime tracking lives in GameScreen.

    fun onPointerDown(x: Float, y: Float, playerId: Int) {
        if (Settings.screenWidth == 0f) return
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
        if (interceptBallMenuMove(x, y, playerId)) return
        if (Settings.isSinglePlayer && playerId == 0) return
        updateFlingCurrent(if (playerId == 0) highPlayer else lowPlayer, x, y)
    }

    fun onPointerUp(x: Float, y: Float, playerId: Int) {
        if (Settings.screenWidth == 0f) return
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

        val wallPoints = arrayOf(
            floatArrayOf(Settings.screenLeft,  py),
            floatArrayOf(Settings.screenRight, py),
            floatArrayOf(px, Settings.topGoalBottom),
            floatArrayOf(px, Settings.bottomGoalTop)
        )
        for (wp in wallPoints) {
            val d = hypot(px - wp[0], py - wp[1])
            if (d < nearestDist) { nearestDist = d; nearestX = wp[0]; nearestY = wp[1] }
        }

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

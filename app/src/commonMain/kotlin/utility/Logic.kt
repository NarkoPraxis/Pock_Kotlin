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

    var highTouchedFirst = false

    var tempGameState = GameState.BallSelection
    var leaving = false
    var canCollide = true

    /** Called instead of doOnSizeChange when running under Compose (no GameView). */
    var composeReinitCallback: (() -> Unit)? = null

    private var highPopupDragPointerId: Int = -1
    private var lowPopupDragPointerId: Int = -1

    var winnerSoundHasBeenPlayed = false
    private var victoryCelebrationFrame = 0

    private var timerMark: TimeMark? = null
    var timerStarted = false
    var timerExpired = false
    var timerHidden = false
    var timerSecondsRemaining = 0

    private var highInDanger = false
    private var lowInDanger  = false

    var highStartX = 0f
    var lowStartX = 0f

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
        highPopupDragPointerId = -1
        lowPopupDragPointerId = -1
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
        if (Settings.highBallType == BallType.Random) Settings.highRandomRoll = BallStyleFactory.rollRandom()
        if (Settings.lowBallType == BallType.Random) Settings.lowRandomRoll = BallStyleFactory.rollRandom()
        highPlayer = Player(
            Puck(Settings.ballRadius, highStartX, Settings.middleY, BallStyleFactory.buildRenderer(Settings.highBallType, ColorTheme.getTheme(true), Settings.highRandomRoll)),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight / 5, PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb()),
            true
        )
        lowPlayer = Player(
            Puck(Settings.ballRadius, lowStartX, Settings.middleY, BallStyleFactory.buildRenderer(Settings.lowBallType, ColorTheme.getTheme(false), Settings.lowRandomRoll)),
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
            highPopupDragPointerId = -1
            lowPopupDragPointerId = -1
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
        if (loser.shielded) return false
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
            lowPlayer.lockedPointerId = -1
            highPlayer.lockedPointerId = -1
            lowPlayer.shielded = false
            highPlayer.shielded = false
            lowPlayer.inertLocked = false
            highPlayer.inertLocked = false
            lowPlayer.fatigueInertLocked = false
            highPlayer.fatigueInertLocked = false
            lowPlayer.clearCharge()
            highPlayer.clearCharge()
            Settings.gameOver = false
            highPopupDragPointerId = -1
            lowPopupDragPointerId = -1
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
                highPlayer.launch(Force(-direction, 10f + Settings.sweetSpotMax))
                lowPlayer.launch(Force(direction, 10f + Settings.sweetSpotMax))
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
            } else if (highPlayer.reappearing) {
                Sounds.playTeleportFinish(highPlayer.px)
                lowPlayer.launch(Force(direction, 10f + Settings.sweetSpotMax))
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
            } else if (lowPlayer.reappearing) {
                Sounds.playTeleportFinish(lowPlayer.px)
                highPlayer.launch(Force(-direction, 10f + Settings.sweetSpotMax))
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
            } else if (highPlayer.shielded && !lowPlayer.shielded) {
                Sounds.playChargeCollision(collisionPoint.x)
                val bonusPower = if (lowPlayer.isCharging) 20f else 10f
                lowPlayer.launch(Force(direction, bonusPower + highPlayer.power))
                highPlayer.launch(Force(-direction, Settings.minLaunchPower))
                lowPlayer.inertLocked = true
                applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                lowPlayer.puck.renderer.skin.onHit()
            } else if (lowPlayer.shielded && !highPlayer.shielded) {
                Sounds.playChargeCollision(collisionPoint.x)
                val bonusPower = if (highPlayer.isCharging) 20f else 10f
                highPlayer.launch(Force(-direction, bonusPower + lowPlayer.power))
                lowPlayer.launch(Force(direction, Settings.minLaunchPower))
                highPlayer.inertLocked = true
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                highPlayer.puck.renderer.skin.onHit()
            } else if (lowPlayer.shielded && highPlayer.shielded) {
                Sounds.playDoubleChargeCollision(collisionPoint.x)
                highPlayer.launch(Force(-direction, 10f + lowPower))
                lowPlayer.launch(Force(direction, 10f + highPower))
                highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
            } else if (highPlayer.isCharging && lowPlayer.isCharging) {
                Sounds.playDoubleChargeCollision(collisionPoint.x)
                highPlayer.launch(Force(-direction, 10f + lowPower))
                lowPlayer.launch(Force(direction, 10f + highPower))
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
                highPlayer.launch(Force(-direction, 10f + lowPower))
                lowPlayer.launch(Force(direction, if (highPower < Settings.minLaunchPower) Settings.minLaunchPower else highPower))
                highPlayer.inertLocked = true
                highPlayer.clearCharge()
                applyHitStun(highPlayer, highPlayer.puck.impactPower)
                highPlayer.puck.renderer.skin.onHit()
                lowPlayer.puck.renderer.skin.onCollisionWin(intersection, lowPlayer.movementSpeed)
            } else if (lowPlayer.isCharging) {
                Sounds.playChargeCollision(collisionPoint.x)
                lowPlayer.launch(Force(direction, 10f + highPower))
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

                if (highPlayer.power > lowPlayer.power) {
                    Sounds.playLowPlayerSound(collisionPoint.x)
                } else {
                    Sounds.playHighPlayerSound(collisionPoint.x)
                }
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
            GameEvents.canScore.emit(Unit)
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

    fun onPointerDown(x: Float, y: Float, pointerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptBallMenuDown(x, y, pointerId)) return

        if (Settings.gameState == GameState.BallSelection) {
            val isHighSide = y < Settings.middleY
            if (isHighSide && !Settings.isSinglePlayer) {
                Drawing.cycleHighTip()
                if (highPlayer.lockedPointerId == -1) {
                    highPlayer.lockedPointerId = pointerId
                    startFling(highPlayer, x, y)
                    highPlayer.touch = TouchState.Down
                }
            } else if (!isHighSide) {
                Drawing.cycleLowTip()
                if (lowPlayer.lockedPointerId == -1) {
                    lowPlayer.lockedPointerId = pointerId
                    startFling(lowPlayer, x, y)
                    lowPlayer.touch = TouchState.Down
                }
            }
            return
        }

        val isHighSide = y < Settings.middleY
        if (isHighSide && !Settings.isSinglePlayer && highPlayer.lockedPointerId == -1) {
            if (lowPlayer.lockedPointerId == -1) highTouchedFirst = true
            highPlayer.lockedPointerId = pointerId
            startFling(highPlayer, x, y)
            highPlayer.touch = TouchState.Down
            Sounds.playHighPlayerSound(highPlayer.fx)
        } else if (!isHighSide && lowPlayer.lockedPointerId == -1) {
            if (highPlayer.lockedPointerId != -1) highTouchedFirst = false
            lowPlayer.lockedPointerId = pointerId
            startFling(lowPlayer, x, y)
            lowPlayer.touch = TouchState.Down
            Sounds.playLowPlayerSound(lowPlayer.fx)
        }
    }

    fun onPointerMove(x: Float, y: Float, pointerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptBallMenuMove(x, y, pointerId)) return
        if (pointerId == highPlayer.lockedPointerId) updateFlingCurrent(highPlayer, x, y)
        if (pointerId == lowPlayer.lockedPointerId) updateFlingCurrent(lowPlayer, x, y)
    }

    fun onPointerUp(x: Float, y: Float, pointerId: Int) {
        if (Settings.screenWidth == 0f) return
        if (interceptBallMenuUp(x, y, pointerId)) return

        if (Settings.gameState == GameState.BallSelection) {
            if (pointerId == highPlayer.lockedPointerId) {
                endFling(highPlayer, x, y)
                highPlayer.lockedPointerId = -1
                highPlayer.touch = TouchState.Ready
                highPlayer.shouldReleaseCharge = true
            } else if (pointerId == lowPlayer.lockedPointerId) {
                endFling(lowPlayer, x, y)
                lowPlayer.lockedPointerId = -1
                lowPlayer.touch = TouchState.Ready
                lowPlayer.shouldReleaseCharge = true
            }
            return
        }

        if (pointerId == highPlayer.lockedPointerId) {
            Sounds.playHighPlayerSound(highPlayer.fx)
            endFling(highPlayer, x, y)
            highPlayer.lockedPointerId = -1
            if (Settings.gameState == GameState.Play) highPlayer.shouldReleaseCharge = true
            highPlayer.touch = TouchState.Ready
        } else if (pointerId == lowPlayer.lockedPointerId) {
            Sounds.playLowPlayerSound(lowPlayer.fx)
            endFling(lowPlayer, x, y)
            lowPlayer.lockedPointerId = -1
            if (Settings.gameState == GameState.Play) lowPlayer.shouldReleaseCharge = true
            lowPlayer.touch = TouchState.Ready
        }
    }

    private fun interceptBallMenuDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        if (y < Settings.middleY) {
            if (highBallPopup.isOpen && highBallPopup.hitTest(x, y)) {
                if (highPopupDragPointerId == -1) {
                    highPopupDragPointerId = pointerId
                    highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_DOWN, x, y)
                }
                Drawing.cycleHighTip()
                return true
            }
        } else {
            if (lowBallPopup.isOpen && lowBallPopup.hitTest(x, y)) {
                if (lowPopupDragPointerId == -1) {
                    lowPopupDragPointerId = pointerId
                    lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_DOWN, x, y)
                }
                Drawing.cycleLowTip()
                return true
            }
        }
        return false
    }

    private fun interceptBallMenuMove(x: Float, y: Float, pointerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        var consumed = false
        if (highBallPopup.isOpen && pointerId == highPopupDragPointerId) {
            highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_MOVE, x, y)
            consumed = true
        }
        if (lowBallPopup.isOpen && pointerId == lowPopupDragPointerId) {
            lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_MOVE, x, y)
            consumed = true
        }
        return consumed
    }

    private fun interceptBallMenuUp(x: Float, y: Float, pointerId: Int): Boolean {
        if (Settings.gameState != GameState.BallSelection) return false
        var consumed = false
        if (pointerId == highPopupDragPointerId) {
            highPopupDragPointerId = -1
            if (highBallPopup.isOpen) highBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_UP, x, y)
            consumed = true
        }
        if (pointerId == lowPopupDragPointerId) {
            lowPopupDragPointerId = -1
            if (lowBallPopup.isOpen) lowBallPopup.handleTouchEvent(BallSelectionPopup.ACTION_UP, x, y)
            consumed = true
        }
        return consumed
    }

    fun closeBallPopups() {
        highBallPopup.close()
        lowBallPopup.close()
        highPopupDragPointerId = -1
        lowPopupDragPointerId = -1
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

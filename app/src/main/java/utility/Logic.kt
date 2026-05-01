package utility

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.puck.GameView
import com.example.puck.MainActivity
import com.example.puck.SettingsActivity
import enums.*
import gameobjects.PauseMenu
import gameobjects.Player
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.reflect.KFunction5

object Logic {

    lateinit var highPlayer: Player
    lateinit var lowPlayer: Player

    lateinit var pauseMenu: PauseMenu
    lateinit var victoryTicker: Ticker

    val highBallPopup = shapes.BallSelectionPopup(isHigh = true)
    val lowBallPopup = shapes.BallSelectionPopup(isHigh = false)

    lateinit var activity: AppCompatActivity

    var menuSelection = MenuSelection.none

    var countPauseTouches = 0
    var highTouchedFirst = false

    var tempGameState = GameState.BallSelection
    var leaving = false
    var canCollide = true;

    lateinit var gameView: GameView

    // Tracks which pointer is driving each popup's drag so multi-touch routes correctly
    private var highPopupDragPointerId: Int = -1
    private var lowPopupDragPointerId: Int = -1

    var winnerSoundHasBeenPlayed = false

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
        Settings.tailLength = Storage.tailLength
        Settings.maxBonusTickerTime = Storage.maxBonusTickerTime
        Settings.launchBonus = Storage.launchBonus
        Settings.chargeIncreaseRate = Storage.chargeSpeed
        Settings.refreshRate = Storage.gameSpeed
        Settings.pointsToWin = Storage.loadPointsToWin()
        Settings.highBallType = Storage.loadHighBallType(Settings.highBallType)
        Settings.lowBallType = Storage.loadLowBallType(Settings.lowBallType)
        Settings.unlockProgress = Storage.unlockProgress
        Settings.highPlayerArrow = Storage.highPlayerArrow
        Settings.lowPlayerArrow = Storage.lowPlayerArrow
        Settings.highPlayerChargeFill = Storage.highPlayerChargeFill
        Settings.lowPlayerChargeFill = Storage.lowPlayerChargeFill
        Settings.scoreOffsetHigh = Storage.scoreOffsetHigh.toFloat()
        Settings.scoreOffsetLow = Storage.scoreOffsetLow.toFloat()

        Settings.screenWidth = width.toFloat()
        Settings.screenHeight = height.toFloat()
        Settings.middleX = Settings.screenWidth / 2f
        Settings.middleY = Settings.screenHeight / 2f
        Settings.screenRatio = if(Settings.screenWidth < Settings.screenHeight) Settings.screenWidth / 20 else Settings.screenHeight / 20

        val ballSizeSettings =  when (Storage.ballSize) {
            "small" -> .5f
            "default" -> 1f
            "large" -> 1.5f
            else -> 1f
        }

        Settings.ballRadius = Settings.screenRatio * ballSizeSettings

        Settings.maxPuckSpeed = Settings.screenRatio * (5f/8f)
        Settings.maxPuckLaunchSpeed = Settings.screenRatio * (6f/8f)
        Settings.minPuckSpeed = Settings.screenRatio * (1f/8f)
        Settings.shortParticleSide = Settings.screenRatio / 3f
        Settings.longParticleSide = Settings.screenRatio - Settings.shortParticleSide
        Settings.screenLeft = Settings.shortParticleSide
        Settings.screenRight = Settings.screenWidth - Settings.shortParticleSide
        Settings.screenTop = Settings.shortParticleSide
        Settings.screenBottom = Settings.screenHeight - Settings.shortParticleSide
        Settings.strokeWidth = Settings.screenRatio / 4

        Settings.topGoalBottom = Settings.screenRatio * Settings.scoreZoneHeight
        Settings.bottomGoalTop = Settings.screenHeight - Settings.topGoalBottom

        highStartX = Settings.screenWidth / 4f
        lowStartX = Settings.screenWidth * (3 / 4f)
    }

    fun initialize(activity: AppCompatActivity, gameView: GameView) {
        this.activity = activity
        this.gameView = gameView
        leaving = false
        Settings.gameState = GameState.BallSelection
        Settings.pauseGame = false
        Settings.gameOver = false
        Settings.playerPaused = false
        highPopupDragPointerId = -1
        lowPopupDragPointerId = -1
        Drawing.resetTipIndices()
        applyBallStyles()
        registerPhaseCallbacks()

        highPlayer.resetLocation = Point(highStartX, Settings.middleY)
        lowPlayer.resetLocation = Point(lowStartX, Settings.middleY)

        victoryTicker = Ticker(Settings.victoryThreshold)

        pauseMenu = PauseMenu(this.activity)

        highBallPopup.open()
        lowBallPopup.open()

    }

    fun reset() {
        countPauseTouches = 0
        tempGameState = GameState.Play
    }

    fun applyBallStyles() {
        highPlayer = Player(
            Puck(Settings.ballRadius, highStartX, Settings.middleY, BallStyleFactory.buildRenderer(Settings.highBallType, ColorTheme.getTheme(true))),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight / 5, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor),
            true
        )
        lowPlayer = Player(
            Puck(Settings.ballRadius, lowStartX, Settings.middleY, BallStyleFactory.buildRenderer(Settings.lowBallType, ColorTheme.getTheme(false))),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight - Settings.screenHeight / 5, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            false
        )
    }


    private fun interceptBallMenu(event: MotionEvent?, motionEvent: Int?): Boolean {
        if (event == null) return false
        if (Settings.gameState != GameState.BallSelection) return false
        val action = motionEvent ?: return false
        val maskedAction = action and MotionEvent.ACTION_MASK

        when (maskedAction) {
            MotionEvent.ACTION_MOVE -> {
                // Drive each open popup from its own tracked pointer so both players can scroll simultaneously
                var consumed = false
                if (highBallPopup.isOpen && highPopupDragPointerId >= 0) {
                    val pIdx = event.findPointerIndex(highPopupDragPointerId)
                    if (pIdx >= 0 && highBallPopup.handleTouchEvent(action, event.getX(pIdx), event.getY(pIdx))) consumed = true
                }
                if (lowBallPopup.isOpen && lowPopupDragPointerId >= 0) {
                    val pIdx = event.findPointerIndex(lowPopupDragPointerId)
                    if (pIdx >= 0 && lowBallPopup.handleTouchEvent(action, event.getX(pIdx), event.getY(pIdx))) consumed = true
                }
                return consumed
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Use the action pointer's coordinates (not pointer 0) so multi-touch routes to the correct side
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val pid = event.getPointerId(idx)
                if (y < Settings.middleY) {
                    if (highBallPopup.isOpen && highBallPopup.hitTest(x, y)) {
                        if (highPopupDragPointerId == -1) {
                            highPopupDragPointerId = pid
                            highBallPopup.handleTouchEvent(action, x, y)
                        }
                        Drawing.cycleHighTip()
                        return true
                    }
                } else {
                    if (lowBallPopup.isOpen && lowBallPopup.hitTest(x, y)) {
                        if (lowPopupDragPointerId == -1) {
                            lowPopupDragPointerId = pid
                            lowBallPopup.handleTouchEvent(action, x, y)
                        }
                        Drawing.cycleLowTip()
                        return true
                    }
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)

                if (pid == highPopupDragPointerId) {
                    highPopupDragPointerId = -1
                    if (highBallPopup.isOpen) highBallPopup.handleTouchEvent(action, x, y)
                    return true
                }
                if (pid == lowPopupDragPointerId) {
                    lowPopupDragPointerId = -1
                    if (lowBallPopup.isOpen) lowBallPopup.handleTouchEvent(action, x, y)
                    return true
                }
                // Untracked pointer lifting in the popup area — consume only if the lift lands inside the carousel
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (highBallPopup.isOpen) highBallPopup.handleTouchEvent(action, event.x, event.y)
                if (lowBallPopup.isOpen) lowBallPopup.handleTouchEvent(action, event.x, event.y)
                highPopupDragPointerId = -1
                lowPopupDragPointerId = -1
                return false
            }
        }
        return false
    }



    fun checkBallSelectionEnd() {
        if (highPlayer.charge > Settings.chargeStart && lowPlayer.charge > Settings.chargeStart) {
            highBallPopup.isOpen = false
            lowBallPopup.isOpen = false
            highPopupDragPointerId = -1
            lowPopupDragPointerId = -1
            canCollide = true
            Settings.gameState = GameState.Play
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
        highPlayer.puck.renderer.effect.unregisterAllPhaseCallbacks()
        lowPlayer.puck.renderer.effect.unregisterAllPhaseCallbacks()
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

    private fun routeBallSelectionFling(event: MotionEvent?, motionEvent: Int?) {
        if (event == null) return
        val masked = motionEvent?.and(MotionEvent.ACTION_MASK) ?: return
        val actionIndex = event.actionIndex
        val actionX = event.getX(actionIndex)
        val actionY = event.getY(actionIndex)
        val actionPointerId = event.getPointerId(actionIndex)

        when (masked) {
            MotionEvent.ACTION_DOWN -> {
                if (actionY < Settings.middleY) {
                    Drawing.cycleHighTip()
                    highTouchedFirst = true
                    if (highPlayer.lockedPointerId == -1) highPlayer.lockedPointerId = actionPointerId
                    startFling(highPlayer, actionX, actionY)
                    setSingleTouch(motionEvent, highPlayer)
                } else {
                    Drawing.cycleLowTip()
                    highTouchedFirst = false
                    if (lowPlayer.lockedPointerId == -1) lowPlayer.lockedPointerId = actionPointerId
                    startFling(lowPlayer, actionX, actionY)
                    setSingleTouch(motionEvent, lowPlayer)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (actionY < Settings.middleY && highPlayer.lockedPointerId == -1) {
                    Drawing.cycleHighTip()
                    highPlayer.lockedPointerId = actionPointerId
                    startFling(highPlayer, actionX, actionY)
                    highPlayer.touch = TouchState.Down
                } else if (actionY >= Settings.middleY && lowPlayer.lockedPointerId == -1) {
                    Drawing.cycleLowTip()
                    lowPlayer.lockedPointerId = actionPointerId
                    startFling(lowPlayer, actionX, actionY)
                    lowPlayer.touch = TouchState.Down
                }
            }
            MotionEvent.ACTION_UP -> {
                if (actionPointerId == highPlayer.lockedPointerId) {
                    endFling(highPlayer, actionX, actionY)
                    highPlayer.lockedPointerId = -1
                    setSingleTouch(motionEvent, highPlayer)
                } else if (actionPointerId == lowPlayer.lockedPointerId) {
                    endFling(lowPlayer, actionX, actionY)
                    lowPlayer.lockedPointerId = -1
                    setSingleTouch(motionEvent, lowPlayer)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (actionPointerId == highPlayer.lockedPointerId) {
                    endFling(highPlayer, actionX, actionY)
                    highPlayer.lockedPointerId = -1
                    highPlayer.touch = TouchState.Ready
                    highPlayer.shouldReleaseCharge = true
                } else if (actionPointerId == lowPlayer.lockedPointerId) {
                    endFling(lowPlayer, actionX, actionY)
                    lowPlayer.lockedPointerId = -1
                    lowPlayer.touch = TouchState.Ready
                    lowPlayer.shouldReleaseCharge = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val highLockedIdx = if (highPlayer.lockedPointerId != -1)
                    event.findPointerIndex(highPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
                else -1
                val lowLockedIdx = if (lowPlayer.lockedPointerId != -1)
                    event.findPointerIndex(lowPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
                else -1
                if (highLockedIdx >= 0) updateFlingCurrent(highPlayer, event.getX(highLockedIdx), event.getY(highLockedIdx))
                if (lowLockedIdx >= 0) updateFlingCurrent(lowPlayer, event.getX(lowLockedIdx), event.getY(lowLockedIdx))
            }
        }
    }

    fun gameOver() {
        lowPlayer.disableEffects = true
        highPlayer.disableEffects = true
        if (!winnerSoundHasBeenPlayed) {
            winnerSoundHasBeenPlayed = true
            Sounds.playWeHaveAWinner()
            GameEvents.gameOver.emit(Unit)
        }
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
            Settings.pauseGame = false
            Settings.gameOver = false
            highPopupDragPointerId = -1
            lowPopupDragPointerId = -1
            GameEvents.gameReset.emit(Unit)
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

    fun calculateCollision() : Boolean {
        if (highPlayer.pucksIntersect(lowPlayer)) {
            if (canCollide) {
                val direction = highPlayer.puck.directionTo(lowPlayer.puck)
                val collisionPoint = lowPlayer.puck.intersectionPoint(highPlayer.puck)

                highPlayer.launchFrom = Point(highPlayer.px, highPlayer.py)
                lowPlayer.launchFrom = Point(lowPlayer.px, lowPlayer.py)

                highPlayer.bonusCountdown = 0f
                lowPlayer.bonusCountdown = 0f
                val intersection = lowPlayer.puck.intersectionPoint(highPlayer.puck)

                if (highPlayer.reappearing && lowPlayer.reappearing) {
                    Sounds.playTeleportFinish(highPlayer.px)
                    Sounds.playTeleportFinish(lowPlayer.px)
                    highPlayer.launch(
                        Force(
                            -direction,
                            Settings.launchBonus + Settings.sweetSpotMax
                        )
                    )
                    lowPlayer.launch(Force(direction, Settings.launchBonus + Settings.sweetSpotMax))
                    applyHitStun(highPlayer, highPlayer.puck.impactPower)
                    applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                } else if (highPlayer.reappearing) {
                    Sounds.playTeleportFinish(highPlayer.px)
                    lowPlayer.launch(Force(direction, Settings.launchBonus + Settings.sweetSpotMax))
                    applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                } else if (lowPlayer.reappearing) {
                    Sounds.playTeleportFinish(lowPlayer.px)
                    highPlayer.launch(
                        Force(
                            -direction,
                            Settings.launchBonus + Settings.sweetSpotMax
                        )
                    )
                    applyHitStun(highPlayer, highPlayer.puck.impactPower)
                } else if (highPlayer.shielded && !lowPlayer.shielded) {
                    Sounds.playChargeCollision(collisionPoint.x)
                    lowPlayer.launch(Force(direction, Settings.launchBonus + highPlayer.power))
                    highPlayer.launch(Force(-direction, Settings.minLaunchPower))
                    lowPlayer.inertLocked = true
                    applyHitStun(lowPlayer, lowPlayer.puck.impactPower)
                    highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                    lowPlayer.puck.renderer.skin.onHit()
                } else if (lowPlayer.shielded && !highPlayer.shielded) {
                    Sounds.playChargeCollision(collisionPoint.x)
                    highPlayer.launch(Force(-direction, Settings.launchBonus + lowPlayer.power))
                    lowPlayer.launch(Force(direction, Settings.minLaunchPower))
                    highPlayer.inertLocked = true
                    applyHitStun(highPlayer, highPlayer.puck.impactPower)
                    lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                    highPlayer.puck.renderer.skin.onHit()
                } else if (lowPlayer.shielded && highPlayer.shielded) {
                    Sounds.playDoubleChargeCollision(collisionPoint.x)
                    val lowPower = lowPlayer.power
                    val highPower = highPlayer.power
                    highPlayer.launch(Force(-direction, Settings.launchBonus + lowPower))
                    lowPlayer.launch(Force(direction, Settings.launchBonus + highPower))
                    // both shielded — no hit-stun for either
                    highPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                    lowPlayer.puck.renderer.skin.onShieldedCollision(intersection)
                } else {
                    val highPower = highPlayer.power
                    val lowPower = lowPlayer.power
                    highPlayer.launch(
                        Force(
                            -direction,
                            if (lowPower < Settings.minLaunchPower) Settings.minLaunchPower else lowPower
                        )
                    )
                    lowPlayer.launch(
                        Force(
                            direction,
                            if (highPower < Settings.minLaunchPower) Settings.minLaunchPower else highPower
                        )
                    )
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
                canCollide = false
                return true
            }
        }
        else if (!canCollide) {
            canCollide = true
        }
        return false
    }

    fun checkPauseGame(pointerCount: Int, y1: Float, y2: Float) {
        if ((Settings.gameState == GameState.Play || Settings.gameState == GameState.BallSelection) && !Tutorial.showing) {
            if (pointerCount == 2 && (y1 < Settings.topGoalBottom && y2 < Settings.topGoalBottom) || (y1 > Settings.bottomGoalTop && y2 > Settings.bottomGoalTop)) {
                tempGameState = Settings.gameState
                Settings.pauseGame = true
                Settings.playerPaused = true
            }
        }
    }

    fun checkUnpauseGame(y1: Float, y2: Float) {
        if (!Tutorial.showing && (Settings.topGoalBottom < y1 && y1 < Settings.bottomGoalTop) || Settings.topGoalBottom < y2 && y2 < Settings.bottomGoalTop) {
            Settings.gameState = tempGameState
            Settings.pauseGame = false
            countPauseTouches = 0
        }
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
        if ( player.shouldReleaseCharge) {
            gotBonus = player.releaseCharge()
        }
        val hadLaunchPower = player.puck.launch.hasPower
        val hadMovementPower = player.puck.movement.hasPower
        if(player.applyForces()) {
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


    fun assignTouchState(highTouchedFirst: Boolean, motionEvent: Int?) {
        if (highTouchedFirst) {
            if (lowPlayer.notLocked()) {
                when (motionEvent) {
                    MotionEvent.ACTION_POINTER_2_DOWN -> setMultiTouch(TouchState.Down,lowPlayer)
                    MotionEvent.ACTION_POINTER_2_UP -> setMultiTouch(TouchState.Up, lowPlayer)
                }
            }
            if (highPlayer.notLocked()) {
                when (motionEvent) {
                    MotionEvent.ACTION_POINTER_DOWN -> setMultiTouch( TouchState.Down,highPlayer)
                    MotionEvent.ACTION_POINTER_UP -> setMultiTouch(TouchState.Up, highPlayer)
                }
            }
        } else {
            if (highPlayer.notLocked()) {
                when (motionEvent) {
                    MotionEvent.ACTION_POINTER_2_DOWN -> setMultiTouch(TouchState.Down,  highPlayer  )
                    MotionEvent.ACTION_POINTER_2_UP -> setMultiTouch( TouchState.Up,  highPlayer)
                }
            }
            if (lowPlayer.notLocked()) {
                when (motionEvent) {
                    MotionEvent.ACTION_POINTER_DOWN -> setMultiTouch(TouchState.Down, lowPlayer)
                    MotionEvent.ACTION_POINTER_UP -> setMultiTouch(TouchState.Up, lowPlayer)
                }
            }
        }
    }

    private fun setMultiTouch(state: TouchState, player: Player) {
        if (player.touch == TouchState.Ready && player.motion == MotionStates.Free) {
            player.touch = TouchState.Down
            if (player.isHigh) {
                Sounds.playHighPlayerSound(player.fx)
            }
            else {
                Sounds.playLowPlayerSound(player.fx)
            }
        }
        if (state == TouchState.Up && player.touch == TouchState.Down ) {
            if (Settings.gameState == GameState.Play || Settings.gameState == GameState.BallSelection) {
                player.shouldReleaseCharge = true
            }
            player.touch = TouchState.Ready
            if (player.isHigh) {
                Sounds.playHighPlayerSound(player.fx)
            }
            else {
                Sounds.playLowPlayerSound(player.fx)
            }
        } else {
            player.touch = state
        }
    }

    private fun setSingleTouch(motionEvent: Int?, player: Player) {
        player.touch = if (player.motion == MotionStates.Free && player.touch == TouchState.Ready)
            TouchState.Down
        else
            player.touch

        val newState = when (motionEvent) {
            MotionEvent.ACTION_DOWN -> TouchState.Down
            MotionEvent.ACTION_UP -> TouchState.Up
            else -> player.touch
        }

        if (player.touch == TouchState.Down && newState == TouchState.Up) {
            player.touch = TouchState.Ready
            if (Settings.gameState == GameState.Play || Settings.gameState == GameState.BallSelection) {
                player.shouldReleaseCharge = true
            }
        }
        else {
            player.touch = newState
        }
    }



    fun updateCanScoreWall() {
        if (!this::highPlayer.isInitialized) return
        val delta = 0.4f
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
        } else if (lowScored && ! highScored) {
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
            val highGoal = loser.py < Settings.topGoalBottom  + loser.pRadius
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
            setPuckColor(loser, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)
            setPuckColor(winner, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor)
            Settings.gameState = GameState.Scored
            Settings.canScore = false
            Settings.canScoreWallProgress = 0f
            Sounds.playScoreSound(loser.py)
            Effects.clearCollisionEffects()
            Effects.signalScored()
            // Todo: refactor this so it's not "other" the effect spawned should match the puck entering the goal, not the other way around
            val goalPoint = Point(loser.px, if (highGoal) Settings.topGoalBottom else Settings.bottomGoalTop)
            loser.puck.renderer.skin.onScore(loser.puckFillColor, goalPoint, highGoal)
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
            Settings.gameState = if (!Settings.gameOver && (lowPlayer.score >= Settings.pointsToWin || highPlayer.score >= Settings.pointsToWin)) {
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
        // Only store release data in states where a launch should actually fire
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

    fun onTouchEvent(event: MotionEvent?, context: Context) {
        val motionEvent = event?.action
        var pointerCount = event?.pointerCount
        if (pointerCount == null) {
            pointerCount = 1
        }

        if (interceptBallMenu(event, motionEvent)) return

        if (Settings.gameState == GameState.BallSelection) {
            routeBallSelectionFling(event, motionEvent)
            return
        }

        if (pointerCount > 1) {
            val maskedAction = motionEvent?.and(MotionEvent.ACTION_MASK)
            val actionIndex = event!!.actionIndex
            val actionPointerId = event.getPointerId(actionIndex)
            val actionY = event.getY(actionIndex)
            val actionX = event.getX(actionIndex)

            if (maskedAction == MotionEvent.ACTION_POINTER_DOWN) {
                if (actionY < Settings.middleY && highPlayer.lockedPointerId == -1) {
                    highPlayer.lockedPointerId = actionPointerId
                    startFling(highPlayer, actionX, actionY)
                } else if (actionY > Settings.middleY && lowPlayer.lockedPointerId == -1) {
                    lowPlayer.lockedPointerId = actionPointerId
                    startFling(lowPlayer, actionX, actionY)
                }
            } else if (maskedAction == MotionEvent.ACTION_POINTER_UP) {
                if (actionPointerId == highPlayer.lockedPointerId) {
                    endFling(highPlayer, actionX, actionY)
                    highPlayer.lockedPointerId = -1
                } else if (actionPointerId == lowPlayer.lockedPointerId) {
                    endFling(lowPlayer, actionX, actionY)
                    lowPlayer.lockedPointerId = -1
                }
            }

            assignTouchState(highTouchedFirst, motionEvent)

            val highLockedIdx = if (highPlayer.lockedPointerId != -1)
                event.findPointerIndex(highPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
            else -1
            val lowLockedIdx = if (lowPlayer.lockedPointerId != -1)
                event.findPointerIndex(lowPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
            else -1

            if (highLockedIdx >= 0) updateFlingCurrent(highPlayer, event.getX(highLockedIdx), event.getY(highLockedIdx))
            if (lowLockedIdx >= 0) updateFlingCurrent(lowPlayer, event.getX(lowLockedIdx), event.getY(lowLockedIdx))
        }
        else {
            val x = event!!.x
            val y = event.y
            if (Settings.pauseGame) {
                if (Tutorial.showing && Settings.topGoalBottom < y && y < Settings.bottomGoalTop) {
                    Settings.gameState = tempGameState
                    Settings.pauseGame = false
                    countPauseTouches = 0
                }
                else if (y < Settings.topGoalBottom) { // top menu touched
                    if (x < Settings.screenWidth / 3f) { //left
                        menuSelection = MenuSelection.back
                    }
                    else if (x >  (2 * Settings.screenWidth) / 3f) { // right
                        menuSelection = MenuSelection.settings
                    }
                    else { //middle
                        menuSelection = MenuSelection.reset
                    }
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        countPauseTouches++
                        if (countPauseTouches == 2) {
                            menuCallback(context)
                            countPauseTouches = 0
                        }
                    }
                }
                else if (y > Settings.bottomGoalTop) {// bottom menu touched
                    if (x < Settings.screenWidth / 3) { //left
                        menuSelection = MenuSelection.settings
                    }
                    else if (x >  (2 * Settings.screenWidth) / 3) { // right
                        menuSelection = MenuSelection.back
                    }
                    else { //middle
                        menuSelection = MenuSelection.reset
                    }
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        countPauseTouches++
                        if (countPauseTouches == 2) {
                            menuCallback(context)
                            countPauseTouches = 0
                        }
                    }
                }
                else {
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        Settings.pauseGame = false
                        Settings.gameState = tempGameState
                        countPauseTouches = 0
                    }
                }
            }
            else {
                val pointerId = event.getPointerId(0)
                if (motionEvent == MotionEvent.ACTION_DOWN) {
                    // Ownership is fixed at touch-down based on starting y position
                    if (y > Settings.middleY) {
                        highTouchedFirst = false
                        if (lowPlayer.lockedPointerId == -1) lowPlayer.lockedPointerId = pointerId
                        startFling(lowPlayer, x, y)
                        setSingleTouch(motionEvent, lowPlayer)
                    } else {
                        highTouchedFirst = true
                        if (highPlayer.lockedPointerId == -1) highPlayer.lockedPointerId = pointerId
                        startFling(highPlayer, x, y)
                        setSingleTouch(motionEvent, highPlayer)
                    }
                } else {
                    // Route MOVE and UP by locked pointer ID so the arrow can cross the midline
                    if (pointerId == highPlayer.lockedPointerId) {
                        if (motionEvent == MotionEvent.ACTION_UP) {
                            Sounds.playHighPlayerSound(highPlayer.fx)
                            endFling(highPlayer, x, y)
                            highPlayer.lockedPointerId = -1
                        }
                        updateFlingCurrent(highPlayer, x, y)
                        if (highPlayer.notLocked()) setSingleTouch(motionEvent, highPlayer)
                    } else if (pointerId == lowPlayer.lockedPointerId) {
                        if (motionEvent == MotionEvent.ACTION_UP) {
                            Sounds.playLowPlayerSound(lowPlayer.fx)
                            endFling(lowPlayer, x, y)
                            lowPlayer.lockedPointerId = -1
                        }
                        updateFlingCurrent(lowPlayer, x, y)
                        if (lowPlayer.notLocked()) setSingleTouch(motionEvent, lowPlayer)
                    }
                }
            }
        }
    }

    fun menuCallback(context: Context) {
        when(menuSelection){
            MenuSelection.back -> {
                leaving = true
                Effects.clearPersistentEffects()
                Effects.collisions.clear()
                Sounds.playMenuAmbiance()
                val intent = Intent(context, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ContextCompat.startActivity(context, intent, Bundle())
                activity.finish()
            }
            MenuSelection.reset -> {
                resetGame(GameView::doOnSizeChange)
            }
            MenuSelection.settings -> {
                leaving = true
                Effects.clearPersistentEffects()
                Effects.collisions.clear()
                Sounds.playMenuAmbiance()
                val intent = Intent(context, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ContextCompat.startActivity(context, intent, Bundle())
                activity.finish()
            }
            else -> {}
        }
    }

    fun closeBallPopups() {
        highBallPopup.close()
        lowBallPopup.close()
        highPopupDragPointerId = -1
        lowPopupDragPointerId = -1
    }

    fun resetGame(sizeChanged: KFunction5<GameView, Int, Int, Int, Int, Unit>) {
        closeBallPopups()
        Settings.pointsToWin = Storage.loadPointsToWin()
        Settings.highBallType = Storage.loadHighBallType(Settings.highBallType)
        Settings.lowBallType = Storage.loadLowBallType(Settings.lowBallType)
        Settings.unlockProgress = Storage.unlockProgress
        Settings.highPlayerArrow = Storage.highPlayerArrow
        Settings.lowPlayerArrow = Storage.lowPlayerArrow
        Settings.highPlayerChargeFill = Storage.highPlayerChargeFill
        Settings.lowPlayerChargeFill = Storage.lowPlayerChargeFill
        Settings.pauseGame = false
        sizeChanged(gameView, Settings.screenWidth.toInt(), Settings.screenHeight.toInt(),Settings.screenWidth.toInt(), Settings.screenHeight.toInt())
        Settings.gameState = GameState.BallSelection
        Settings.gameOver = false
        Settings.playerPaused = false
        Drawing.cycleHighTip()
        Drawing.cycleLowTip()
    }

    private fun resetPlayerStates(highPlayer: Player, lowPlayer: Player) {
        highPlayer.motion = MotionStates.Free
        highPlayer.touch = TouchState.Ready
        lowPlayer.motion = MotionStates.Free
        lowPlayer.touch = TouchState.Ready
        highPlayer.clearPower()
        lowPlayer.clearPower()

        highPlayer.setPuckStroke(PaintBucket.highBallStrokeColor)
        lowPlayer.setPuckStroke(PaintBucket.lowBallStrokeColor)
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
        setPuckColor(highPlayer, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)
        setPuckColor(lowPlayer, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor)
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

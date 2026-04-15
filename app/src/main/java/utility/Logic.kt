package utility

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.puck.GameView
import com.example.puck.PlayView
import com.example.puck.MainActivity
import com.example.puck.SettingsActivity
import enums.*
import gameobjects.PauseMenu
import gameobjects.Player
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import shapes.HandSelection
import kotlin.reflect.KFunction5

object Logic {

    lateinit var highPlayer: Player
    lateinit var lowPlayer: Player

    lateinit var bottomLeftFinger: HandSelection
    lateinit var topLeftFinger: HandSelection
    lateinit var bottomRightFinger: HandSelection
    lateinit var topRightFinger: HandSelection

    lateinit var pauseMenu: PauseMenu
    lateinit var victoryTicker: Ticker
    lateinit var fingerTicker: Ticker

    val highBallPopup = shapes.BallSelectionPopup(isHigh = true)
    val lowBallPopup = shapes.BallSelectionPopup(isHigh = false)
    val highBallCard = shapes.BallSelectionCard(isHigh = true, popup = highBallPopup)
    val lowBallCard = shapes.BallSelectionCard(isHigh = false, popup = lowBallPopup)

    lateinit var activity: AppCompatActivity

    var menuSelection = MenuSelection.none

    var countPauseTouches = 0
    var countDownText = arrayOf("Ready", "Set", "Go!")
    var cdIndex = 0
    var highTouchedFirst = false

    val countDownTicker = Ticker(33, true)

    var tempGameState = GameState.FingerSelection
    var lowPointer = false
    var highPointer = false
    var leaving = false
    var canCollide = true;

    lateinit var gameView: GameView


    var lowFingerState = FingerState.Unselected
    var highFingerState = FingerState.Unselected

    var winnerSoundHasBeenPlayed = false


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
    }

    fun initialize(activity: AppCompatActivity, gameView: GameView) {
        this.activity = activity
        this.gameView = gameView
        leaving = false
        Settings.pauseGame = false
        Settings.gameOver = false
        Settings.playerPaused = false
        lowFingerState = FingerState.Unselected
        highFingerState = FingerState.Unselected
        highPlayer = Player(
            Puck(Settings.ballRadius, Settings.screenWidth / 4f, Settings.middleY, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight / 5, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor),
            true
        )
        lowPlayer = Player(
            Puck(Settings.ballRadius, Settings.screenWidth * (3/4f), Settings.middleY, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            Circle(Settings.ballRadius, Settings.screenWidth / 2f, Settings.screenHeight - Settings.screenHeight / 5, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            false
        )

        applyBallStyles()


        bottomLeftFinger = HandSelection(Point(Settings.middleX / 3, (5 * Settings.middleY) / 3),  lowPlayer.puckStrokeColor, lowPlayer.puckFillColor, PaintBucket.backgroundColor, false)
        bottomRightFinger = HandSelection(Point((5 * Settings.middleX) / 3, (5 * Settings.middleY) / 3), lowPlayer.puckStrokeColor, lowPlayer.puckFillColor, PaintBucket.backgroundColor, true)
        topRightFinger = HandSelection(Point(Settings.middleX / 3, (Settings.middleY) / 3), highPlayer.puckStrokeColor, highPlayer.puckFillColor, PaintBucket.backgroundColor, true, true)
        topLeftFinger = HandSelection(Point((5 * Settings.middleX) / 3, (Settings.middleY) / 3), highPlayer.puckStrokeColor, highPlayer.puckFillColor, PaintBucket.backgroundColor, false, true)

        victoryTicker = Ticker(Settings.victoryThreshold)
        fingerTicker = Ticker(Settings.fingerSelectionThreshold, true)

        pauseMenu = PauseMenu(this.activity)

    }

    fun reset() {
        countPauseTouches = 0
        tempGameState = GameState.Play
    }

    private fun interceptBallMenu(event: MotionEvent?, motionEvent: Int?): Boolean {
        if (event == null) return false
        if (Settings.gameState != GameState.FingerSelection || Settings.pauseGame) return false
        val x = event.x
        val y = event.y
        val action = motionEvent ?: return false
        val maskedAction = action and MotionEvent.ACTION_MASK
        val isDown = maskedAction == MotionEvent.ACTION_DOWN || maskedAction == MotionEvent.ACTION_POINTER_DOWN

        // Route each side independently so both players can interact simultaneously
        if (y < Settings.middleY) {
            if (highBallPopup.isOpen) {
                if (isDown && !highBallPopup.hitTest(x, y) && highBallCard.hitTest(x, y)) { return true }
                if (highBallPopup.handleTouchEvent(action, x, y)) return true
            } else if (isDown && highBallCard.hitTest(x, y)) {
                highBallPopup.open(); return true
            }
        } else {
            if (lowBallPopup.isOpen) {
                if (isDown && !lowBallPopup.hitTest(x, y) && lowBallCard.hitTest(x, y)) { return true }
                if (lowBallPopup.handleTouchEvent(action, x, y)) return true
            } else if (isDown && lowBallCard.hitTest(x, y)) {
                lowBallPopup.open(); return true
            }
        }

        return false
    }

    fun applyBallStyles() {
        val (highSkin, highTail) = BallStyleFactory.build(Settings.highBallType, ColorTheme.Warm)
        highPlayer.puck.skin = highSkin
        highPlayer.puck.tail = highTail
        val (lowSkin, lowTail) = BallStyleFactory.build(Settings.lowBallType, ColorTheme.Cold)
        lowPlayer.puck.skin = lowSkin
        lowPlayer.puck.tail = lowTail
    }

    private fun prepareCountDown() {
        cdIndex = 0
        countDownTicker.reset(Storage.countdownFramesPerBeat)
        Drawing.countDownProgressTicker.reset(3 * Storage.countdownFramesPerBeat)
    }

    fun countDown() {
        if (highBallPopup.isOpen || lowBallPopup.isOpen) return
        lowPlayer.disableEffects = true
        highPlayer.disableEffects = true
        Drawing.countDownProgressTicker.tick
        if (countDownTicker.tick) {
            if (++cdIndex < countDownText.size) {
                countDownTicker.reset(Storage.countdownFramesPerBeat)
            }
            else {
                lowPlayer.disableEffects = false
                highPlayer.disableEffects = false
                Settings.gameState = GameState.Play
                countDownTicker.reset(Storage.countdownFramesPerBeat)
                Drawing.countDownProgressTicker.reset(3 * Storage.countdownFramesPerBeat)
                cdIndex = 0
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
            lowPlayer.canScore = false
            highPlayer.canScore = false
            lowPlayer.lockedPointerId = -1
            highPlayer.lockedPointerId = -1
            lowPlayer.shielded = false
            highPlayer.shielded = false
            lowPlayer.chargePowerLocked = false
            highPlayer.chargePowerLocked = false
            lowPlayer.charge = 0f
            highPlayer.charge = 0f
            Settings.pauseGame = false
            Settings.gameOver = false
            GameEvents.gameReset.emit(Unit)

            if (Storage.skipFingerSelection && highFingerState != FingerState.Unselected && lowFingerState != FingerState.Unselected) {
                canCollide = true
                prepareCountDown()
                Settings.gameState = GameState.CountDown
            } else {
                lowFingerState = FingerState.Unselected
                highFingerState = FingerState.Unselected
                bottomRightFinger.reset()
                bottomLeftFinger.reset()
                topLeftFinger.reset()
                topRightFinger.reset()
                Settings.gameState = GameState.FingerSelection
            }
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
                } else if (highPlayer.reappearing) {
                    Sounds.playTeleportFinish(highPlayer.px)
                    lowPlayer.launch(Force(direction, Settings.launchBonus + Settings.sweetSpotMax))
                } else if (lowPlayer.reappearing) {
                    Sounds.playTeleportFinish(lowPlayer.px)
                    highPlayer.launch(
                        Force(
                            -direction,
                            Settings.launchBonus + Settings.sweetSpotMax
                        )
                    )
                } else if (highPlayer.shielded && !lowPlayer.shielded) {
                    Sounds.playChargeCollision(collisionPoint.x)
                    highPlayer.shielded = false
                    lowPlayer.launch(Force(direction, Settings.launchBonus + highPlayer.power))
                    highPlayer.launch(Force(-direction, Settings.minLaunchPower))
                    Effects.addShieldedCollisionEffect(
                        highPlayer.puckFillColor,
                        lowPlayer.puckFillColor,
                        intersection
                    )
                } else if (lowPlayer.shielded && !highPlayer.shielded) {
                    Sounds.playChargeCollision(collisionPoint.x)
                    lowPlayer.shielded = false
                    highPlayer.launch(Force(-direction, Settings.launchBonus + lowPlayer.power))
                    lowPlayer.launch(Force(direction, Settings.minLaunchPower))
                    Effects.addShieldedCollisionEffect(
                        lowPlayer.puckFillColor,
                        highPlayer.puckFillColor,
                        intersection
                    )
                } else if (lowPlayer.shielded && highPlayer.shielded) {
                    Sounds.playDoubleChargeCollision(collisionPoint.x)
                    highPlayer.shielded = false
                    lowPlayer.shielded = false
                    val lowPower = lowPlayer.power
                    val highPower = highPlayer.power
                    highPlayer.launch(Force(-direction, Settings.launchBonus + lowPower))
                    lowPlayer.launch(Force(direction, Settings.launchBonus + highPower))
                    Effects.addShieldedCollisionEffect(
                        PaintBucket.effectColor,
                        PaintBucket.effectColor,
                        intersection
                    )
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

                    if (highPlayer.power > lowPlayer.power) {
                        Sounds.playLowPlayerSound(collisionPoint.x)
                    } else {
                        Sounds.playHighPlayerSound(collisionPoint.x)
                    }
                }
                resetTails(highPlayer, lowPlayer)

                //Collision Effect
                Effects.addPuckCollisionEffect(
                    highPlayer.movementSpeed,
                    lowPlayer.movementSpeed,
                    highPlayer.puckFillColor,
                    lowPlayer.puckFillColor,
                    collisionPoint
                )
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
        if ((Settings.gameState == GameState.Play || Settings.gameState == GameState.FingerSelection) && !Tutorial.showing) {
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
        return highBonus && lowBonus
    }

    fun adjustPlayerPosition(player: Player) : Boolean {
        var gotBonus = player.shielded
        if (player.shouldReleaseCharge) {
            gotBonus = player.releaseCharge()
            GameEvents.cantScore.emit(Unit)
        }
        val hadLaunchPower = player.puck.launch.hasPower
        if(player.applyForces()) {
            Effects.addWallCollisionEffect(player.bounceDirection, player.puckFillColor, player.puck)
        }
        if (hadLaunchPower && !player.puck.launch.hasPower) {
            GameEvents.cantScore.emit(Unit)
        }
        return gotBonus
    }

    fun getFingerState(x: Float, y: Float, leftHand: HandSelection, rightHand: HandSelection) : FingerState {
        val leftState = leftHand.getFinger(x, y)
        val rightState = rightHand.getFinger(x, y)

        return if (leftState != FingerState.Unselected) {
            leftHand.lockIn(leftState)
            rightHand.unlock()
            leftState
        } else if (rightState != FingerState.Unselected) {
            rightHand.lockIn(rightState)
            leftHand.unlock()
            rightState
        } else {
            rightHand.unlock()
            leftHand.unlock()
            FingerState.Unselected
        }
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
            player.shouldReleaseCharge = true
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
            player.shouldReleaseCharge = true
        }
        else {
            player.touch = newState
        }
    }



    fun updateCanScoreWall() {
        if (!this::highPlayer.isInitialized) return
        val delta = 0.4f
        Settings.canScoreWallProgress = if (Settings.canScoreWallHiding)
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

    private fun checkScored(scoring: Player, other: Player) : Boolean {
        if (scoring.canScore && (other.py < Settings.topGoalBottom + other.pRadius || other.py > Settings.bottomGoalTop - other.pRadius)) {
            val highGoal = other.py < Settings.topGoalBottom  + other.pRadius
            scoring.score()
            other.clearPower()
            scoring.clearPower()
            if (Settings.scoreFlashEnabled) {
                Settings.scoreFlashAlpha = 200f
                Settings.scoreFlashColor = scoring.puckFillColor
            }
            if (Settings.scorePopEnabled) {
                if (scoring.isHigh) {
                    Settings.highScorePopTicker.reset()
                } else {
                    Settings.lowScorePopTicker.reset()
                }
            }
            setPuckColor(other, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)
            setPuckColor(scoring, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor)
            Settings.gameState = GameState.Scored
            Sounds.playScoreSound(other.py)
            Effects.clearCollisionEffects()
            Effects.addScoreEffect(
                scoring.puckFillColor,
                other.puckFillColor,
                Point(other.px, if (highGoal) Settings.topGoalBottom  else  Settings.bottomGoalTop),
                highGoal
            )
            return true
        } else if (other.motion == MotionStates.Free && other.py > Settings.topGoalBottom + other.pRadius && other.py < Settings.bottomGoalTop - other.pRadius) {
            scoring.canScore = true
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
                val victoryDelay = if (Storage.skipFingerSelection) Settings.refreshRate * 20 else Settings.victoryThreshold
                victoryTicker.reset(victoryDelay)
                GameState.GameOver
            } else {
                prepareCountDown()
                GameState.CountDown
            }
            highPlayer.disableEffects = false
            lowPlayer.disableEffects = false
        }
        resetPlayerStates(highPlayer, lowPlayer)
    }

    private fun bestHighPointer(event: MotionEvent): Int {
        var bestIdx = 0
        var bestY = Float.MAX_VALUE
        for (i in 0 until event.pointerCount) {
            val y = event.getY(i)
            if (y < Settings.middleY && y < bestY) {
                bestY = y
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun bestLowPointer(event: MotionEvent): Int {
        var bestIdx = 0
        var bestY = Float.MIN_VALUE
        for (i in 0 until event.pointerCount) {
            val y = event.getY(i)
            if (y > Settings.middleY && y > bestY) {
                bestY = y
                bestIdx = i
            }
        }
        return bestIdx
    }

    fun onTouchEvent(event: MotionEvent?, context: Context) {
        val motionEvent = event?.action
        var pointerCount = event?.pointerCount
        if (pointerCount == null) {
            pointerCount = 1
        }

        if (interceptBallMenu(event, motionEvent)) return


        //use these as temporary variables so that finger position is only
        //updated after all calculations are finished
        val highFinger = Point(highPlayer.fx, highPlayer.fy)
        val lowFinger = Point(lowPlayer.fx, lowPlayer.fy)

        var clearTop = false
        var clearBottom = false

        if (pointerCount > 1) {
            checkPauseGame(pointerCount, event!!.getY(0), event.getY(1))

            if (Settings.pauseGame) {
                checkUnpauseGame(event.getY(0), event.getY(1))
            }
            else {
                // Lock/unlock pointer IDs on ACTION_POINTER_DOWN/UP
                val maskedAction = motionEvent?.and(MotionEvent.ACTION_MASK)
                val actionIndex = event.actionIndex
                val actionPointerId = event.getPointerId(actionIndex)
                val actionY = event.getY(actionIndex)

                if (maskedAction == MotionEvent.ACTION_POINTER_DOWN) {
                    if (actionY < Settings.middleY && highPlayer.lockedPointerId == -1) {
                        highPlayer.lockedPointerId = actionPointerId
                    } else if (actionY > Settings.middleY && lowPlayer.lockedPointerId == -1) {
                        lowPlayer.lockedPointerId = actionPointerId
                    }
                } else if (maskedAction == MotionEvent.ACTION_POINTER_UP) {
                    if (actionPointerId == highPlayer.lockedPointerId) {
                        highPlayer.lockedPointerId = -1
                    } else if (actionPointerId == lowPlayer.lockedPointerId) {
                        lowPlayer.lockedPointerId = -1
                    }
                }

                assignTouchState(highTouchedFirst, motionEvent)
            }

            // Prefer locked pointer, fall back to best-Y heuristic
            val highLockedIdx = if (highPlayer.lockedPointerId != -1)
                event!!.findPointerIndex(highPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
            else -1
            val lowLockedIdx = if (lowPlayer.lockedPointerId != -1)
                event!!.findPointerIndex(lowPlayer.lockedPointerId).let { if (it >= 0) it else -1 }
            else -1

            val highIdx = if (highLockedIdx >= 0) highLockedIdx else bestHighPointer(event!!)
            val lowIdx = if (lowLockedIdx >= 0) lowLockedIdx else bestLowPointer(event!!)

            val highX = event!!.getX(highIdx)
            val highY = event.getY(highIdx) - Settings.topGoalBottom
            val lowX = event.getX(lowIdx)
            val lowY = event.getY(lowIdx) + Settings.topGoalBottom

            highFinger.setLocation(highX, highY)
            lowFinger.setLocation(lowX, lowY)
            if (Settings.gameState == GameState.FingerSelection) {
                highFingerState = getFingerState(highX, highY, topLeftFinger, topRightFinger)
                lowFingerState = getFingerState(lowX, lowY, bottomLeftFinger, bottomRightFinger)
            }
            assignFingerLocation(clearBottom, lowFinger, clearTop, highFinger)
        }
        else {
            val x = event!!.x
            var y = event.y
            if (Settings.pauseGame) {
                if (Tutorial.showing && Settings.topGoalBottom < y && y < Settings.bottomGoalTop) {
                    Settings.gameState = tempGameState
                    Settings.pauseGame = false
//                    Tutorial.assignBox(TutorialState.None)
                    countPauseTouches = 0
//                    layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
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
                    // Touch in the play area during pause — dismiss the pause menu
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        Settings.pauseGame = false
                        Settings.gameState = tempGameState
                        countPauseTouches = 0
                    }
                }
            }
            else {
                if (y > Settings.middleY) {
                    if (Settings.gameState == GameState.FingerSelection)  {
                        lowFingerState = getFingerState(x, y, bottomLeftFinger, bottomRightFinger)
                        highPointer = false
                    }

                    if (motionEvent == MotionEvent.ACTION_DOWN) {
                        highTouchedFirst = false
                        if (lowPlayer.lockedPointerId == -1) {
                            lowPlayer.lockedPointerId = event.getPointerId(0)
                        }
//                        Sounds.playLowPlayerSound(lowPlayer.fx)
                    }
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        Sounds.playLowPlayerSound(lowPlayer.fx)
                        if (event.getPointerId(0) == lowPlayer.lockedPointerId) {
                            lowPlayer.lockedPointerId = -1
                        }
                    }
                    if (lowPlayer.notLocked()) {
                        setSingleTouch(motionEvent, lowPlayer)
                        y += Settings.topGoalBottom;
                        lowFinger.setLocation(x,y)
                        clearTop = true
                    }
                }
                else {
                    if (Settings.gameState == GameState.FingerSelection) {
                        highFingerState = getFingerState(x, y, topLeftFinger, topRightFinger)
                        lowPointer = false
                    }
                    if (motionEvent == MotionEvent.ACTION_DOWN) {
                        highTouchedFirst = true
                        if (highPlayer.lockedPointerId == -1) {
                            highPlayer.lockedPointerId = event.getPointerId(0)
                        }
//                        Sounds.playHighPlayerSound(highPlayer.fx)
                    }
                    if (motionEvent == MotionEvent.ACTION_UP) {
                        Sounds.playHighPlayerSound(highPlayer.fx)
                        if (event.getPointerId(0) == highPlayer.lockedPointerId) {
                            highPlayer.lockedPointerId = -1
                        }
                    }
                    if (highPlayer.notLocked()) {
                        setSingleTouch(motionEvent, highPlayer)
                        y -= Settings.topGoalBottom
                        highFinger.setLocation(x,y)
                        clearBottom = true
                    }
                }
                assignFingerLocation(clearBottom, lowFinger, clearTop, highFinger)
            }
        }
    }

    fun menuCallback(context: Context) {
        when(menuSelection){
            MenuSelection.back -> {
                leaving = true
                Sounds.playMenuAmbiance()
                resetGame(GameView::doOnSizeChange)
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
                Sounds.playMenuAmbiance()
                resetGame(GameView::doOnSizeChange)
                val intent = Intent(context, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ContextCompat.startActivity(context, intent, Bundle())
                activity.finish()
            }
            else -> {}
        }
    }

    fun assignFingerLocation(clearBottom: Boolean,lowFinger: Point,clearTop: Boolean,highFinger: Point) {
        if (Settings.gameState == GameState.FingerSelection) {
            highPlayer.fingerTargetLocation = highPlayer.puck
            lowPlayer.fingerTargetLocation = lowPlayer.puck
            return
        }

        if (!clearBottom) {
            transform(lowFinger, lowFingerState)
        }
        if (!clearTop) {
            transform(highFinger, highFingerState, true)
        }

        constrain(highFinger, highPlayer.finger)
        constrain(lowFinger, lowPlayer.finger)

        highPlayer.fingerTargetLocation = highFinger
        lowPlayer.fingerTargetLocation = lowFinger

//        highPlayer.finger.setLocation(highFinger)
//        lowPlayer.finger.setLocation(lowFinger)
//        highPlayer.finger.moveTowardLocation(highFinger)
//        lowPlayer.finger.moveTowardLocation(lowFinger)
    }

    private fun constrain(point: Point, circle: Circle) {
        if (point.x < circle.radius + Settings.shortParticleSide) { point.x = circle.radius + Settings.shortParticleSide + 10 }
        if (point.x > Settings.screenWidth - circle.radius - Settings.shortParticleSide) point.x = (Settings.screenWidth - circle.radius - Settings.shortParticleSide - 10)
        if (point.y < circle.radius + Settings.topGoalBottom + Settings.shortParticleSide) { point.y = circle.radius + Settings.topGoalBottom + Settings.shortParticleSide}
        if (point.y > Settings.bottomGoalTop - circle.radius - Settings.shortParticleSide) point.y = (Settings.bottomGoalTop - circle.radius - Settings.shortParticleSide)
    }

    fun transform(point: Point, finger: FingerState, flip: Boolean = false) {
        when (finger) {
            FingerState.RightThumb -> {
                point.setLocation(
                    if (flip) point.x * 2f else point.x - 2f * (Settings.screenWidth - point.x),
                    if (flip) point.y * 3.5f else point.y - 3.5f * (Settings.screenHeight - point.y)
                )
            }
            FingerState.LeftThumb -> {
                point.setLocation(
                    if (flip) point.x - 2f * (Settings.screenWidth - point.x) else point.x * 2f,
                    if (flip) point.y * 3.5f else point.y - 3.5f * (Settings.screenHeight - point.y)
                )
            }
            FingerState.RightPointer, FingerState.LeftPointer -> {
                var dx = point.x * (point.x / Settings.middleX)
                dx = if (dx < Settings.ballRadius) Settings.ballRadius else dx
                dx = if (dx > Settings.screenWidth - Settings.ballRadius) Settings.screenWidth - Settings.ballRadius else dx
                point.setLocation(dx, if (flip) point.y * 3.5f else point.y - 3.5f * (Settings.screenHeight - point.y))
            }
            else -> {}
        }
    }

    fun closeBallPopups() {
        highBallPopup.close()
        lowBallPopup.close()
    }

    fun resetGame(sizeChanged: KFunction5<GameView, Int, Int, Int, Int, Unit>) {
        closeBallPopups()
        Settings.pointsToWin = Storage.loadPointsToWin()
        Settings.highBallType = Storage.loadHighBallType(Settings.highBallType)
        Settings.lowBallType = Storage.loadLowBallType(Settings.lowBallType)
        Settings.unlockProgress = Storage.unlockProgress
        lowFingerState = FingerState.Unselected
        highFingerState = FingerState.Unselected
        topRightFinger.unlock()
        topLeftFinger.unlock()
        bottomRightFinger.unlock()
        bottomLeftFinger.unlock()
        fingerTicker.reset()
        Drawing.countDownProgressTicker.reset()
        countDownTicker.reset()
        Settings.pauseGame = false
        sizeChanged(gameView, Settings.screenWidth.toInt(), Settings.screenHeight.toInt(),Settings.screenWidth.toInt(), Settings.screenHeight.toInt())
        Settings.gameState = GameState.FingerSelection
        Settings.gameOver = false
        Settings.playerPaused = false
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

    fun setPuckColor(player: Player, fill: Int, stroke: Int) {
        player.puck.setFill(fill)
        player.puck.setStroke(stroke)
    }

    fun resetTails(highPlayer: Player, lowPlayer: Player) {
        setPuckColor(highPlayer, PaintBucket.highBallColor, PaintBucket.highBallStrokeColor)
        setPuckColor(lowPlayer, PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor)
    }

    fun countDownText() : String {
        return countDownText[cdIndex]
    }
}
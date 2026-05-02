package gameobjects

import enums.BallType
import enums.GameState
import gameobjects.puckstyle.RandomRoll
import physics.Ticker

object Settings {
    var highBallType: BallType = BallType.Classic
    var lowBallType: BallType = BallType.Classic
    var highRandomRoll: RandomRoll? = null
    var lowRandomRoll: RandomRoll? = null
    val scoreFlashEnabled = true
    val scoreBurstEnabled = true
    val scorePopEnabled = true

    var scoreFlashAlpha = 0f
    var scoreFlashColor = 0

    var highScorePopTicker = Ticker(20, true)
    var lowScorePopTicker = Ticker(20, true)

    var canScoreWallProgress: Float = 0f
    var canScore: Boolean = false

    // Opening: anchored at screen edge, inner edge retreats outward (inner side clears first).
    // Closing: anchored at inner (goal) edge, outer edge grows toward screen edge (goal boundary appears immediately).
    val canScoreTopWallTop: Float get() = if (canScore) 0f else topGoalBottom * canScoreWallProgress
    val canScoreTopWallBottom: Float get() = if (canScore) topGoalBottom * (1f - canScoreWallProgress) else topGoalBottom

    val canScoreBottomWallTop: Float get() = if (canScore) screenHeight - (screenHeight - bottomGoalTop) * (1f - canScoreWallProgress) else bottomGoalTop
    val canScoreBottomWallBottom: Float get() = if (canScore) screenHeight else screenHeight - (screenHeight - bottomGoalTop) * canScoreWallProgress
    var refreshRate: Int = 16
    var unlockProgress = 0
    var pauseGame = false;
    var startWithTutorial = false
    var tutorialPaused = false
    var playerPaused = false
    var gameOver = false
    var gameState = GameState.BallSelection
    var longParticleSide = 0f
    var basePuckDistanceModifier = .05f// TODO: set by screen ratio
    var strokeWidth = 0f
    var prepareTeleportTickerTime = 100
    var teleportTickerTime = 10
    var screenRatio = 0f
    var ballRadius = 0f
    var middleX = 0f
    var middleY = 0f
    val victoryThreshold : Int get() = refreshRate * 10
    var friction = .3f
    var screenLeft = 0f
    var screenRight = 0f
    var screenTop = 0f
    var topGoalBottom = 0f
    var bottomGoalTop = 0f
    var screenBottom = 0f
    var screenWidth = 0f
    var screenHeight = 0f
    var shortParticleSide = 0f
    var chargeStart = 10f
    var sweetSpotMin = 40
    var sweetSpotMax = 50
    var minLaunchPower = 10f
    var minPuckSpeed: Float = 3f// TODO: set by screen ratio
    var maxPuckSpeed: Float = 15f// TODO: set by screen ratio
    var maxPuckLaunchSpeed: Float = 23f// TODO: set by screen ratio
    var chargeIncreaseRate: Float = 1f
    var sweetSpotWindowFrames: Int = 60
    var inertHoldFrames: Int = 60
    val chargeDrainRate: Float get() = chargeIncreaseRate
    val drainFloor: Float get() = sweetSpotMax * 0.5f
    var maxPower: Float = 30f
    var tailLength: Int = 20
    val tailLengthMultiplier: Float get() = when (tailLength) {
        10 -> 0.5f
        40 -> 1.5f
        else -> 1.0f
    }
    var scoreZoneHeight: Float = 3f
    var pointsToWin: Int = 5
    var highPlayerArrow: Boolean = true
    var lowPlayerArrow: Boolean = true
    var highPlayerChargeFill: Boolean = true
    var lowPlayerChargeFill: Boolean = true
    var scoreOffsetHigh: Float = 0f
    var scoreOffsetLow: Float = 0f

    val hitStunMinImpactPower: Float get() = minLaunchPower
    val hitStunMaxImpactPower: Float get() = maxPuckLaunchSpeed
    val hitStunMinSeconds: Float = 0.15f
    val hitStunMaxSeconds: Float = 0.5f
}
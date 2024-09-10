package gameobjects

import enums.GameState

object Settings {
    var refreshRate: Int = 16
    val maxAds = 100
    var adsLeft = 100
    var adShownToday = false;
    var pauseGame = false;
    var startWithTutorial = false
    var tutorialPaused = false
    var playerPaused = false
    var gameOver = false
    var gameState = GameState.FingerSelection
    var longParticleSide = 0f
    var basePuckDistanceModifier = .05f// TODO: set by screen ratio
    var strokeWidth = 0f
    var prepareTeleportTickerTime = 100
    var teleportTickerTime = 10
    var screenRatio = 0f
    var ballRadius = 0f
    var middleX = 0f
    var middleY = 0f
    val fingerSelectionThreshold : Int get() = refreshRate * 5
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
    var maxBonusTickerTime: Int = 200
    var chargeIncreaseRate: Float = 1f
    var maxPower: Float = 30f
    var tailLength: Int = 20
    var scoreZoneHeight: Float = 3f
    var launchBonus: Float = 10f
}
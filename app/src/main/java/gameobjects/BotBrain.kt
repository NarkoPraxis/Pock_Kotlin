package gameobjects

import enums.GameState
import enums.TouchState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BotBrain(private var player: Player, private var opponent: Player, private val config: BotConfig) {

    private enum class BotState { Waiting, Charging, Released }

    private var state = BotState.Waiting
    private var framesRemaining = config.reactionDelay
    private var plannedAimDir = physics.Point(0f, 0f)
    private var plannedPower = 0f
    private var lastGameState = GameState.BallSelection
    private var storedVarianceRad = 0f

    fun tick() {
        val currentState = Settings.gameState
        val stateChanged = currentState != lastGameState
        lastGameState = currentState

        when (currentState) {
            GameState.BallSelection -> {
                if (opponent.isFlingHeld) {
                    if (!player.isFlingHeld) {
                        player.flingStart.setLocation(player.puck.x, player.puck.y)
                        player.flingCurrent.setLocation(player.puck.x, player.puck.y)
                        player.isFlingHeld = true
                    }
                    player.touch = TouchState.Down
                }
            }
            GameState.Play -> {
                if (stateChanged) {
                    player.touch = TouchState.Ready
                    player.isFlingHeld = false
                    player.clearCharge()
                }
                tickPlay()
            }
            else -> {}
        }
    }

    private fun tickPlay() {
        when (state) {
            BotState.Waiting -> {
                framesRemaining--
                if (framesRemaining <= 0) planAndStartShot()
            }
            BotState.Charging -> {
                framesRemaining--
                updateTracking()
                if (framesRemaining <= 0) fireShot()
            }
            BotState.Released -> {
                state = BotState.Waiting
                framesRemaining = nextShotInterval()
            }
        }
    }

    private fun updateTracking() {
        plannedAimDir = currentAimDir()
        player.updateBotAimDirection(plannedAimDir, plannedPower)
    }

    private fun currentAimDir(): physics.Point {
        val dx = opponent.puck.x - player.puck.x
        val dy = opponent.puck.y - player.puck.y
        val baseAngle = atan2(dy, dx)
        val finalAngle = baseAngle + storedVarianceRad
        return physics.Point(cos(finalAngle), sin(finalAngle))
    }

    private fun planAndStartShot() {
        val varianceDeg = kotlin.random.Random.nextFloat() * 2 * config.accuracyVariance - config.accuracyVariance
        storedVarianceRad = (varianceDeg * Math.PI / 180.0).toFloat()
        plannedAimDir = currentAimDir()

        // Frames needed for charge to reach sweetSpotMax (phase → SweetSpot), adjusted for charge rate.
        val framesToSweetSpot = ((Settings.sweetSpotMax - Settings.chargeStart) / Settings.chargeIncreaseRate).toInt()
        // Total frames until Draining begins (= sweet spot window end).
        val totalFramesToDraining = framesToSweetSpot + Settings.sweetSpotWindowFrames

        val hitsSweetSpot = Random.nextInt(1, 101) <= config.sweetSpotChance
        val chargeFrames: Int
        if (hitsSweetSpot) {
            // Fire mid-window so the bot is guaranteed in SweetSpot phase regardless of charge rate.
            chargeFrames = framesToSweetSpot + Settings.sweetSpotWindowFrames / 2
            plannedPower = Settings.sweetSpotMax.toFloat()
        } else {
            // basePower and powerVariance are on a 0–100 scale where 100 = Draining threshold.
            // Values above 100 place the shot into Draining (weaker but organic overcharge).
            val rawPercent = config.basePower + (Random.nextFloat() * 2f - 1f) * config.powerVariance
            val clampedPercent = rawPercent.coerceIn(0f, 150f)
            chargeFrames = ((clampedPercent / 100f) * totalFramesToDraining).toInt().coerceAtLeast(1)
            // Map the clamped percentage to charge units for physics force and arrow display.
            val powerFraction = (clampedPercent / 100f).coerceIn(0f, 1f)
            plannedPower = Settings.chargeStart + powerFraction * (Settings.sweetSpotMax - Settings.chargeStart)
        }

        player.beginBotCharge()
        player.updateBotAimDirection(plannedAimDir, plannedPower)
        framesRemaining = chargeFrames
        state = BotState.Charging
    }

    private fun fireShot() {
        player.releaseBotShot(plannedAimDir, plannedPower)
        state = BotState.Released
    }

    private fun nextShotInterval(): Int {
        val variance = Random.nextInt(-config.shotFrequencyVariance, config.shotFrequencyVariance + 1)
        return (config.baseShotFrequency + variance).coerceAtLeast(15)
    }

    fun updateReferences(player: Player, opponent: Player) {
        this.player = player
        this.opponent = opponent
    }

    fun reset() {
        state = BotState.Waiting
        framesRemaining = config.reactionDelay
    }
}

package gameobjects

import enums.GameState
import enums.TouchState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BotBrain(private val player: Player, private val opponent: Player, private val config: BotConfig) {

    private enum class BotState { Waiting, Charging, Released }

    private var state = BotState.Waiting
    private var framesRemaining = config.reactionDelay
    private var plannedAimDir = physics.Point(0f, 0f)
    private var plannedPower = 0f
    private var lastGameState = GameState.BallSelection

    fun tick() {
        val currentState = Settings.gameState
        val stateChanged = currentState != lastGameState
        lastGameState = currentState

        when (currentState) {
            GameState.BallSelection -> {
                player.flingStart.setLocation(player.puck.x, player.puck.y)
                player.flingCurrent.setLocation(player.puck.x, player.puck.y)
                player.isFlingHeld = true
                player.touch = TouchState.Down
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
                if (framesRemaining <= 0) fireShot()
            }
            BotState.Released -> {
                state = BotState.Waiting
                framesRemaining = nextShotInterval()
            }
        }
    }

    private fun planAndStartShot() {
        val dx = opponent.puck.x - player.puck.x
        val dy = opponent.puck.y - player.puck.y
        val dist = sqrt(dx * dx + dy * dy)
        val baseAngle = atan2(dy, dx)

        val varianceDeg = Random.nextFloat() * 2 * config.accuracyVariance - config.accuracyVariance
        val varianceRad = (varianceDeg * Math.PI / 180.0).toFloat()
        val finalAngle = baseAngle + varianceRad

        plannedAimDir = physics.Point(cos(finalAngle), sin(finalAngle))

        val hitsSweetSpot = Random.nextInt(1, 101) <= config.sweetSpotChance
        val chargeFrames: Int
        if (hitsSweetSpot) {
            plannedPower = ((Settings.sweetSpotMin + Settings.sweetSpotMax) / 2f)
            chargeFrames = ((Settings.sweetSpotMin + Settings.sweetSpotMax) / 2)
        } else {
            plannedPower = (config.basePower + (Random.nextFloat() * 2f - 1f) * config.powerVariance)
                .coerceIn(Settings.chargeStart, Settings.sweetSpotMax.toFloat())
            val sweetSpotWindow = (Settings.sweetSpotMax - Settings.sweetSpotMin).coerceAtLeast(1)
            chargeFrames = Settings.sweetSpotMin + Random.nextInt(0, sweetSpotWindow)
        }

        player.beginBotCharge()
        val aimOffset = Settings.screenRatio * 2f
        player.flingCurrent.setLocation(
            player.flingStart.x - plannedAimDir.x * aimOffset,
            player.flingStart.y - plannedAimDir.y * aimOffset
        )
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

    fun reset() {
        state = BotState.Waiting
        framesRemaining = config.reactionDelay
    }
}

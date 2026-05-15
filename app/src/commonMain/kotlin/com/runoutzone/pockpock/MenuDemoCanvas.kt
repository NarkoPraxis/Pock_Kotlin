package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import enums.BallType
import enums.GameState
import gameobjects.BotBrain
import gameobjects.BotConfig
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import utility.Drawing
import utility.GameLoop
import utility.Logic
import utility.PaintBucket
import utility.Sounds
import utility.drawGameFrame

@Composable
fun MenuDemoCanvas() {
    val tickState = remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    Drawing.initializeTextMeasurer(textMeasurer)

    val demoBotHigh = remember { mutableStateOf<BotBrain?>(null) }
    val demoBotLow  = remember { mutableStateOf<BotBrain?>(null) }

    val gameLoop = remember {
        GameLoop(
            intervalMs = { Settings.refreshRate.toLong() },
            onTick = {
                // Guard with isDemoMode only — isInitialized is intentionally not checked here
                // because IosGameHost.onDispose (which resets it) can fire after our onSizeChanged
                // due to Compose's compose-before-dispose ordering.
                if (Settings.isDemoMode) {
                    demoBotHigh.value?.tick()
                    demoBotLow.value?.tick()
                    Logic.updateCanScoreWall()
                    when (Settings.gameState) {
                        GameState.Play -> {
                            Logic.adjustPlayerPositions()
                            Logic.checkCharge()
                            Logic.calculateCollision()
                            Logic.checkScored()
                            Logic.checkDanger()
                        }
                        GameState.Scored -> Logic.scored()
                        else -> {}
                    }
                }
                tickState.intValue++
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            gameLoop.stop()
            Settings.isDemoMode = false
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (!initialized && size.width > 0 && size.height > 0) {
                    initialized = true

                    Logic.initializeSettings(size.width, size.height)
                    PaintBucket.initialize(Settings.screenRatio)
                    Sounds.initializeGame()
                    Drawing.initialize()

                    Settings.isSinglePlayer = false
                    Settings.isDemoMode = true

                    // Set ball types before initialize() so applyBallStyles() uses them
                    val unlocked = BallType.entries
                        .filter { it != BallType.Random && BallStyleFactory.isUnlocked(it, Settings.unlockProgress) }
                        .shuffled()
                    Settings.highBallType = unlocked.getOrElse(0) { BallType.Classic }
                    Settings.lowBallType  = unlocked.getOrElse(1) { BallType.Classic }

                    Logic.initialize()

                    // Close the ball-selection popups — demo starts directly in Play
                    Logic.highBallPopup.isOpen = false
                    Logic.lowBallPopup.isOpen = false

                    // Create two Hard bots using the freshly-built players
                    demoBotHigh.value = BotBrain(Logic.highPlayer, Logic.lowPlayer, BotConfig.Hard)
                    demoBotLow.value  = BotBrain(Logic.lowPlayer, Logic.highPlayer, BotConfig.Hard)

                    // Skip BallSelection — pucks are already at their natural start positions
                    // from applyBallStyles() (highStartX/lowStartX, middleY), matching the real game
                    Settings.gameState = GameState.Play

                    gameLoop.start()
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        tickState.value
        drawGameFrame()
    }
}

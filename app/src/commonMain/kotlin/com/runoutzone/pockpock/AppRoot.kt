package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import enums.GameState
import gameobjects.Settings
import utility.Drawing
import utility.GameLoop
import utility.Logic
import utility.PaintBucket
import utility.Sounds

private enum class Screen { MainMenu, Game, Settings, BallUnlock }

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Screen.MainMenu.name) {
        composable(Screen.MainMenu.name) {
            MainMenuScreen(
                onPlayTapped = {
                    Settings.isSinglePlayer = false
                    navController.navigate(Screen.Game.name)
                },
                onSettingsTapped = { navController.navigate(Screen.Settings.name) },
                onBallsTapped = { navController.navigate(Screen.BallUnlock.name) }
            )
        }
        composable(Screen.Game.name) {
            IosGameHost(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.name) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.BallUnlock.name) {
            BallUnlockScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun IosGameHost(onBack: () -> Unit) {
    val tickState = remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }

    val gameLoop = remember {
        GameLoop(
            intervalMs = { Settings.refreshRate.toLong() },
            onTick = {
                if (Logic.isInitialized) {
                    Logic.botBrain?.tick()
                    Logic.updateCanScoreWall()
                    when (Settings.gameState) {
                        GameState.BallSelection -> {
                            Logic.checkCharge()
                            Logic.cancelChargesOnRelease()
                            Logic.checkBallSelectionEnd()
                        }
                        GameState.Play -> {
                            Logic.adjustPlayerPositions()
                            Logic.checkCharge()
                            Logic.calculateCollision()
                            Logic.checkScored()
                            Logic.checkDanger()
                        }
                        GameState.Scored -> Logic.scored()
                        GameState.GameOver -> Logic.gameOver()
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
            Logic.isInitialized = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GameScreen(
            gameLoopTick = tickState,
            onSizeKnown = { w, h ->
                if (!initialized) {
                    initialized = true
                    Logic.initializeSettings(w.toInt(), h.toInt())
                    PaintBucket.initialize(Settings.screenRatio)
                    Sounds.initializeGame()
                    Drawing.initialize()
                    Logic.initialize()
                    Logic.composeReinitCallback = {
                        Logic.isInitialized = false
                        Logic.initializeSettings(w.toInt(), h.toInt())
                        PaintBucket.initialize(Settings.screenRatio)
                        Drawing.initialize()
                        Logic.initialize()
                    }
                    gameLoop.start()
                }
            }
        )
    }
}

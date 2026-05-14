package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import enums.GameState
import gameobjects.BotConfig
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
    var showDifficultyDialog by remember { mutableStateOf(false) }

    NavHost(navController, startDestination = Screen.MainMenu.name) {
        composable(Screen.MainMenu.name) {
            LaunchedEffect(Unit) {
                Sounds.playMenuAmbiance()
            }
            MainMenuScreen(
                onPlayTapped = {
                    Settings.isSinglePlayer = false
                    Sounds.playGameAmbiance()
                    navController.navigate(Screen.Game.name)
                },
                onSinglePlayerTapped = { showDifficultyDialog = true },
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

    if (showDifficultyDialog) {
        AlertDialog(
            onDismissRequest = { showDifficultyDialog = false },
            title = { Text("Choose Difficulty", color = Color.White) },
            text = {
                Column {
                    listOf(
                        "Easy" to BotConfig.Easy,
                        "Medium" to BotConfig.Medium,
                        "Hard" to BotConfig.Hard
                    ).forEach { (label, config) ->
                        TextButton(onClick = {
                            Settings.botConfig = config
                            Settings.isSinglePlayer = true
                            showDifficultyDialog = false
                            Sounds.playGameAmbiance()
                            navController.navigate(Screen.Game.name)
                        }) {
                            Text(label, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDifficultyDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF2A2A3A)
        )
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
            Sounds.pauseAll()
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

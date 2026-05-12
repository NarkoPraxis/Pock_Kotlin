package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import gameobjects.Settings
import utility.GameLoop
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
    val gameLoop = remember {
        GameLoop(
            intervalMs = { Settings.refreshRate.toLong() },
            onTick = { tickState.intValue++ }
        )
    }

    DisposableEffect(Unit) {
        onDispose { gameLoop.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GameScreen(
            gameLoopTick = tickState,
            onSizeKnown = { w, h ->
                if (Settings.screenWidth == 0f) {
                    Settings.initializeForScreen(w.toInt(), h.toInt())
                    PaintBucket.initialize(Settings.screenRatio)
                    Sounds.initializeGame()
                    gameLoop.start()
                }
            }
        )
    }
}

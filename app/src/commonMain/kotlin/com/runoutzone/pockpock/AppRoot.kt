package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import enums.GameState
import gameobjects.BotConfig
import gameobjects.Settings
import gameobjects.puckstyle.skins.PokPokSkinPainters
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.Drawing
import utility.GameLoop
import utility.Logic
import utility.PaintBucket
import utility.Sounds
import utility.Storage
import utility.edgeSwipeBack

/** Provides the current dark-mode flag to any composable in the tree. */
val LocalDarkMode = compositionLocalOf { false }

private enum class Screen { MainMenu, Game, Settings, BallUnlock, ScoreCalibration }

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    // Drive isOnMainMenu via a destination-changed listener for guaranteed synchronous updates.
    // `currentBackStackEntryAsState` is Flow-backed and can lag by a frame, leaving MenuDemoCanvas
    // mounted briefly after navigation — long enough to emit collision sounds.
    var isOnMainMenu by remember { mutableStateOf(true) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val onMenu = destination.route == Screen.MainMenu.name
            isOnMainMenu = onMenu
            // Hard kill the demo synchronously so the next gameLoop tick can't fire
            // collision sounds while Compose schedules the unmount of MenuDemoCanvas.
            if (!onMenu) {
                Settings.isDemoMode = false
                Sounds.stopAllSfx()
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    var showDifficultyDialog by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(Storage.darkMode) }

    // SVG painters used by PokPokSkin must be created in a composable scope. `painterResource`
    // is internally remembered, so this is effectively a one-time cost per app session.
    PokPokSkinPainters.load()

    // Fire playMenuAmbiance every time MainMenu becomes the active route.
    LaunchedEffect(isOnMainMenu) {
        if (isOnMainMenu) Sounds.playMenuAmbiance()
    }

    CompositionLocalProvider(LocalDarkMode provides darkMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // MenuDemoCanvas lives outside NavHost so its lifecycle is controlled entirely
            // by isOnMainMenu at AppRoot level. When false, the composable leaves the tree,
            // DisposableEffect.onDispose fires, and the game loop is stopped immediately.
            if (isOnMainMenu) {
                MenuDemoCanvas()
            }

            NavHost(navController, startDestination = Screen.MainMenu.name) {
                composable(Screen.MainMenu.name) {
                    MainMenuScreen(
                        onPlayTapped = {
                            Settings.isDemoMode = false
                            Settings.isSinglePlayer = false
                            Sounds.playGameAmbiance()
                            navController.navigate(Screen.Game.name)
                        },
                        onSinglePlayerTapped = { showDifficultyDialog = true },
                        onSettingsTapped = {
                            Settings.isDemoMode = false
                            navController.navigate(Screen.Settings.name)
                        },
                        onBallsTapped = {
                            Settings.isDemoMode = false
                            navController.navigate(Screen.BallUnlock.name)
                        },
                    )
                }
                composable(Screen.Game.name) {
                    IosGameHost(onBack = { navController.popBackStack() })
                }
                composable(Screen.Settings.name) {
                    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack { navController.popBackStack() }) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onDarkModeChanged = { darkMode = it },
                            onScoreCalibrationTapped = { navController.navigate(Screen.ScoreCalibration.name) }
                        )
                    }
                }
                composable(Screen.ScoreCalibration.name) {
                    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack { navController.popBackStack() }) {
                        ScoreCalibrationScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Screen.BallUnlock.name) {
                    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack { navController.popBackStack() }) {
                        BallUnlockScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }

        if (showDifficultyDialog) {
            AlertDialog(
                onDismissRequest = { showDifficultyDialog = false },
                title = { Text(stringResource(Res.string.choose_difficulty), color = PaintBucket.white) },
                text = {
                    Column {
                        listOf(
                            stringResource(Res.string.easy) to BotConfig.Easy,
                            stringResource(Res.string.medium) to BotConfig.Medium,
                            stringResource(Res.string.hard) to BotConfig.Hard
                        ).forEach { (label, config) ->
                            TextButton(onClick = {
                                Settings.isDemoMode = false
                                Settings.botConfig = config
                                Settings.isSinglePlayer = true
                                showDifficultyDialog = false
                                Sounds.playGameAmbiance()
                                navController.navigate(Screen.Game.name)
                            }) {
                                Text(label, color = PaintBucket.white)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDifficultyDialog = false }) {
                        Text(stringResource(Res.string.cancel), color = PaintBucket.white)
                    }
                },
                containerColor = PaintBucket.menuButtonDark
            )
        }
    }
}

@Composable
private fun IosGameHost(onBack: () -> Unit) {
    val tickState = remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var backWarnShown by remember { mutableStateOf(false) }
    val swipeAgainText = stringResource(Res.string.swipe_again_to_exit)

    LaunchedEffect(backWarnShown) {
        if (backWarnShown) {
            delay(3000)
            backWarnShown = false
        }
    }

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

    ImmersiveModeEffect()

    // Release any pointer locks left over from iOS touch cancellations (e.g. app backgrounded
    // mid-touch). Runs every time IosGameHost enters composition, which is every time the game
    // screen becomes visible — safe to call even before Logic is fully initialized.
    LaunchedEffect(Unit) {
        Logic.releaseAllPointers()
    }

    DisposableEffect(Unit) {
        onDispose {
            gameLoop.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack {
        if (backWarnShown) {
            onBack()
        } else {
            backWarnShown = true
        }
    }) {
        GameScreen(
            gameLoopTick = tickState,
            onSizeKnown = { w, h ->
                if (!initialized) {
                    initialized = true
                    Settings.isDemoMode = false   // stop demo loop before touching Logic state
                    Logic.initializeSettings(w.toInt(), h.toInt())
                    PaintBucket.initialize(Settings.screenRatio)
                    PaintBucket.initializePlatformColors(Storage.darkMode)
                    Sounds.initializeGame()
                    Drawing.initialize()
                    Logic.initialize()
                    Logic.composeReinitCallback = {
                        Logic.isInitialized = false
                        Logic.initializeSettings(w.toInt(), h.toInt())
                        PaintBucket.initialize(Settings.screenRatio)
                        PaintBucket.initializePlatformColors(Storage.darkMode)
                        Drawing.initialize()
                        Logic.initialize()
                    }
                    gameLoop.start()
                }
            }
        )

        if (backWarnShown) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = swipeAgainText,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

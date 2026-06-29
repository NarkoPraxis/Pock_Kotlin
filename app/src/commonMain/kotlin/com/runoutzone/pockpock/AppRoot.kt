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
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import enums.GameState
import gameobjects.BotConfig
import gameobjects.Settings
import gameobjects.puckstyle.skins.AxolotlSkinPainters
import gameobjects.puckstyle.skins.CatSkinPainters
import gameobjects.puckstyle.skins.DragonSkinPainters
import gameobjects.puckstyle.skins.PokPokSkinPainters
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.Drawing
import utility.FrameProfiler
import utility.GameLoop
import utility.Logic
import utility.PaintBucket
import utility.Sounds
import utility.Storage
import utility.UiStrobeClock
import utility.edgeSwipeBack

/** Provides the current dark-mode flag to any composable in the tree. */
val LocalDarkMode = compositionLocalOf { false }

private enum class Screen { Splash, MainMenu, Game, Settings, ScoreCalibration, BallDesigner, BallDesignerColor, CustomBallCreator, CustomColorPicker }

/**
 * Navigation guarded against rapid double-taps.
 *
 * Compose dispatches click events per-frame, and during a nav transition both the leaving and
 * entering destinations are briefly composed (so their buttons are both hit-testable). The settings
 * button and the Settings screen's back button occupy the same bottom-left corner, so rapidly
 * tapping that spot can fire a second [popBackStack] mid-transition — popping the start destination
 * (MainMenu) off an otherwise-empty back stack. The NavHost is then left with nothing to render
 * (the menu chrome vanishes) while [MenuDemoCanvas], which lives *outside* the NavHost and is keyed
 * on `isOnMainMenu`, keeps drawing with its blur. That is the "soft lock" the menu can't recover
 * from. Likewise, a double-tap on a navigate button can push the same destination twice.
 *
 * A navigation action is only honored when the current destination is fully [Lifecycle.State.RESUMED]
 * (i.e. no transition is in flight); taps that arrive mid-transition are dropped. This is the
 * standard debounce for Compose Navigation.
 */
private fun NavController.isTransitionIdle(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

private fun NavController.navigateIfIdle(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isTransitionIdle()) navigate(route, builder)
}

private fun NavController.popBackStackIfIdle() {
    if (isTransitionIdle()) popBackStack()
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    // Drive isOnMainMenu via a destination-changed listener for guaranteed synchronous updates.
    // `currentBackStackEntryAsState` is Flow-backed and can lag by a frame, leaving MenuDemoCanvas
    // mounted briefly after navigation — long enough to emit collision sounds.
    // Starts false: the app opens on the Splash destination, not the menu. The destination-changed
    // listener flips it true once MainMenu becomes active.
    var isOnMainMenu by remember { mutableStateOf(false) }
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

    // Zen mode: hide the menu chrome and unblur the demo game for endless passive viewing.
    var zenMode by remember { mutableStateOf(false) }

    // SVG painters used by PokPokSkin must be created in a composable scope. `painterResource`
    // is internally remembered, so this is effectively a one-time cost per app session.
    PokPokSkinPainters.load()
    DragonSkinPainters.load()
    AxolotlSkinPainters.load()
    CatSkinPainters.load()

    // Fire playMenuAmbiance every time MainMenu becomes the active route.
    LaunchedEffect(isOnMainMenu) {
        if (isOnMainMenu) Sounds.playMenuAmbiance()
    }

    // Drive Storage's time-derived getters (minutesUntilNextAd, canWatchAdNow)
    // so the "Next ad in X" label ticks down without leaving the screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            Storage.notifyTimeChanged()
        }
    }

    CompositionLocalProvider(LocalDarkMode provides darkMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // MenuDemoCanvas lives outside NavHost so its lifecycle is controlled entirely
            // by isOnMainMenu at AppRoot level. When false, the composable leaves the tree,
            // DisposableEffect.onDispose fires, and the game loop is stopped immediately.
            if (isOnMainMenu) {
                MenuDemoCanvas(zenMode = zenMode)
            }

            key(LocaleController.version) {
            NavHost(navController, startDestination = Screen.Splash.name) {
                composable(Screen.Splash.name) {
                    SplashScreen(onDone = {
                        navController.navigate(Screen.MainMenu.name) {
                            popUpTo(Screen.Splash.name) { inclusive = true }
                        }
                    })
                }
                composable(Screen.MainMenu.name) {
                    MainMenuScreen(
                        onPlayTapped = {
                            Settings.isDemoMode = false
                            Settings.isSinglePlayer = false
                            Sounds.playGameAmbiance()
                            navController.navigateIfIdle(Screen.Game.name)
                        },
                        onSinglePlayerTapped = { showDifficultyDialog = true },
                        onSettingsTapped = {
                            Settings.isDemoMode = false
                            navController.navigateIfIdle(Screen.Settings.name)
                        },
                        onCustomBallTapped = {
                            Settings.isDemoMode = false
                            navController.navigateIfIdle(Screen.BallDesigner.name)
                        },
                        onZenTapped = { zenMode = true },
                        zenMode = zenMode,
                        onExitZen = { zenMode = false },
                    )
                }
                composable(Screen.Game.name) {
                    IosGameHost(onBack = { navController.popBackStackIfIdle() })
                }
                composable(Screen.Settings.name) {
                    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack { navController.popBackStackIfIdle() }) {
                        SettingsScreen(
                            onBack = { navController.popBackStackIfIdle() },
                            onDarkModeChanged = { darkMode = it },
                            onScoreCalibrationTapped = { navController.navigateIfIdle(Screen.ScoreCalibration.name) }
                        )
                    }
                }
                composable(Screen.ScoreCalibration.name) {
                    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack { navController.popBackStackIfIdle() }) {
                        ScoreCalibrationScreen(onBack = { navController.popBackStackIfIdle() })
                    }
                }
                composable(Screen.BallDesigner.name) {
                    BallDesignerScreen(
                        onBack = { navController.popBackStackIfIdle() },
                        onNavigateToColor = {
                            navController.navigateIfIdle(Screen.BallDesignerColor.name) {
                                popUpTo(Screen.MainMenu.name) { inclusive = false }
                            }
                        }
                    )
                }
                composable(Screen.BallDesignerColor.name) {
                    BallDesignerColorScreen(
                        onBack = { navController.popBackStackIfIdle() },
                        onNavigateToStyle = {
                            navController.navigateIfIdle(Screen.BallDesigner.name) {
                                popUpTo(Screen.MainMenu.name) { inclusive = false }
                            }
                        }
                    )
                }
                composable(Screen.CustomBallCreator.name) {
                    CustomBallCreatorScreen(
                        onBack = { navController.popBackStackIfIdle() },
                        onNavigateToCcp = {
                            navController.navigateIfIdle(Screen.CustomColorPicker.name) {
                                popUpTo(Screen.MainMenu.name) { inclusive = false }
                            }
                        }
                    )
                }
                composable(Screen.CustomColorPicker.name) {
                    CustomColorPickerScreen(
                        onBack = { navController.popBackStackIfIdle() },
                        onNavigateToCbc = {
                            navController.navigateIfIdle(Screen.CustomBallCreator.name) {
                                popUpTo(Screen.MainMenu.name) { inclusive = false }
                            }
                        }
                    )
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
                                navController.navigateIfIdle(Screen.Game.name)
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
                    FrameProfiler.begin(FrameProfiler.S_LOGIC)
                    Logic.botBrain?.tick()
                    Logic.updateSpikes()
                    Logic.updateShieldFlatten()
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
                    FrameProfiler.end(FrameProfiler.S_LOGIC)
                }
                // Keep the static rainbow/prism cosmetics strobing in the ball-select popup (their
                // geometry is frozen there; only the hue cycles). No-op for any other style.
                UiStrobeClock.advance()
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
            // Auto-save any in-flight profiler session when leaving the game (e.g. back to the menu
            // to switch balls), so a forgotten-running recording is finalized instead of bleeding
            // into menu navigation. No-op when disabled or not recording.
            if (FrameProfiler.isSessionActive) FrameProfiler.endSession()
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
                    // Dev-only frame profiler: gated behind the compile-time DEV_TOOLS flag and the
                    // persisted Storage toggle, so a release build leaves it a no-op (zero hot-path cost).
                    FrameProfiler.enabled = FrameProfiler.DEV_TOOLS && Storage.profilerEnabled
                    Settings.isDemoMode = false   // stop demo loop before touching Logic state
                    Logic.initializeSettings(w.toInt(), h.toInt())
                    PaintBucket.initialize(Settings.screenRatio)
                    PaintBucket.initializePlatformColors(Storage.darkMode)
                    PaintBucket.applyPlayerHues()
                    Sounds.initializeGame()
                    Drawing.initialize()
                    Logic.initialize()
                    Logic.composeReinitCallback = {
                        Logic.isInitialized = false
                        Logic.initializeSettings(w.toInt(), h.toInt())
                        PaintBucket.initialize(Settings.screenRatio)
                        PaintBucket.initializePlatformColors(Storage.darkMode)
                        PaintBucket.applyPlayerHues()
                        Drawing.initialize()
                        Logic.initialize()
                    }
                    gameLoop.start()
                }
            }
        )

        // Dev-only profiling HUD + REC control (no-op when FrameProfiler.enabled is false).
        ProfilerHud(gameLoopTick = tickState, modifier = Modifier.align(Alignment.TopStart))

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

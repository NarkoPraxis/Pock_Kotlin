package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.menu.EdgePill
import com.runoutzone.pockpock.menu.MenuIconButton
import com.runoutzone.pockpock.menu.PillSide
import com.runoutzone.pockpock.menu.poppinsFamily
import enums.ChargeMeterStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PaintBucket
import utility.PlatformStorage
import utility.Sounds
import utility.Storage
import kotlinx.coroutines.launch

/**
 * Settings, translated from Plans/UIOverhaul/Screens/{Gameplay,Graphics,Sound}.svg.
 *
 * One screen with three tabs (Graphics / Sound / Gameplay) hosted in a [HorizontalPager] so they
 * can be swiped between (non-looping) as well as tapped in the bottom bar: a red back button (flush
 * left) and a brand-blue tab tray (flush right) whose selected icon sits on a white pill. The screen
 * themes to the dark-mode toggle (white text on a dark background; the red/blue chrome keeps its
 * white-on-color text). Toggling dark mode triggers an Activity recreate on Android (MainActivity
 * recreates on the "darkmode" pref change); the pager's [rememberPagerState] persists the selected
 * tab to survive it.
 *
 * Radio/toggle circles render like the Classic ball skin: inert = neutral grey; selected/on = the
 * low player's default Classic blue (fixed, not hue-responsive).
 */

private enum class SettingsTab { Gameplay, Graphics, Sound }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDarkModeChanged: (Boolean) -> Unit = {},
    onScoreCalibrationTapped: () -> Unit = {}
) {
    val poppins = poppinsFamily()
    val isDark = LocalDarkMode.current

    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.white

    // Page order matches the bottom tab tray (Graphics | Sound | Gameplay) so swiping lines up
    // with the icon positions. A HorizontalPager gives smooth drag-following transitions and is
    // non-looping by default (swiping past either end does nothing).
    val pageOrder = remember { listOf(SettingsTab.Graphics, SettingsTab.Sound, SettingsTab.Gameplay) }
    // rememberPagerState persists currentPage via its built-in saver, so the selected tab survives
    // the dark-mode Activity recreate (see MainActivity.darkModeListener).
    val pagerState = rememberPagerState(
        initialPage = pageOrder.indexOf(SettingsTab.Gameplay),
        pageCount = { pageOrder.size }
    )
    val scope = rememberCoroutineScope()
    val tab = pageOrder[pagerState.currentPage]
    fun goToTab(target: SettingsTab) {
        scope.launch { pagerState.animateScrollToPage(pageOrder.indexOf(target)) }
    }

    var ballSize by remember { mutableStateOf(Storage.ballSize) }
    var chargeSpeed by remember { mutableStateOf(Storage.chargeSpeed) }
    var gameSpeed by remember { mutableIntStateOf(Storage.gameSpeed) }
    var tailLength by remember { mutableIntStateOf(Storage.tailLength) }
    var pointsToWin by remember { mutableIntStateOf(Storage.loadPointsToWin()) }
    var timeLimit by remember { mutableIntStateOf(Storage.loadTimeLimit()) }
    var masterVol by remember { mutableIntStateOf(Storage.soundMasterVolume) }
    var bgVol by remember { mutableIntStateOf(Storage.soundBackgroundVolume) }
    var sfxVol by remember { mutableIntStateOf(Storage.soundSfxVolume) }
    var masterMuted by remember { mutableStateOf(Storage.soundMasterMuted) }
    var bgMuted by remember { mutableStateOf(Storage.soundBackgroundMuted) }
    var sfxMuted by remember { mutableStateOf(Storage.soundSfxMuted) }
    var highArrow by remember { mutableStateOf(Storage.highPlayerArrow) }
    var lowArrow by remember { mutableStateOf(Storage.lowPlayerArrow) }
    var highChargeMeter by remember { mutableStateOf(Storage.highPlayerChargeMeterStyle) }
    var lowChargeMeter by remember { mutableStateOf(Storage.lowPlayerChargeMeterStyle) }

    fun resetToDefaults() {
        PlatformStorage.saveString("settings", "ball_sizes", "default")
        PlatformStorage.saveString("settings", "charge_speed", "default")
        PlatformStorage.saveString("settings", "game_speed", "default")
        PlatformStorage.saveString("settings", "tail_length", "default")
        Storage.savePointsToWin(5)
        Storage.saveTimeLimit(0)
        Storage.saveSoundMasterVolume(70)
        Storage.saveSoundBackgroundVolume(100)
        Storage.saveSoundSfxVolume(70)
        Storage.saveSoundMasterMuted(false)
        Storage.saveSoundBackgroundMuted(false)
        Storage.saveSoundSfxMuted(false)
        PlatformStorage.saveBoolean("settings", "high_player_arrow", true)
        PlatformStorage.saveBoolean("settings", "low_player_arrow", true)
        Storage.saveHighPlayerChargeMeterStyle(ChargeMeterStyle.SideBar)
        Storage.saveLowPlayerChargeMeterStyle(ChargeMeterStyle.SideBar)
        Sounds.applyBackgroundVolume()
        ballSize = "default"
        chargeSpeed = 0.7f
        gameSpeed = 16
        tailLength = 20
        pointsToWin = 5
        timeLimit = 0
        masterVol = 70
        bgVol = 100
        sfxVol = 70
        masterMuted = false
        bgMuted = false
        sfxMuted = false
        highArrow = true
        lowArrow = true
        highChargeMeter = ChargeMeterStyle.SideBar
        lowChargeMeter = ChargeMeterStyle.SideBar
        // darkmode left untouched here so a reset doesn't recreate the Activity.
    }

    // Pre-resolve strings in composable scope.
    val strBack = stringResource(Res.string.back)
    val strBallSize = stringResource(Res.string.ball_size_label)
    val strSmall = stringResource(Res.string.ball_size_small)
    val strDefault = stringResource(Res.string.ball_size_default)
    val strLarge = stringResource(Res.string.ball_size_large)
    val strChargeSpeed = stringResource(Res.string.charge_speed_label)
    val strSlow = stringResource(Res.string.speed_slow)
    val strFast = stringResource(Res.string.speed_fast)
    val strGameSpeed = stringResource(Res.string.game_speed_label)
    val strPointsToWin = stringResource(Res.string.points_to_win_label)
    val strTimeLimit = stringResource(Res.string.time_limit_label)
    val strMaster = stringResource(Res.string.sound_master)
    val strBackground = stringResource(Res.string.sound_background)
    val strFx = stringResource(Res.string.sound_fx)
    val strMute = stringResource(Res.string.mute)
    val strMuted = stringResource(Res.string.muted)
    val strChargeArrows = stringResource(Res.string.charge_arrows)
    val strChargeMeter = stringResource(Res.string.charge_meter)
    val strTop = stringResource(Res.string.player_top_short)
    val strBottom = stringResource(Res.string.player_bottom_short)
    val strOn = stringResource(Res.string.toggle_on)
    val strOff = stringResource(Res.string.toggle_off)
    val strSideBar = stringResource(Res.string.charge_meter_sidebar)
    val strFullScreen = stringResource(Res.string.charge_meter_fullscreen)
    val strTailLength = stringResource(Res.string.tail_length_label)
    val strTailShort = stringResource(Res.string.tail_short)
    val strTailDefault = stringResource(Res.string.tail_default)
    val strTailLong = stringResource(Res.string.tail_long)
    val strDarkMode = stringResource(Res.string.dark_mode)
    val strSetScorePosition = stringResource(Res.string.set_score_position)
    val strResetDefaults = stringResource(Res.string.reset_defaults)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(bgColor)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val pillHeight = (screenH * 0.08f).coerceIn(58.dp, 92.dp)
        val bottomPad = screenH * 0.03f
        val circleD = (screenW * 0.14f).coerceIn(42.dp, 66.dp)
        // Reserve just the button band plus a thin border above it (was a tall 24dp gap).
        val contentBottomInset = pillHeight + bottomPad + 8.dp
        // Extra scroll runway so a tab's last control can be parked around mid-screen instead of
        // being pinned to the bottom bar. Sound's controls are short enough to never need it.
        val scrollRunway = screenH * 0.5f

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageTab = pageOrder[page]
            val extraBottom = if (pageTab == SettingsTab.Sound) 0.dp else scrollRunway
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp)
                    .padding(bottom = contentBottomInset + extraBottom),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            when (pageTab) {
                SettingsTab.Gameplay -> {
                    CircleRadioRow(
                        strBallSize,
                        listOf("small" to strSmall, "default" to strDefault, "large" to strLarge),
                        ballSize, circleD, poppins
                    ) { ballSize = it; PlatformStorage.saveString("settings", "ball_sizes", it) }

                    CircleRadioRow(
                        strChargeSpeed,
                        listOf(0.3f to strSlow, 0.7f to strDefault, 1.2f to strFast),
                        chargeSpeed, circleD, poppins
                    ) {
                        chargeSpeed = it
                        val key = when (it) { 0.3f -> "small"; 1.2f -> "large"; else -> "default" }
                        PlatformStorage.saveString("settings", "charge_speed", key)
                    }

                    CircleRadioRow(
                        strGameSpeed,
                        listOf(24 to strSlow, 16 to strDefault, 8 to strFast),
                        gameSpeed, circleD, poppins
                    ) {
                        gameSpeed = it
                        val key = when (it) { 24 -> "small"; 8 -> "large"; else -> "default" }
                        PlatformStorage.saveString("settings", "game_speed", key)
                    }

                    NumberDropdownRow(strPointsToWin, pointsToWin, poppins) {
                        pointsToWin = it; Storage.savePointsToWin(it)
                    }
                    NumberDropdownRow(strTimeLimit, timeLimit, poppins) {
                        timeLimit = it; Storage.saveTimeLimit(it)
                    }

                    SlantedBanner(
                        strResetDefaults, PaintBucket.dangerRed, poppins, fontSize = 20.sp
                    ) { resetToDefaults() }
                }

                SettingsTab.Graphics -> {
                    ChargeArrowsBlock(
                        strChargeArrows, strTop, highArrow, strBottom, lowArrow, circleD, poppins,
                        onP1 = { highArrow = it; PlatformStorage.saveBoolean("settings", "high_player_arrow", it) },
                        onP2 = { lowArrow = it; PlatformStorage.saveBoolean("settings", "low_player_arrow", it) }
                    )

                    ChargeMeterBlock(
                        strChargeMeter,
                        listOf(
                            ChargeMeterStyle.SideBar to strSideBar,
                            ChargeMeterStyle.FullScreen to strFullScreen
                        ),
                        strTop, highChargeMeter, strBottom, lowChargeMeter, circleD, poppins,
                        onP1 = { highChargeMeter = it; Storage.saveHighPlayerChargeMeterStyle(it) },
                        onP2 = { lowChargeMeter = it; Storage.saveLowPlayerChargeMeterStyle(it) }
                    )

                    CircleRadioRow(
                        strTailLength,
                        listOf(10 to strTailShort, 20 to strTailDefault, 40 to strTailLong),
                        tailLength, circleD, poppins
                    ) {
                        tailLength = it
                        val key = when (it) { 10 -> "small"; 40 -> "large"; else -> "default" }
                        PlatformStorage.saveString("settings", "tail_length", key)
                    }

                    DarkModeRow(strDarkMode, isDark, strOn, strOff, circleD, poppins) { next ->
                        PlatformStorage.saveBoolean("settings", "darkmode", next)
                        onDarkModeChanged(next)
                    }

                    ScorePositionBanner(strSetScorePosition, poppins, onScoreCalibrationTapped)
                }

                SettingsTab.Sound -> {
                    SoundSliderRow(strMaster, masterVol, masterMuted, strMute, strMuted,
                        onMute = {
                            masterMuted = !masterMuted
                            Storage.saveSoundMasterMuted(masterMuted)
                            Sounds.applyBackgroundVolume()
                        }) {
                        masterVol = it; Storage.saveSoundMasterVolume(it); Sounds.applyBackgroundVolume()
                    }
                    SoundSliderRow(strBackground, bgVol, bgMuted, strMute, strMuted,
                        onMute = {
                            bgMuted = !bgMuted
                            Storage.saveSoundBackgroundMuted(bgMuted)
                            Sounds.applyBackgroundVolume()
                        }) {
                        bgVol = it; Storage.saveSoundBackgroundVolume(it); Sounds.applyBackgroundVolume()
                    }
                    SoundSliderRow(strFx, sfxVol, sfxMuted, strMute, strMuted,
                        onMute = {
                            sfxMuted = !sfxMuted
                            Storage.saveSoundSfxMuted(sfxMuted)
                            Sounds.applyBackgroundVolume()
                        }) {
                        sfxVol = it; Storage.saveSoundSfxVolume(it); Sounds.applyBackgroundVolume()
                    }
                }
            }
            }
        }

        // ── Opaque themed strip behind the bottom bar so scrolled content can't peek below it. ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(contentBottomInset)
                .background(bgColor)
        )

        // ── Back button (flush left) ──
        EdgePill(
            side = PillSide.Start,
            color = PaintBucket.menuAccentRed,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomPad)
                .height(pillHeight)
        ) {
            MenuIconButton(
                painter = painterResource(Res.drawable.ic_menu_back),
                contentDescription = strBack,
                size = pillHeight * 0.36f,
                onClick = onBack
            )
        }

        // ── Tab tray (flush right) ──
        val tabIconSize = pillHeight * 0.42f
        EdgePill(
            side = PillSide.End,
            color = PaintBucket.menuAccentBlue,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPad)
                .height(pillHeight)
        ) {
            TabIcon(painterResource(Res.drawable.ic_menu_graphics), stringResource(Res.string.visual),
                tab == SettingsTab.Graphics, tabIconSize) { goToTab(SettingsTab.Graphics) }
            TabIcon(painterResource(Res.drawable.ic_menu_audio), stringResource(Res.string.sound),
                tab == SettingsTab.Sound, tabIconSize) { goToTab(SettingsTab.Sound) }
            TabIcon(painterResource(Res.drawable.ic_menu_gameplay), stringResource(Res.string.gameplay),
                tab == SettingsTab.Gameplay, tabIconSize) { goToTab(SettingsTab.Gameplay) }
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

@Composable
private fun titleColor() = if (LocalDarkMode.current) PaintBucket.white else PaintBucket.black

@Composable
private fun labelColor() = if (LocalDarkMode.current) Color(0xFFCCCCCC) else Color(0xFF222222)

@Composable
private fun SectionTitle(text: String, poppins: FontFamily, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, color = titleColor(), fontFamily = poppins, fontWeight = FontWeight.Bold, fontSize = 24.sp,
        textAlign = if (center) TextAlign.Center else null,
        modifier = if (center) modifier.fillMaxWidth() else modifier
    )
}

@Composable
private fun ItalicLabel(text: String, poppins: FontFamily) {
    Text(
        text, color = labelColor(), fontFamily = poppins,
        fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 15.sp
    )
}

/** A Classic-skin disc: a filled circle with an outline. Inert = grey, selected/on = low Classic blue. */
@Composable
private fun ClassicCircle(selected: Boolean, diameter: Dp, modifier: Modifier = Modifier) {
    val fill = if (selected) PaintBucket.menuAccentBlueSoft else PaintBucket.inertPrimary
    val outline = if (selected) PaintBucket.menuAccentBlue else PaintBucket.inertSecondary
    Canvas(modifier = modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val sw = r * 0.16f
        drawCircle(color = fill, radius = r - sw / 2f, center = center)
        drawCircle(color = outline, radius = r - sw / 2f, center = center, style = Stroke(width = sw))
    }
}

/** Centered title + a row of labelled radio circles (exactly one selected). */
@Composable
private fun <T> CircleRadioRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    circleD: Dp,
    poppins: FontFamily,
    onSelect: (T) -> Unit
) {
    Column {
        SectionTitle(title, poppins, center = true)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ItalicLabel(label, poppins)
                    Spacer(Modifier.height(8.dp))
                    ClassicCircle(value == selected, circleD, Modifier.clickable { onSelect(value) })
                }
            }
        }
    }
}

/** Display Charge Arrows: two centered single-circle on/off toggles (P1 / P2). */
@Composable
private fun ChargeArrowsBlock(
    title: String,
    p1Label: String, p1On: Boolean,
    p2Label: String, p2On: Boolean,
    circleD: Dp, poppins: FontFamily,
    onP1: (Boolean) -> Unit,
    onP2: (Boolean) -> Unit
) {
    Column {
        SectionTitle(title, poppins, center = true)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally)
        ) {
            ToggleColumn(p1Label, p1On, circleD, poppins) { onP1(!p1On) }
            ToggleColumn(p2Label, p2On, circleD, poppins) { onP2(!p2On) }
        }
    }
}

@Composable
private fun ToggleColumn(label: String, on: Boolean, circleD: Dp, poppins: FontFamily, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ItalicLabel(label, poppins)
        Spacer(Modifier.height(8.dp))
        ClassicCircle(on, circleD, Modifier.clickable { onToggle() })
    }
}

/**
 * Charge Meter: a per-player style selector (Side Bar / Full Screen). Laid out as an equal-weight
 * 3-column grid — the player label (Bottom / Top) is the first column — so the option circles line
 * up with the 3-option rows above and below it (e.g. the Tail Length row's Default / Long columns).
 */
@Composable
private fun <T> ChargeMeterBlock(
    title: String,
    options: List<Pair<T, String>>,
    p1Label: String, p1Selected: T,
    p2Label: String, p2Selected: T,
    circleD: Dp, poppins: FontFamily,
    onP1: (T) -> Unit,
    onP2: (T) -> Unit
) {
    Column {
        SectionTitle(title, poppins, center = true)
        Spacer(Modifier.height(10.dp))
        // Column headers (style labels) over the option columns; the first column (player label) has none.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f))
            options.forEach { (_, label) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ItalicLabel(label, poppins) }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChargeMeterPlayerRow(p1Label, options, p1Selected, circleD, poppins, onP1)
        Spacer(Modifier.height(12.dp))
        ChargeMeterPlayerRow(p2Label, options, p2Selected, circleD, poppins, onP2)
    }
}

@Composable
private fun <T> ChargeMeterPlayerRow(
    playerLabel: String,
    options: List<Pair<T, String>>,
    selected: T,
    circleD: Dp,
    poppins: FontFamily,
    onSelect: (T) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                playerLabel, color = labelColor(), fontFamily = poppins,
                fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 17.sp
            )
        }
        options.forEach { (value, _) ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ClassicCircle(value == selected, circleD, Modifier.clickable { onSelect(value) })
            }
        }
    }
}

/**
 * Dark Mode: centered title with two mutually-exclusive On/Off radio circles, laid out like the
 * Charge Arrows block. Tapping the already-selected option is a no-op so it doesn't needlessly
 * trigger the dark-mode Activity recreate.
 */
@Composable
private fun DarkModeRow(
    title: String,
    on: Boolean,
    onLabel: String,
    offLabel: String,
    circleD: Dp,
    poppins: FontFamily,
    onToggle: (Boolean) -> Unit
) {
    Column {
        SectionTitle(title, poppins, center = true)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally)
        ) {
            ToggleColumn(onLabel, on, circleD, poppins) { if (!on) onToggle(true) }
            ToggleColumn(offLabel, !on, circleD, poppins) { if (on) onToggle(false) }
        }
    }
}

/** A pale-lavender pill that opens a dropdown of 1..20 + ∞. 0 represents ∞ (unlimited). */
@Composable
private fun NumberDropdownRow(
    title: String,
    value: Int,
    poppins: FontFamily,
    onValue: (Int) -> Unit
) {
    val isDark = LocalDarkMode.current
    val pillBg = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuHashStroke
    val txt = if (isDark) PaintBucket.white else PaintBucket.black
    var expanded by remember { mutableStateOf(false) }
    val options = (1..20).toList() + 0  // 0 == ∞, shown last

    fun render(v: Int) = if (v == 0) "∞" else v.toString()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, color = txt, fontFamily = poppins, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, modifier = Modifier.weight(1f)
        )
        Box {
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Text(render(value), color = txt, fontFamily = poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }
            // DropdownMenu colors its container from surfaceContainer (not surface), so override
            // both — otherwise the menu keeps the default pale surface and the white text vanishes.
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = pillBg,
                    surfaceContainer = pillBg,
                    onSurface = txt
                )
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    // ~7 rows (48dp each + 16dp menu padding) then scroll.
                    modifier = Modifier.width(118.dp).heightIn(max = 352.dp)
                ) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    render(opt), color = txt, fontFamily = poppins,
                                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                                )
                            },
                            onClick = { onValue(opt); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

/** A bold-titled volume slider (pale track, blue thumb) with a speaker mute icon (blue / red-slashed). */
@Composable
private fun SoundSliderRow(
    title: String,
    value: Int,
    muted: Boolean,
    muteLabel: String,
    mutedLabel: String,
    onMute: () -> Unit,
    onValue: (Int) -> Unit
) {
    val poppins = poppinsFamily()
    val isDark = LocalDarkMode.current
    val inactiveTrack = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuHashStroke
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(title, poppins)
            Box(
                modifier = Modifier.size(46.dp).clickable { onMute() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        if (muted) Res.drawable.ic_menu_audio_muted else Res.drawable.ic_menu_audio
                    ),
                    contentDescription = if (muted) mutedLabel else muteLabel,
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(
                        if (muted) PaintBucket.menuAccentRed else PaintBucket.menuAccentBlue
                    )
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange = 0f..100f,
            enabled = !muted,
            modifier = Modifier.fillMaxWidth().alpha(if (muted) 0.4f else 1f),
            colors = SliderDefaults.colors(
                thumbColor = PaintBucket.menuAccentBlue,
                activeTrackColor = PaintBucket.menuAccentBlue,
                inactiveTrackColor = inactiveTrack,
                disabledThumbColor = PaintBucket.menuAccentBlue,
                disabledActiveTrackColor = PaintBucket.menuAccentBlue,
                disabledInactiveTrackColor = inactiveTrack
            )
        )
    }
}

/**
 * A symmetric parallelogram (both side edges share the same left-leaning diagonal), matching the
 * SVG banner. Top edge is shifted right of the bottom edge by [slantFraction] × height.
 */
private fun parallelogramShape(slantFraction: Float = 0.34f): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val slant = size.height * slantFraction
        val path = Path().apply {
            moveTo(slant, 0f)                        // top-left
            lineTo(size.width, 0f)                   // top-right
            lineTo(size.width - slant, size.height)  // bottom-right
            lineTo(0f, size.height)                  // bottom-left
            close()
        }
        return Outline.Generic(path)
    }
}

/** Centered slanted parallelogram button (matching the SVG banner), white bold label. */
@Composable
private fun SlantedBanner(
    text: String,
    color: Color,
    poppins: FontFamily,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    onClick: () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(64.dp)
                .clip(parallelogramShape())
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text, color = PaintBucket.white, fontFamily = poppins, fontWeight = FontWeight.Bold,
                fontSize = fontSize, maxLines = 1, softWrap = false
            )
        }
    }
}

/** Centered blue parallelogram button (matching the SVG) → score-position calibration screen. */
@Composable
private fun ScorePositionBanner(text: String, poppins: FontFamily, onClick: () -> Unit) =
    SlantedBanner(text, PaintBucket.menuAccentBlue, poppins, onClick = onClick)

/** One tab in the bottom tray: selected = blue icon on a white pill; unselected = white icon. */
@Composable
private fun RowScope.TabIcon(
    painter: Painter,
    contentDescription: String,
    selected: Boolean,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(iconSize + 20.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) PaintBucket.white else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(if (selected) PaintBucket.menuAccentBlue else PaintBucket.white)
        )
    }
}

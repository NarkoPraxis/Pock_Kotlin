package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import enums.ChargeMeterStyle
import org.jetbrains.compose.resources.stringResource
import pock_kotlin.app.generated.resources.*
import utility.PlatformStorage
import utility.Sounds
import utility.Storage

@Composable
fun SettingsScreen(onBack: () -> Unit, onDarkModeChanged: (Boolean) -> Unit = {}) {
    val isDark = LocalDarkMode.current
    val bgColor = if (isDark) Color(0xFF12102A) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF12102A)
    val textSecondary = if (isDark) Color(0xFFAAAAAA) else Color(0xFF555566)
    val labelColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF333344)
    val dividerColor = if (isDark) Color(0xFF444466) else Color(0xFFCCCCDD)

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
    var darkMode by remember { mutableStateOf(Storage.darkMode) }

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
        PlatformStorage.saveBoolean("settings", "darkmode", false)
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
        darkMode = false
        onDarkModeChanged(false)
    }

    // Pre-resolve all strings in composable scope
    val strBack = stringResource(Res.string.back)
    val strSettingsTitle = stringResource(Res.string.settings_title)
    val strGameplay = stringResource(Res.string.gameplay)
    val strBallSizeLabel = stringResource(Res.string.ball_size_label)
    val strBallSizeSmall = stringResource(Res.string.ball_size_small)
    val strBallSizeDefault = stringResource(Res.string.ball_size_default)
    val strBallSizeLarge = stringResource(Res.string.ball_size_large)
    val strChargeSpeedLabel = stringResource(Res.string.charge_speed_label)
    val strSpeedSlow = stringResource(Res.string.speed_slow)
    val strSpeedNormal = stringResource(Res.string.speed_normal)
    val strSpeedFast = stringResource(Res.string.speed_fast)
    val strSpeedFastest = stringResource(Res.string.speed_fastest)
    val strGameSpeedLabel = stringResource(Res.string.game_speed_label)
    val strPointsToWinLabel = stringResource(Res.string.points_to_win_label)
    val strTimeLimitLabel = stringResource(Res.string.time_limit_label)
    val strMinuteShort = stringResource(Res.string.minute_short)
    val strSound = stringResource(Res.string.sound)
    val strSoundMaster = stringResource(Res.string.sound_master)
    val strSoundBackground = stringResource(Res.string.sound_background)
    val strSoundFx = stringResource(Res.string.sound_fx)
    val strMute = stringResource(Res.string.mute)
    val strMuted = stringResource(Res.string.muted)
    val strVisual = stringResource(Res.string.visual)
    val strTailLengthLabel = stringResource(Res.string.tail_length_label)
    val strTailNone = stringResource(Res.string.tail_none)
    val strTailShort = stringResource(Res.string.tail_short)
    val strTailDefault = stringResource(Res.string.tail_default)
    val strTailLong = stringResource(Res.string.tail_long)
    val strHighPlayerArrow = stringResource(Res.string.high_player_arrow)
    val strLowPlayerArrow = stringResource(Res.string.low_player_arrow)
    val strHighChargeMeter = stringResource(Res.string.high_player_charge_meter)
    val strLowChargeMeter = stringResource(Res.string.low_player_charge_meter)
    val strSideBar = stringResource(Res.string.charge_meter_sidebar)
    val strFullScreen = stringResource(Res.string.charge_meter_fullscreen)
    val strMeterNone = stringResource(Res.string.charge_meter_none)
    val strDarkMode = stringResource(Res.string.dark_mode)
    val strResetDefaults = stringResource(Res.string.reset_defaults)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(strBack, color = textPrimary, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(strSettingsTitle, color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        HorizontalDivider(color = dividerColor)
        SettingsSectionLabel(strGameplay, textSecondary)

        SettingsSectionLabel(strBallSizeLabel, textSecondary)
        SegmentedSelector(
            options = listOf("small" to strBallSizeSmall, "default" to strBallSizeDefault, "large" to strBallSizeLarge),
            selected = ballSize,
            onSelect = { ballSize = it; PlatformStorage.saveString("settings", "ball_sizes", it) }
        )

        SettingsSectionLabel(strChargeSpeedLabel, textSecondary)
        SegmentedSelector(
            options = listOf(0.3f to strSpeedSlow, 0.7f to strSpeedNormal, 1.2f to strSpeedFast, 2f to strSpeedFastest),
            selected = chargeSpeed,
            onSelect = {
                chargeSpeed = it
                val key = when (it) { 0.3f -> "small"; 1.2f -> "large"; 2f -> "fastest"; else -> "default" }
                PlatformStorage.saveString("settings", "charge_speed", key)
            }
        )

        SettingsSectionLabel(strGameSpeedLabel, textSecondary)
        SegmentedSelector(
            options = listOf(24 to strSpeedSlow, 16 to strSpeedNormal, 8 to strSpeedFast),
            selected = gameSpeed,
            onSelect = {
                gameSpeed = it
                val key = when (it) { 24 -> "small"; 8 -> "large"; else -> "default" }
                PlatformStorage.saveString("settings", "game_speed", key)
            }
        )

        SettingsSectionLabel(
            if (pointsToWin == 0) "$strPointsToWinLabel: ∞" else "$strPointsToWinLabel: $pointsToWin",
            textSecondary
        )
        Slider(
            value = pointsToWin.toFloat(),
            onValueChange = {
                pointsToWin = it.toInt()
                Storage.savePointsToWin(pointsToWin)
            },
            valueRange = 0f..20f,
            steps = 19
        )

        SettingsSectionLabel(
            if (timeLimit == 0) "$strTimeLimitLabel: ∞" else "$strTimeLimitLabel: $timeLimit $strMinuteShort",
            textSecondary
        )
        Slider(
            value = timeLimit.toFloat(),
            onValueChange = {
                timeLimit = it.toInt()
                Storage.saveTimeLimit(timeLimit)
            },
            valueRange = 0f..20f,
            steps = 19
        )

        HorizontalDivider(color = dividerColor)
        SettingsSectionLabel(strSound, textSecondary)

        VolumeSliderRow(strSoundMaster, masterVol, masterMuted, labelColor, textPrimary,
            muteLabel = strMute, mutedLabel = strMuted,
            onMute = {
                val next = !masterMuted
                masterMuted = next
                Storage.saveSoundMasterMuted(next)
                Sounds.applyBackgroundVolume()
            }) {
            masterVol = it
            Storage.saveSoundMasterVolume(it)
            Sounds.applyBackgroundVolume()
        }
        VolumeSliderRow(strSoundBackground, bgVol, bgMuted, labelColor, textPrimary,
            muteLabel = strMute, mutedLabel = strMuted,
            onMute = {
                val next = !bgMuted
                bgMuted = next
                Storage.saveSoundBackgroundMuted(next)
                Sounds.applyBackgroundVolume()
            }) {
            bgVol = it
            Storage.saveSoundBackgroundVolume(it)
            Sounds.applyBackgroundVolume()
        }
        VolumeSliderRow(strSoundFx, sfxVol, sfxMuted, labelColor, textPrimary,
            muteLabel = strMute, mutedLabel = strMuted,
            onMute = {
                val next = !sfxMuted
                sfxMuted = next
                Storage.saveSoundSfxMuted(next)
                Sounds.applyBackgroundVolume()
            }) {
            sfxVol = it
            Storage.saveSoundSfxVolume(it)
            Sounds.applyBackgroundVolume()
        }

        HorizontalDivider(color = dividerColor)
        SettingsSectionLabel(strVisual, textSecondary)

        SettingsSectionLabel(strTailLengthLabel, textSecondary)
        SegmentedSelector(
            options = listOf(0 to strTailNone, 10 to strTailShort, 20 to strTailDefault, 40 to strTailLong),
            selected = tailLength,
            onSelect = {
                tailLength = it
                val key = when (it) { 0 -> "none"; 10 -> "small"; 40 -> "large"; else -> "default" }
                PlatformStorage.saveString("settings", "tail_length", key)
            }
        )

        ToggleRow(strHighPlayerArrow, highArrow, labelColor) {
            highArrow = it; PlatformStorage.saveBoolean("settings", "high_player_arrow", it)
        }
        ToggleRow(strLowPlayerArrow, lowArrow, labelColor) {
            lowArrow = it; PlatformStorage.saveBoolean("settings", "low_player_arrow", it)
        }
        SettingsSectionLabel(strHighChargeMeter, textSecondary)
        SegmentedSelector(
            options = listOf(
                ChargeMeterStyle.SideBar    to strSideBar,
                ChargeMeterStyle.FullScreen to strFullScreen,
                ChargeMeterStyle.None       to strMeterNone
            ),
            selected = highChargeMeter,
            onSelect = { highChargeMeter = it; Storage.saveHighPlayerChargeMeterStyle(it) }
        )
        SettingsSectionLabel(strLowChargeMeter, textSecondary)
        SegmentedSelector(
            options = listOf(
                ChargeMeterStyle.SideBar    to strSideBar,
                ChargeMeterStyle.FullScreen to strFullScreen,
                ChargeMeterStyle.None       to strMeterNone
            ),
            selected = lowChargeMeter,
            onSelect = { lowChargeMeter = it; Storage.saveLowPlayerChargeMeterStyle(it) }
        )
        ToggleRow(strDarkMode, darkMode, textSecondary) {
            darkMode = it
            PlatformStorage.saveBoolean("settings", "darkmode", it)
            onDarkModeChanged(it)
        }

        HorizontalDivider(color = dividerColor)
        TextButton(
            onClick = { resetToDefaults() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strResetDefaults, color = Color(0xFFFF6666), fontSize = 16.sp)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionLabel(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 13.sp)
}

@Composable
private fun <T> SegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val isDark = LocalDarkMode.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Button(
                onClick = { onSelect(value) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF6666AA)
                                     else if (isDark) Color(0xFF333344) else Color(0xFFD4C8FF),
                    contentColor = if (isSelected || isDark) Color.White else Color(0xFF12102A)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    value: Int,
    muted: Boolean,
    labelColor: Color,
    valueColor: Color,
    muteLabel: String = "Mute",
    mutedLabel: String = "Muted",
    onMute: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, color = labelColor, fontSize = 14.sp)
            TextButton(onClick = onMute) {
                Text(
                    if (muted) mutedLabel else muteLabel,
                    color = if (muted) Color(0xFFFF6666) else Color(0xFF8888AA),
                    fontSize = 12.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (muted) 0.4f else 1f),
                enabled = !muted
            )
            Spacer(Modifier.width(8.dp))
            Text("$value%", color = valueColor, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, labelColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

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
import utility.PaintBucket
import utility.PlatformStorage
import utility.Sounds
import utility.Storage

@Composable
fun SettingsScreen(onBack: () -> Unit) {
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
    var highCharge by remember { mutableStateOf(Storage.highPlayerChargeFill) }
    var lowCharge by remember { mutableStateOf(Storage.lowPlayerChargeFill) }
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
        PlatformStorage.saveBoolean("settings", "high_player_charge_fill", true)
        PlatformStorage.saveBoolean("settings", "low_player_charge_fill", true)
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
        highCharge = true
        lowCharge = true
        darkMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaintBucket.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("SETTINGS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        HorizontalDivider(color = Color(0xFF444466))
        SettingsSectionLabel("Gameplay")

        SettingsSectionLabel("Ball Size")
        SegmentedSelector(
            options = listOf("small" to "Small", "default" to "Default", "large" to "Large"),
            selected = ballSize,
            onSelect = { ballSize = it; PlatformStorage.saveString("settings", "ball_sizes", it) }
        )

        SettingsSectionLabel("Charge Speed")
        SegmentedSelector(
            options = listOf(0.3f to "Slow", 0.7f to "Normal", 1.2f to "Fast", 2f to "Fastest"),
            selected = chargeSpeed,
            onSelect = {
                chargeSpeed = it
                val key = when (it) { 0.3f -> "small"; 1.2f -> "large"; 2f -> "fastest"; else -> "default" }
                PlatformStorage.saveString("settings", "charge_speed", key)
            }
        )

        SettingsSectionLabel("Game Speed")
        SegmentedSelector(
            options = listOf(24 to "Slow", 16 to "Normal", 8 to "Fast"),
            selected = gameSpeed,
            onSelect = {
                gameSpeed = it
                val key = when (it) { 24 -> "small"; 8 -> "large"; else -> "default" }
                PlatformStorage.saveString("settings", "game_speed", key)
            }
        )

        SettingsSectionLabel(if (pointsToWin == 0) "Points to Win: ∞" else "Points to Win: $pointsToWin")
        Slider(
            value = pointsToWin.toFloat(),
            onValueChange = {
                pointsToWin = it.toInt()
                Storage.savePointsToWin(pointsToWin)
            },
            valueRange = 0f..20f,
            steps = 19
        )

        SettingsSectionLabel(if (timeLimit == 0) "Time Limit: ∞" else "Time Limit: $timeLimit min")
        Slider(
            value = timeLimit.toFloat(),
            onValueChange = {
                timeLimit = it.toInt()
                Storage.saveTimeLimit(timeLimit)
            },
            valueRange = 0f..20f,
            steps = 19
        )

        HorizontalDivider(color = Color(0xFF444466))
        SettingsSectionLabel("Sound")

        VolumeSliderRow("Master", masterVol, masterMuted, onMute = {
            val next = !masterMuted
            masterMuted = next
            Storage.saveSoundMasterMuted(next)
            Sounds.applyBackgroundVolume()
        }) {
            masterVol = it
            Storage.saveSoundMasterVolume(it)
            Sounds.applyBackgroundVolume()
        }
        VolumeSliderRow("Background", bgVol, bgMuted, onMute = {
            val next = !bgMuted
            bgMuted = next
            Storage.saveSoundBackgroundMuted(next)
            Sounds.applyBackgroundVolume()
        }) {
            bgVol = it
            Storage.saveSoundBackgroundVolume(it)
            Sounds.applyBackgroundVolume()
        }
        VolumeSliderRow("Sound FX", sfxVol, sfxMuted, onMute = {
            val next = !sfxMuted
            sfxMuted = next
            Storage.saveSoundSfxMuted(next)
            Sounds.applyBackgroundVolume()
        }) {
            sfxVol = it
            Storage.saveSoundSfxVolume(it)
            Sounds.applyBackgroundVolume()
        }

        HorizontalDivider(color = Color(0xFF444466))
        SettingsSectionLabel("Visual")

        SettingsSectionLabel("Tail Length")
        SegmentedSelector(
            options = listOf(0 to "None", 10 to "Short", 20 to "Default", 40 to "Long"),
            selected = tailLength,
            onSelect = {
                tailLength = it
                val key = when (it) { 0 -> "none"; 10 -> "small"; 40 -> "large"; else -> "default" }
                PlatformStorage.saveString("settings", "tail_length", key)
            }
        )

        ToggleRow("High Player Arrow", highArrow) {
            highArrow = it; PlatformStorage.saveBoolean("settings", "high_player_arrow", it)
        }
        ToggleRow("Low Player Arrow", lowArrow) {
            lowArrow = it; PlatformStorage.saveBoolean("settings", "low_player_arrow", it)
        }
        ToggleRow("High Player Charge Fill", highCharge) {
            highCharge = it; PlatformStorage.saveBoolean("settings", "high_player_charge_fill", it)
        }
        ToggleRow("Low Player Charge Fill", lowCharge) {
            lowCharge = it; PlatformStorage.saveBoolean("settings", "low_player_charge_fill", it)
        }
        ToggleRow("Dark Mode", darkMode) {
            darkMode = it
            PlatformStorage.saveBoolean("settings", "darkmode", it)
        }

        HorizontalDivider(color = Color(0xFF444466))
        TextButton(
            onClick = { resetToDefaults() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Defaults", color = Color(0xFFFF6666), fontSize = 16.sp)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text = text, color = Color(0xFFAAAAAA), fontSize = 13.sp)
}

@Composable
private fun <T> SegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Button(
                onClick = { onSelect(value) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF6666AA) else Color(0xFF333344),
                    contentColor = Color.White
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
    onMute: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp)
            TextButton(onClick = onMute) {
                Text(
                    if (muted) "Muted" else "Mute",
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
            Text("$value%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

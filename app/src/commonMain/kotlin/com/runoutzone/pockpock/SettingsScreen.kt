package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utility.PaintBucket
import utility.Storage

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var ballSize by remember { mutableStateOf(Storage.ballSize) }
    var gameSpeed by remember { mutableStateOf(Storage.gameSpeed) }
    var tailLength by remember { mutableStateOf(Storage.tailLength) }
    var pointsToWin by remember { mutableIntStateOf(Storage.loadPointsToWin()) }
    var masterVol by remember { mutableIntStateOf(Storage.soundMasterVolume) }
    var bgVol by remember { mutableIntStateOf(Storage.soundBackgroundVolume) }
    var sfxVol by remember { mutableIntStateOf(Storage.soundSfxVolume) }
    var highArrow by remember { mutableStateOf(Storage.highPlayerArrow) }
    var lowArrow by remember { mutableStateOf(Storage.lowPlayerArrow) }
    var highCharge by remember { mutableStateOf(Storage.highPlayerChargeFill) }
    var lowCharge by remember { mutableStateOf(Storage.lowPlayerChargeFill) }

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

        SettingsSectionLabel("Ball Size")
        SegmentedSelector(
            options = listOf("small" to "Small", "default" to "Default", "large" to "Large"),
            selected = ballSize,
            onSelect = { ballSize = it; utility.PlatformStorage.saveString("settings", "ball_sizes", it) }
        )

        SettingsSectionLabel("Game Speed")
        SegmentedSelector(
            options = listOf(24 to "Slow", 16 to "Normal", 8 to "Fast"),
            selected = gameSpeed,
            onSelect = {
                gameSpeed = it
                val key = when (it) { 24 -> "slow"; 8 -> "large"; else -> "default" }
                utility.PlatformStorage.saveString("settings", "game_speed", key)
            }
        )

        SettingsSectionLabel("Tail Length")
        SegmentedSelector(
            options = listOf(0 to "None", 10 to "Short", 20 to "Default", 40 to "Long"),
            selected = tailLength,
            onSelect = {
                tailLength = it
                val key = when (it) { 0 -> "none"; 10 -> "small"; 40 -> "large"; else -> "default" }
                utility.PlatformStorage.saveString("settings", "tail_length", key)
            }
        )

        SettingsSectionLabel("Points to Win: $pointsToWin")
        Slider(
            value = pointsToWin.toFloat(),
            onValueChange = {
                pointsToWin = it.toInt()
                utility.PlatformStorage.saveString("settings", "points_to_win", pointsToWin.toString())
            },
            valueRange = 1f..10f,
            steps = 8
        )

        HorizontalDivider(color = Color(0xFF444466))
        SettingsSectionLabel("Sound")

        VolumeSliderRow("Master", masterVol, onMute = {
            Storage.saveSoundMasterMuted(!Storage.soundMasterMuted)
        }) {
            masterVol = it; Storage.saveSoundMasterVolume(it)
        }
        VolumeSliderRow("Background", bgVol, onMute = {
            Storage.saveSoundBackgroundMuted(!Storage.soundBackgroundMuted)
        }) {
            bgVol = it; Storage.saveSoundBackgroundVolume(it)
        }
        VolumeSliderRow("Sound FX", sfxVol, onMute = {
            Storage.saveSoundSfxMuted(!Storage.soundSfxMuted)
        }) {
            sfxVol = it; Storage.saveSoundSfxVolume(it)
        }

        HorizontalDivider(color = Color(0xFF444466))
        SettingsSectionLabel("Controls")

        ToggleRow("High Player Arrow", highArrow) {
            highArrow = it; utility.PlatformStorage.saveBoolean("settings", "high_player_arrow", it)
        }
        ToggleRow("Low Player Arrow", lowArrow) {
            lowArrow = it; utility.PlatformStorage.saveBoolean("settings", "low_player_arrow", it)
        }
        ToggleRow("High Player Charge Fill", highCharge) {
            highCharge = it; utility.PlatformStorage.saveBoolean("settings", "high_player_charge_fill", it)
        }
        ToggleRow("Low Player Charge Fill", lowCharge) {
            lowCharge = it; utility.PlatformStorage.saveBoolean("settings", "low_player_charge_fill", it)
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
private fun VolumeSliderRow(label: String, value: Int, onMute: () -> Unit, onValueChange: (Int) -> Unit) {
    Column {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
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

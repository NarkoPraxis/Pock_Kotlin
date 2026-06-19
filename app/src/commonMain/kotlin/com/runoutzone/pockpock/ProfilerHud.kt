package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import gameobjects.Settings
import utility.FrameProfiler

/**
 * Dev-only in-game profiler HUD. Live rolling avg / worst / jank from [FrameProfiler], the active
 * per-section breakdown, and a REC control that wraps [FrameProfiler.startSession]/[endSession].
 * The session is tagged with the current ball type and a toggleable before/after label, producing
 * exactly one JSON file per capture (its path is surfaced here so you know what to `adb pull`).
 *
 * Rendered only when [FrameProfiler.enabled]; the convenience is the HUD, the deliverable is the file.
 */
@Composable
fun ProfilerHud(gameLoopTick: State<Int>, modifier: Modifier = Modifier) {
    if (!FrameProfiler.enabled) return

    @Suppress("UNUSED_EXPRESSION")
    gameLoopTick.value   // recompose every frame so the live numbers update

    var label by remember { mutableStateOf("before") }
    var lastPath by remember { mutableStateOf<String?>(null) }
    val recording = FrameProfiler.isSessionActive

    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        val mono = FontFamily.Monospace
        val avg = FrameProfiler.hudAvgMs
        val worst = FrameProfiler.hudWorstMs
        val jankColor = if (FrameProfiler.hudJank > 0) Color(0xFFFFB300) else Color(0xFF8BC34A)
        HudLine("avg ${fmt(avg)}  worst ${fmt(worst)}", mono)
        HudLine("jank ${FrameProfiler.hudJank}/120", mono, jankColor)

        // Per-section breakdown of the last completed frame (only populated while a session records).
        for (s in 0 until FrameProfiler.SECTION_COUNT) {
            HudLine("  ${FrameProfiler.sectionNames[s].padEnd(9)} ${fmt(FrameProfiler.hudSectionMs(s))}", mono)
        }

        Row {
            // before/after label toggle
            Text(
                "[$label]",
                color = Color(0xFF4FC3F7),
                fontFamily = mono,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable(enabled = !recording) {
                        label = if (label == "before") "after" else "before"
                    }
            )
            // REC / STOP
            Text(
                if (recording) "■ STOP" else "● REC",
                color = if (recording) Color(0xFFFF5252) else Color(0xFF69F0AE),
                fontFamily = mono,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    if (recording) {
                        lastPath = FrameProfiler.endSession()
                    } else {
                        FrameProfiler.startSession(Settings.highBallProfileName(), label)
                        lastPath = null
                    }
                }
            )
        }

        lastPath?.let {
            if (it.isNotEmpty()) Text(
                "saved: …${it.takeLast(40)}",
                color = Color(0xFFB0BEC5),
                fontFamily = mono,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun HudLine(text: String, mono: FontFamily, color: Color = Color.White) =
    Text(text, color = color, fontFamily = mono, fontSize = 11.sp)

/** One-decimal millisecond formatter, allocation-light, no platform String.format dependency. */
private fun fmt(v: Float): String {
    val scaled = (v * 10f + 0.5f).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

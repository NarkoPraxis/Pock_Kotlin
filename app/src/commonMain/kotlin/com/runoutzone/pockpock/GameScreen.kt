package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import enums.GameState
import gameobjects.Settings
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import utility.Drawing
import utility.drawGameFrame
import utility.onGamePointerDown
import utility.onGamePointerMove
import utility.onGamePointerUp

@Composable
fun GameScreen(
    gameLoopTick: State<Int>,
    onSizeKnown: (width: Float, height: Float) -> Unit = { _, _ -> }
) {
    val textMeasurer = rememberTextMeasurer()
    Drawing.initializeTextMeasurer(textMeasurer)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    onSizeKnown(size.width.toFloat(), size.height.toFloat())
                }
                .pointerInput(Unit) {
                    // GameScreen owns all pointer-to-player assignment.
                    // Logic receives semantic IDs only: 0 = high player, 1 = low player.
                    // Using raw Long IDs avoids the Int-overflow / sentinel-collision risk
                    // that caused the lockedPointerId freeze bug on physical iOS devices.
                    var highRawId: Long? = null
                    var lowRawId:  Long? = null
                    var highLastX = 0f; var highLastY = 0f
                    var lowLastX  = 0f; var lowLastY  = 0f

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)

                            // Verify live assignments against the currently-pressed set.
                            // This auto-releases any player whose pointer was cancelled by
                            // the OS without a normal up event (e.g. iOS touchesCancelled).
                            val active = event.changes.filter { it.pressed }.map { it.id.value }.toSet()
                            if (highRawId != null && highRawId !in active) {
                                onGamePointerUp(highLastX, highLastY, 0)
                                highRawId = null
                            }
                            if (lowRawId != null && lowRawId !in active) {
                                onGamePointerUp(lowLastX, lowLastY, 1)
                                lowRawId = null
                            }

                            event.changes.forEach { change ->
                                val rawId = change.id.value
                                val x = change.position.x
                                val y = change.position.y

                                when {
                                    change.pressed && !change.previousPressed -> {
                                        // Assign to the player whose side of the screen was touched.
                                        // GameScreen enforces single-pointer-per-player; cross-midline
                                        // drags work because we track by rawId, not position.
                                        val isHighSide = y < Settings.middleY
                                        // In single-player, the high side is the bot. Allow touches there
                                        // only during ball selection (so the player can change the bot's
                                        // ball); during Play the bot's half is untouchable.
                                        val highSideAllowed = !Settings.isSinglePlayer ||
                                            Settings.gameState == GameState.BallSelection
                                        if (isHighSide && highRawId == null && highSideAllowed) {
                                            highRawId = rawId; highLastX = x; highLastY = y
                                            onGamePointerDown(x, y, 0)
                                        } else if (!isHighSide && lowRawId == null) {
                                            lowRawId = rawId; lowLastX = x; lowLastY = y
                                            onGamePointerDown(x, y, 1)
                                        }
                                    }
                                    change.pressed -> {
                                        if (rawId == highRawId) {
                                            highLastX = x; highLastY = y
                                            onGamePointerMove(x, y, 0)
                                        } else if (rawId == lowRawId) {
                                            lowLastX = x; lowLastY = y
                                            onGamePointerMove(x, y, 1)
                                        }
                                    }
                                    !change.pressed && change.previousPressed -> {
                                        if (rawId == highRawId) {
                                            onGamePointerUp(x, y, 0); highRawId = null
                                        } else if (rawId == lowRawId) {
                                            onGamePointerUp(x, y, 1); lowRawId = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            gameLoopTick.value

            drawGameFrame()
        }

        TipOverlay(gameLoopTick)
    }
}

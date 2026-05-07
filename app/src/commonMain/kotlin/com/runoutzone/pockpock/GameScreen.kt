package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun GameScreen(
    gameLoopTick: State<Int>,
    onSizeKnown: (width: Float, height: Float) -> Unit = { _, _ -> }
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onSizeKnown(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main)
                        // Touch routing implemented in step 09
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        gameLoopTick.value

        drawRect(Color(0xFF1A1A1A), topLeft = Offset.Zero, size = this.size)
    }
}

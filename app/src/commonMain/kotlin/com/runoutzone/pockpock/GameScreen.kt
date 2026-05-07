package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import utility.drawGameFrame

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
                        // Touch routing implemented in step 10
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        gameLoopTick.value

        drawGameFrame()
    }
}

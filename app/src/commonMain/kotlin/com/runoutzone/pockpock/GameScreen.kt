package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import utility.drawGameFrame
import utility.onGamePointerDown
import utility.onGamePointerMove
import utility.onGamePointerUp

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
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { change ->
                            val x = change.position.x
                            val y = change.position.y
                            val pointerId = change.id.value.toInt()
                            when (event.type) {
                                PointerEventType.Press -> onGamePointerDown(x, y, pointerId)
                                PointerEventType.Move -> onGamePointerMove(x, y, pointerId)
                                PointerEventType.Release -> onGamePointerUp(x, y, pointerId)
                                else -> {}
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
}

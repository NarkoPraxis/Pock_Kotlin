package com.runoutzone.pockpock.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import gameobjects.Settings
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal snap carousel — the X-axis analog of [VerticalOptionCarousel]. Used by the Ball
 * Designer's unified ("U") view to scroll through fully composed ball types: items are laid out
 * left-to-right, the centred item is emphasised with a mild grow (and neighbours shrink), and the
 * whole strip snaps to an item on release.
 *
 * Interaction matches the vertical carousel:
 *  - **Tap** any visible item → [onTap] (caller decides what a tap means; the tapped item also
 *    animates to centre).
 *  - **Horizontal drag** scrolls; on release it snaps to the nearest item and calls [onSnap].
 *  - [onCenterChanged] fires live with the index nearest the horizontal centre while scrolling.
 *
 * [drawItem] paints one item in absolute screen space. `isCenter` flags the centred item; `radius`
 * already folds in the grow/shrink so callers size content directly from it. `isPressed` is true
 * while that item is held. `cellWidth`/`cellHeight` give the item's clip cell.
 *
 * [itemsVisible] controls how many items fit across the width (~the vertical carousel's 3.2). Full
 * composed balls are large, so the default shows fewer than the vertical part-carousel.
 */
@Composable
fun HorizontalOptionCarousel(
    itemCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    itemsVisible: Float = 2.8f,
    onTap: (Int) -> Unit = {},
    onSnap: (Int) -> Unit = {},
    onCenterChanged: (Int) -> Unit = {},
    drawItem: DrawScope.(
        index: Int, centerX: Float, centerY: Float, radius: Float,
        isCenter: Boolean, isPressed: Boolean, cellWidth: Float, cellHeight: Float
    ) -> Unit,
) {
    if (itemCount <= 0) { Box(modifier); return }

    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var pressedIndex by remember { mutableIntStateOf(-1) }

    val spacing = if (widthPx > 0) widthPx / itemsVisible else 1f

    // scroll.value == selectedIndex*spacing centres that item. Animatable so taps and snap-on-release
    // ease smoothly; drags snapTo for 1:1 tracking.
    val scroll = remember { Animatable(0f) }

    fun maxScroll() = ((itemCount - 1).coerceAtLeast(0)) * spacing

    LaunchedEffect(selectedIndex, spacing) {
        if (!dragging && spacing > 1f) scroll.animateTo((selectedIndex * spacing).coerceIn(0f, maxScroll()))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { widthPx = it.width; heightPx = it.height }
            .pointerInput(itemCount, spacing) {
                fun indexAt(x: Float): Int {
                    val contentCenterX = size.width / 2f
                    return ((x - contentCenterX + scroll.value) / spacing)
                        .roundToInt().coerceIn(0, itemCount - 1)
                }
                detectTapGestures(
                    onPress = { offset ->
                        if (spacing <= 1f) return@detectTapGestures
                        pressedIndex = indexAt(offset.x)
                        tryAwaitRelease()
                        pressedIndex = -1
                    },
                    onTap = { offset ->
                        if (spacing <= 1f) return@detectTapGestures
                        val idx = indexAt(offset.x)
                        scope.launch { scroll.animateTo((idx * spacing).coerceIn(0f, maxScroll())) }
                        onCenterChanged(idx)
                        onTap(idx)
                    }
                )
            }
            .pointerInput(itemCount, spacing) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (spacing > 1f) {
                            val idx = (scroll.value / spacing).roundToInt().coerceIn(0, itemCount - 1)
                            scope.launch { scroll.animateTo((idx * spacing).coerceIn(0f, maxScroll())) }
                            onSnap(idx)
                        }
                    },
                    onDragCancel = { dragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        val v = (scroll.value - dragAmount).coerceIn(0f, maxScroll())
                        scroll.snapTo(v)
                        if (spacing > 1f) onCenterChanged((v / spacing).roundToInt().coerceIn(0, itemCount - 1))
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (spacing <= 1f) return@Canvas
            val contentCenterX = size.width / 2f
            val contentCenterY = size.height / 2f
            val sc = scroll.value
            val baseRadius = Settings.ballRadius
            val centerIdx = (sc / spacing).roundToInt().coerceIn(0, itemCount - 1)
            val cellPad = spacing * 0.08f

            for (i in 0 until itemCount) {
                val itemCx = contentCenterX + i * spacing - sc
                if (itemCx < -spacing || itemCx > size.width + spacing) continue
                val rel = (itemCx - contentCenterX) / spacing
                val dist = abs(rel).coerceAtMost(1.4f)
                val scale = 1f - 0.15f * dist
                // Clip each option to its own column cell so wide parts can't bleed into neighbours.
                val cellWidth = spacing - 2f * cellPad
                clipRect(itemCx - spacing / 2f + cellPad, 0f, itemCx + spacing / 2f - cellPad, size.height) {
                    drawItem(i, itemCx, contentCenterY, baseRadius * scale, i == centerIdx,
                        i == pressedIndex, cellWidth, size.height)
                }
            }
        }
    }
}

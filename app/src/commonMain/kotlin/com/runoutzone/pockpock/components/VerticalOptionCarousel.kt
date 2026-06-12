package com.runoutzone.pockpock.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
 * Vertical snap carousel used by the Ball Designer (style + color screens).
 *
 * The Y-axis analog of [shapes.ScrollSnapCarousel] / [shapes.BallSelectionPopup]: items are stacked
 * vertically, the centered item is emphasised with a mild grow (and neighbours shrink), and the
 * whole strip snaps to an item on release.
 *
 * Interaction (per design):
 *  - **Tap** any visible item → [onTap] (caller decides select vs. watch-ad for locked items). The
 *    tapped item also animates to centre.
 *  - **Vertical drag** scrolls; on release it snaps to the nearest item and calls [onSnap]
 *    (caller browses/selects — landing on a locked item should merely browse, never trigger an ad).
 *
 * [drawItem] paints one item in absolute screen space. `isCenter` is true for the item currently
 * closest to the vertical centre (use it for any extra emphasis); `scale` already folds in the
 * grow/shrink so callers can size their content directly from `radius`. `isPressed` is true while
 * that item is held down (for a pressed/button affordance); `cellWidth`/`cellHeight` give the item's
 * available clip cell so callers can size a square button to it.
 */
@Composable
fun VerticalOptionCarousel(
    itemCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
    onSnap: (Int) -> Unit = {},
    // Fires (live) with the index currently nearest the vertical centre as the strip scrolls, so a
    // caller can show a label for the browsed item even before it snaps/selects.
    onCenterChanged: (Int) -> Unit = {},
    drawItem: DrawScope.(
        index: Int, centerX: Float, centerY: Float, radius: Float,
        isCenter: Boolean, isPressed: Boolean, cellWidth: Float, cellHeight: Float
    ) -> Unit,
) {
    if (itemCount <= 0) { Box(modifier) ; return }

    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var pressedIndex by remember { mutableIntStateOf(-1) }

    // ~3.2 items visible; spacing drives both layout and the snap grid.
    val spacing = if (heightPx > 0) heightPx / 3.2f else 1f

    // scroll.value == selectedIndex*spacing centres that item. Driven by an Animatable so taps and
    // snap-on-release ease smoothly; drags snapTo for 1:1 tracking.
    val scroll = remember { Animatable(0f) }

    fun maxScroll() = ((itemCount - 1).coerceAtLeast(0)) * spacing

    // Keep the centred item in sync with the externally controlled selection (e.g. loading a slot).
    LaunchedEffect(selectedIndex, spacing) {
        if (!dragging && spacing > 1f) scroll.animateTo((selectedIndex * spacing).coerceIn(0f, maxScroll()))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { heightPx = it.height; widthPx = it.width }
            .pointerInput(itemCount, spacing) {
                fun indexAt(y: Float): Int {
                    val contentCenterY = size.height / 2f
                    return ((y - contentCenterY + scroll.value) / spacing)
                        .roundToInt().coerceIn(0, itemCount - 1)
                }
                detectTapGestures(
                    onPress = { offset ->
                        if (spacing <= 1f) return@detectTapGestures
                        // Held-down affordance: light up the pressed item until release/cancel.
                        pressedIndex = indexAt(offset.y)
                        tryAwaitRelease()
                        pressedIndex = -1
                    },
                    onTap = { offset ->
                        if (spacing <= 1f) return@detectTapGestures
                        val idx = indexAt(offset.y)
                        scope.launch { scroll.animateTo((idx * spacing).coerceIn(0f, maxScroll())) }
                        onCenterChanged(idx)
                        onTap(idx)
                    }
                )
            }
            .pointerInput(itemCount, spacing) {
                detectVerticalDragGestures(
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
            val contentCenterY = size.height / 2f
            val contentCenterX = size.width / 2f
            val sc = scroll.value
            // Play-size parts: the centred item is exactly r (Settings.ballRadius); neighbours shrink
            // a little so the focused one reads as selected.
            val baseRadius = Settings.ballRadius
            val centerIdx = (sc / spacing).roundToInt().coerceIn(0, itemCount - 1)
            val cellPad = spacing * 0.08f

            for (i in 0 until itemCount) {
                val itemCy = contentCenterY + i * spacing - sc
                if (itemCy < -spacing || itemCy > size.height + spacing) continue
                val rel = (itemCy - contentCenterY) / spacing
                val dist = abs(rel).coerceAtMost(1.4f)
                val scale = 1f - 0.15f * dist
                // Clip each option to its own cell (with padding) so wide parts — e.g. the rainbow
                // paddle's trail — can't bleed into the neighbouring option.
                val cellHeight = spacing - 2f * cellPad
                clipRect(0f, itemCy - spacing / 2f + cellPad, size.width, itemCy + spacing / 2f - cellPad) {
                    drawItem(i, contentCenterX, itemCy, baseRadius * scale, i == centerIdx,
                        i == pressedIndex, size.width, cellHeight)
                }
            }
        }
    }
}

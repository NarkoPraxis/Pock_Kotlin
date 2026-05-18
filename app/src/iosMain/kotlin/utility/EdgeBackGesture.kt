package utility

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

actual fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier =
    this.pointerInput(onBack) {
        var trackingId: Long? = null
        var startX = 0f
        var startY = 0f

        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val edgeZone = size.width * 0.07f      // 7% of width from each edge
                val dragThreshold = size.width * 0.20f // 20% drag triggers back
                val maxYDrift = size.height * 0.10f    // cancel if too vertical

                when (event.type) {
                    PointerEventType.Press -> {
                        val change = event.changes.firstOrNull { !it.previousPressed }
                        if (change != null) {
                            val x = change.position.x
                            if (x < edgeZone || x > size.width - edgeZone) {
                                trackingId = change.id.value
                                startX = x
                                startY = change.position.y
                            }
                        }
                    }
                    PointerEventType.Move -> {
                        val id = trackingId
                        if (id != null) {
                            val change = event.changes.find { it.id.value == id }
                            if (change != null && change.pressed) {
                                val dx = change.position.x - startX
                                val dy = abs(change.position.y - startY)
                                if (dy > maxYDrift) {
                                    trackingId = null
                                } else {
                                    val fromLeft = startX < edgeZone
                                    if ((fromLeft && dx > dragThreshold) || (!fromLeft && -dx > dragThreshold)) {
                                        trackingId = null
                                        onBack()
                                    }
                                }
                            } else if (change == null || !change.pressed) {
                                trackingId = null
                            }
                        }
                    }
                    PointerEventType.Release -> {
                        val id = trackingId
                        if (id != null && event.changes.any { it.id.value == id && !it.pressed }) {
                            trackingId = null
                        }
                    }
                    else -> {}
                }
            }
        }
    }

package utility

import androidx.compose.ui.graphics.drawscope.DrawScope

actual fun DrawScope.drawGameFrame() {
    with(Drawing) { drawFrame() }
}

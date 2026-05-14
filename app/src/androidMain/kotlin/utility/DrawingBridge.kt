package utility

import androidx.compose.ui.graphics.drawscope.DrawScope

actual fun DrawScope.drawGameFrame() {
    if (gameobjects.Settings.screenWidth == 0f) return
    with(Drawing) { drawFrame() }
}

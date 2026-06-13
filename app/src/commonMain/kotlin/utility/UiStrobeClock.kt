package utility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * App-wide clock for *static* UI cosmetics whose colors must keep strobing even though their
 * geometry is frozen — e.g. the Rainbow / Prism skins & tails shown as still "screenshots" in the
 * Ball Designer carousels and the in-game ball-select popup.
 *
 * [frame] is snapshot-backed, so any draw that reads it (through
 * [gameobjects.puckstyle.PuckRenderer.strobe]) is invalidated and repainted whenever it advances —
 * that's what keeps an otherwise-static option canvas repainting its color cycle. Drive it with
 * [DriveUiStrobeClock] on a Compose screen, and/or [advance] from a render loop (the game loop, for
 * the popup). Live gameplay never reads this: PuckRenderer.strobe falls back to the per-puck frame
 * whenever staticUiMode is false, so in-game rendering is unaffected.
 */
object UiStrobeClock {
    var frame by mutableIntStateOf(0)
        private set

    fun advance() { frame++ }
}

/** Advances [UiStrobeClock] ~60fps while present, so static rainbow/prism cosmetics keep cycling. */
@Composable
fun DriveUiStrobeClock() {
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            UiStrobeClock.advance()
        }
    }
}

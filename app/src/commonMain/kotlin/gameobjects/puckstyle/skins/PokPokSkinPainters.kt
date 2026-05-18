package gameobjects.puckstyle.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.PokPok_Parts_Body
import pock_kotlin.app.generated.resources.PokPok_Parts_Eyes_Closed
import pock_kotlin.app.generated.resources.PokPok_Parts_Eyes_Opened
import pock_kotlin.app.generated.resources.PokPok_Parts_HeadFeather_Left
import pock_kotlin.app.generated.resources.PokPok_Parts_HeadFeather_Middle
import pock_kotlin.app.generated.resources.PokPok_Parts_HeadFeather_Right
import pock_kotlin.app.generated.resources.PokPok_Parts_Mouth_Closed
import pock_kotlin.app.generated.resources.PokPok_Parts_Mouth_Open
import pock_kotlin.app.generated.resources.PokPok_Parts_Wing_Left
import pock_kotlin.app.generated.resources.PokPok_Parts_Wing_Right

/**
 * Holds Compose [Painter] instances for every SVG part used by [PokPokSkin].
 *
 * `painterResource()` is `@Composable` and cannot be called from `DrawScope.drawBody()`. The
 * painters are loaded once from a composable scope (see [load]) and read from `DrawScope`
 * during each frame. Following the project convention of global object singletons (see
 * `PaintBucket`, `Sounds`, etc.) rather than DI.
 *
 * If a painter is `null` at draw time the skin silently skips that part — `load()` MUST be
 * invoked from a composable that hosts the game `Canvas` (e.g. `IosGameHost` /
 * `GameActivity`) before the first frame.
 */
object PokPokSkinPainters {
    var body: Painter? = null
    var eyesOpen: Painter? = null
    var eyesClosed: Painter? = null
    var mouthOpen: Painter? = null
    var mouthClosed: Painter? = null
    var wingLeft: Painter? = null
    var wingRight: Painter? = null
    var featherLeft: Painter? = null
    var featherMiddle: Painter? = null
    var featherRight: Painter? = null

    @Composable
    fun load() {
        body          = painterResource(Res.drawable.PokPok_Parts_Body)
        eyesOpen      = painterResource(Res.drawable.PokPok_Parts_Eyes_Opened)
        eyesClosed    = painterResource(Res.drawable.PokPok_Parts_Eyes_Closed)
        mouthOpen     = painterResource(Res.drawable.PokPok_Parts_Mouth_Open)
        mouthClosed   = painterResource(Res.drawable.PokPok_Parts_Mouth_Closed)
        wingLeft      = painterResource(Res.drawable.PokPok_Parts_Wing_Left)
        wingRight     = painterResource(Res.drawable.PokPok_Parts_Wing_Right)
        featherLeft   = painterResource(Res.drawable.PokPok_Parts_HeadFeather_Left)
        featherMiddle = painterResource(Res.drawable.PokPok_Parts_HeadFeather_Middle)
        featherRight  = painterResource(Res.drawable.PokPok_Parts_HeadFeather_Right)
    }
}

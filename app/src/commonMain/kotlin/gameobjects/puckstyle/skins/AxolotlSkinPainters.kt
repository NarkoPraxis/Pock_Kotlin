package gameobjects.puckstyle.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.Axolotl_Body
import pock_kotlin.app.generated.resources.Axolotl_Eyes_Pupil
import pock_kotlin.app.generated.resources.Axolotl_Eyes_White
import pock_kotlin.app.generated.resources.Axolotl_Mouth_Closed
import pock_kotlin.app.generated.resources.Axolotl_Mouth_Open_1
import pock_kotlin.app.generated.resources.Axolotl_Mouth_Open_2
import pock_kotlin.app.generated.resources.Axolotl_Gill_L
import pock_kotlin.app.generated.resources.Axolotl_Gill_R

object AxolotlSkinPainters {
    var body: Painter? = null
    var eyesPupil: Painter? = null
    var eyesWhite: Painter? = null
    var mouthClosed: Painter? = null
    var mouthOpen1: Painter? = null
    var mouthOpen2: Painter? = null
    var gillLeft: Painter? = null
    var gillRight: Painter? = null

    @Composable
    fun load() {
        body        = painterResource(Res.drawable.Axolotl_Body)
        eyesPupil   = painterResource(Res.drawable.Axolotl_Eyes_Pupil)
        eyesWhite   = painterResource(Res.drawable.Axolotl_Eyes_White)
        mouthClosed = painterResource(Res.drawable.Axolotl_Mouth_Closed)
        mouthOpen1  = painterResource(Res.drawable.Axolotl_Mouth_Open_1)
        mouthOpen2  = painterResource(Res.drawable.Axolotl_Mouth_Open_2)
        gillLeft    = painterResource(Res.drawable.Axolotl_Gill_L)
        gillRight   = painterResource(Res.drawable.Axolotl_Gill_R)
    }
}

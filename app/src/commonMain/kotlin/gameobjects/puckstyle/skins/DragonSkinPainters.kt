package gameobjects.puckstyle.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.Dragon_Body
import pock_kotlin.app.generated.resources.Dragon_Eye_Closed_L
import pock_kotlin.app.generated.resources.Dragon_Eye_Closed_R
import pock_kotlin.app.generated.resources.Dragon_Eye_Open_L1
import pock_kotlin.app.generated.resources.Dragon_Eye_Open_L2
import pock_kotlin.app.generated.resources.Dragon_Eye_Open_R1
import pock_kotlin.app.generated.resources.Dragon_Eye_Open_R2
import pock_kotlin.app.generated.resources.Dragon_Horn_Left
import pock_kotlin.app.generated.resources.Dragon_Horn_Middle
import pock_kotlin.app.generated.resources.Dragon_Horn_Right
import pock_kotlin.app.generated.resources.Dragon_Mouth_Closed_1
import pock_kotlin.app.generated.resources.Dragon_Mouth_Closed_2
import pock_kotlin.app.generated.resources.Dragon_Mouth_Open_1
import pock_kotlin.app.generated.resources.Dragon_Mouth_Open_2
import pock_kotlin.app.generated.resources.Dragon_Wing_L
import pock_kotlin.app.generated.resources.Dragon_Wing_R
import pock_kotlin.app.generated.resources.Res

object DragonSkinPainters {
    var body: Painter? = null
    var eyeOpenL1: Painter? = null
    var eyeOpenL2: Painter? = null
    var eyeOpenR1: Painter? = null
    var eyeOpenR2: Painter? = null
    var eyeClosedL: Painter? = null
    var eyeClosedR: Painter? = null
    var mouthOpen1: Painter? = null
    var mouthOpen2: Painter? = null
    var mouthClosed1: Painter? = null
    var mouthClosed2: Painter? = null
    var wingL: Painter? = null
    var wingR: Painter? = null
    var hornLeft: Painter? = null
    var hornMiddle: Painter? = null
    var hornRight: Painter? = null

    @Composable
    fun load() {
        body        = painterResource(Res.drawable.Dragon_Body)
        eyeOpenL1   = painterResource(Res.drawable.Dragon_Eye_Open_L1)
        eyeOpenL2   = painterResource(Res.drawable.Dragon_Eye_Open_L2)
        eyeOpenR1   = painterResource(Res.drawable.Dragon_Eye_Open_R1)
        eyeOpenR2   = painterResource(Res.drawable.Dragon_Eye_Open_R2)
        eyeClosedL  = painterResource(Res.drawable.Dragon_Eye_Closed_L)
        eyeClosedR  = painterResource(Res.drawable.Dragon_Eye_Closed_R)
        mouthOpen1  = painterResource(Res.drawable.Dragon_Mouth_Open_1)
        mouthOpen2  = painterResource(Res.drawable.Dragon_Mouth_Open_2)
        mouthClosed1 = painterResource(Res.drawable.Dragon_Mouth_Closed_1)
        mouthClosed2 = painterResource(Res.drawable.Dragon_Mouth_Closed_2)
        wingL       = painterResource(Res.drawable.Dragon_Wing_L)
        wingR       = painterResource(Res.drawable.Dragon_Wing_R)
        hornLeft    = painterResource(Res.drawable.Dragon_Horn_Left)
        hornMiddle  = painterResource(Res.drawable.Dragon_Horn_Middle)
        hornRight   = painterResource(Res.drawable.Dragon_Horn_Right)
    }
}

package gameobjects.puckstyle.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import pock_kotlin.app.generated.resources.Cat_Body
import pock_kotlin.app.generated.resources.Cat_Ear_L1
import pock_kotlin.app.generated.resources.Cat_Ear_L2
import pock_kotlin.app.generated.resources.Cat_Ear_R1
import pock_kotlin.app.generated.resources.Cat_Ear_R2
import pock_kotlin.app.generated.resources.Cat_Eye_Closed_L
import pock_kotlin.app.generated.resources.Cat_Eye_Closed_R
import pock_kotlin.app.generated.resources.Cat_Eye_Open_L1
import pock_kotlin.app.generated.resources.Cat_Eye_Open_L2
import pock_kotlin.app.generated.resources.Cat_Eye_Open_R1
import pock_kotlin.app.generated.resources.Cat_Eye_Open_R2
import pock_kotlin.app.generated.resources.Cat_Fur_L1
import pock_kotlin.app.generated.resources.Cat_Fur_L2
import pock_kotlin.app.generated.resources.Cat_Fur_R1
import pock_kotlin.app.generated.resources.Cat_Fur_R2
import pock_kotlin.app.generated.resources.Cat_Fur_Top
import pock_kotlin.app.generated.resources.Cat_Mouth_Closed
import pock_kotlin.app.generated.resources.Cat_Mouth_Open_1
import pock_kotlin.app.generated.resources.Cat_Mouth_Open_2
import pock_kotlin.app.generated.resources.Res

object CatSkinPainters {
    var body: Painter? = null
    var earL1: Painter? = null
    var earL2: Painter? = null
    var earR1: Painter? = null
    var earR2: Painter? = null
    var eyeOpenL1: Painter? = null
    var eyeOpenL2: Painter? = null
    var eyeOpenR1: Painter? = null
    var eyeOpenR2: Painter? = null
    var eyeClosedL: Painter? = null
    var eyeClosedR: Painter? = null
    var mouthOpen1: Painter? = null
    var mouthOpen2: Painter? = null
    var mouthClosed: Painter? = null
    var furTop: Painter? = null
    var furL1: Painter? = null
    var furL2: Painter? = null
    var furR1: Painter? = null
    var furR2: Painter? = null

    @Composable
    fun load() {
        body       = painterResource(Res.drawable.Cat_Body)
        earL1      = painterResource(Res.drawable.Cat_Ear_L1)
        earL2      = painterResource(Res.drawable.Cat_Ear_L2)
        earR1      = painterResource(Res.drawable.Cat_Ear_R1)
        earR2      = painterResource(Res.drawable.Cat_Ear_R2)
        eyeOpenL1  = painterResource(Res.drawable.Cat_Eye_Open_L1)
        eyeOpenL2  = painterResource(Res.drawable.Cat_Eye_Open_L2)
        eyeOpenR1  = painterResource(Res.drawable.Cat_Eye_Open_R1)
        eyeOpenR2  = painterResource(Res.drawable.Cat_Eye_Open_R2)
        eyeClosedL = painterResource(Res.drawable.Cat_Eye_Closed_L)
        eyeClosedR = painterResource(Res.drawable.Cat_Eye_Closed_R)
        mouthOpen1 = painterResource(Res.drawable.Cat_Mouth_Open_1)
        mouthOpen2 = painterResource(Res.drawable.Cat_Mouth_Open_2)
        mouthClosed = painterResource(Res.drawable.Cat_Mouth_Closed)
        furTop     = painterResource(Res.drawable.Cat_Fur_Top)
        furL1      = painterResource(Res.drawable.Cat_Fur_L1)
        furL2      = painterResource(Res.drawable.Cat_Fur_L2)
        furR1      = painterResource(Res.drawable.Cat_Fur_R1)
        furR2      = painterResource(Res.drawable.Cat_Fur_R2)
    }
}

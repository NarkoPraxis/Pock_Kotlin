package com.runoutzone.pockpock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runoutzone.pockpock.components.AdLimitPopup
import com.runoutzone.pockpock.components.OptionLockState
import com.runoutzone.pockpock.components.StyleOptionButton
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import kotlinx.coroutines.delay
import utility.AdUnlock
import utility.PaintBucket
import utility.Storage
import utility.edgeSwipeBack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val ID_SKIN = 0
private const val ID_TAIL = 1
private const val ID_PADDLE = 2

private const val STATE_NORMAL = 0
private const val STATE_SHIELD = 1
private const val STATE_INERT = 2

private val COMPONENT_TYPES: List<BallType> = BallType.entries.filter { it != BallType.Random }

private fun buildPartRenderer(component: Int, type: BallType, theme: ColorTheme): PuckRenderer {
    val r = when (component) {
        ID_SKIN -> BallStyleFactory.buildSkinOnlyRenderer(type, theme)
        ID_TAIL -> BallStyleFactory.buildTailOnlyRenderer(type, theme)
        else -> BallStyleFactory.buildPaddleOnlyRenderer(type, theme)
    }
    r.isHigh = theme.isWarm
    return r
}

private fun isComponentUnlocked(component: Int, type: BallType): Boolean = when (component) {
    ID_SKIN -> Storage.isSkinUnlocked(type)
    ID_TAIL -> Storage.isTailUnlocked(type)
    else -> Storage.isPaddleUnlocked(type)
}

private fun unlockComponent(component: Int, type: BallType) = when (component) {
    ID_SKIN -> Storage.unlockSkin(type)
    ID_TAIL -> Storage.unlockTail(type)
    else -> Storage.unlockPaddle(type)
}

@Composable
fun CustomBallCreatorScreen(onBack: () -> Unit, onNavigateToCcp: () -> Unit) {
    val isDark = LocalDarkMode.current
    val density = LocalDensity.current
    val bgColor = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight

    var frame by remember { mutableIntStateOf(0) }
    var rootW by remember { mutableIntStateOf(0) }
    var rootH by remember { mutableIntStateOf(0) }
    var initDone by remember { mutableStateOf(false) }

    var skinType by remember { mutableStateOf(BallType.Classic) }
    var tailType by remember { mutableStateOf(BallType.Classic) }
    var paddleType by remember { mutableStateOf(BallType.Classic) }
    // ranks: skin = first, tail = second, paddle = third (0 = back, 2 = front)
    var ranks by remember { mutableStateOf(Triple(0, 1, 2)) }
    var previewTheme by remember { mutableStateOf(ColorTheme.Cold) }
    var previewState by remember { mutableIntStateOf(STATE_NORMAL) }

    var meterPopupVisible by remember { mutableStateOf(false) }
    var adLimitPopupVisible by remember { mutableStateOf(false) }

    fun loadBalls(): List<Pair<Int, CustomBallConfig>> =
        (0 until Storage.SLOT_COUNT).mapNotNull { i -> Storage.loadCustomBall(i)?.let { i to it } }

    var savedBalls by remember { mutableStateOf(loadBalls()) }
    var selectedSlot by remember { mutableIntStateOf(savedBalls.firstOrNull()?.first ?: 0) }

    fun currentConfig() = CustomBallConfig(skinType, tailType, paddleType, ranks.first, ranks.second, ranks.third)

    fun allUnlocked() =
        Storage.isSkinUnlocked(skinType) && Storage.isTailUnlocked(tailType) && Storage.isPaddleUnlocked(paddleType)

    var previewRenderer by remember { mutableStateOf<PuckRenderer?>(null) }

    fun rebuildPreview() {
        previewRenderer?.tail?.clear()
        val r = BallStyleFactory.buildCustomRenderer(currentConfig(), previewTheme)
        r.isHigh = previewTheme.isWarm
        previewRenderer = r
    }

    fun trySave() {
        if (selectedSlot < 0 || !Storage.isSlotUnlocked(selectedSlot)) return
        if (!allUnlocked()) return
        Storage.saveCustomBall(selectedSlot, currentConfig())
        savedBalls = loadBalls()
    }

    fun loadSlotIntoCarousels(config: CustomBallConfig) {
        skinType = config.skinType
        tailType = config.tailType
        paddleType = config.paddleType
        ranks = Triple(config.skinZRank, config.tailZRank, config.paddleZRank)
        rebuildPreview()
    }

    fun selectComponent(component: Int, type: BallType) {
        when (component) {
            ID_SKIN -> skinType = type
            ID_TAIL -> tailType = type
            else -> paddleType = type
        }
        rebuildPreview()
        trySave()
    }

    fun onComponentTapped(component: Int, type: BallType) {
        if (isComponentUnlocked(component, type)) {
            selectComponent(component, type)
            return
        }
        if (Storage.canWatchAdNow()) {
            AdUnlock.watchAdToUnlock(grant = { unlockComponent(component, type) }) { success ->
                if (success) selectComponent(component, type)
            }
        } else {
            adLimitPopupVisible = true
        }
    }

    fun rankOf(id: Int) = when (id) { ID_SKIN -> ranks.first; ID_TAIL -> ranks.second; else -> ranks.third }

    fun bringToFront(id: Int) {
        val others = (0..2).filter { it != id }.sortedBy { rankOf(it) }
        val newRanks = IntArray(3)
        newRanks[id] = 2
        newRanks[others[0]] = 0
        newRanks[others[1]] = 1
        ranks = Triple(newRanks[0], newRanks[1], newRanks[2])
        rebuildPreview()
        trySave()
    }

    // Animation ticker
    LaunchedEffect(Unit) {
        while (true) { delay(16L); frame++ }
    }

    // Init Settings sizing once the root size is known
    LaunchedEffect(rootW, rootH) {
        if (!initDone && rootW > 0 && rootH > 0) {
            val ratio = max(1f, min(rootW.toFloat(), rootH.toFloat()) / 18f)
            if (Settings.screenRatio == 0f) {
                Settings.screenRatio = ratio
                Settings.strokeWidth = ratio / 4f
                Settings.screenWidth = rootW.toFloat()
                Settings.screenHeight = rootH.toFloat()
                Settings.middleX = rootW / 2f
                Settings.middleY = rootH / 2f
                Settings.ballRadius = ratio
                PaintBucket.initialize(ratio)
                PaintBucket.applyPlayerHues()
            }
            if (Settings.ballRadius == 0f) Settings.ballRadius = Settings.screenRatio
            val initial = savedBalls.firstOrNull { it.first == selectedSlot } ?: savedBalls.firstOrNull()
            if (initial != null) { selectedSlot = initial.first; loadSlotIntoCarousels(initial.second) }
            else rebuildPreview()
            initDone = true
        }
    }

    val overlayBtnColors = ButtonDefaults.buttonColors(
        containerColor = if (isDark) PaintBucket.menuButtonDark else PaintBucket.menuButtonLight,
        contentColor = if (isDark) PaintBucket.white else PaintBucket.menuBackgroundDark
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { rootW = it.width; rootH = it.height }
            .edgeSwipeBack(onBack)
    ) {
        if (initDone) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                // Thermometer — progress toward unlocking everything
                if (Storage.unlockProgress < 100) {
                    UnlockProgressBar(
                        progress = Storage.unlockProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(34.dp)
                    )
                }

                // Preview ball (takes remaining vertical space)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pr = previewRenderer ?: return@Canvas
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val amp = Settings.screenRatio * 1.2f
                        pr.x = cx
                        pr.y = cy + amp * sin(2f * PI.toFloat() * frame / 70f)
                        pr.radius = Settings.ballRadius
                        pr.strokeWidth = Settings.strokeWidth
                        pr.frame = frame
                        pr.effectEnabled = true
                        pr.shielded = previewState == STATE_SHIELD
                        pr.inertLocked = previewState == STATE_INERT
                        pr.fillColor = previewTheme.main.primary
                        pr.strokeColor = previewTheme.main.secondary
                        pr.baseFillColor = previewTheme.main.primary
                        pr.effect.increaseCharge()
                        if (pr.effect.phase == ChargePhase.Inert) pr.effect.reset()
                        pr.effect.cbcOrbitAngleDeg = (frame * 1.2f) % 360f
                        with(pr) { draw() }
                    }
                }

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            previewTheme = if (previewTheme.isWarm) ColorTheme.Cold else ColorTheme.Warm
                            rebuildPreview()
                        },
                        colors = overlayBtnColors
                    ) { Text(if (previewTheme.isWarm) "HIGH" else "LOW", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = { previewState = (previewState + 1) % 3 },
                        colors = overlayBtnColors
                    ) {
                        Text(
                            when (previewState) { STATE_SHIELD -> "SHIELD"; STATE_INERT -> "INERT"; else -> "NORMAL" },
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    Box(modifier = Modifier.weight(1f))

                    Button(onClick = onNavigateToCcp, colors = overlayBtnColors) {
                        Text("PALETTE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Slot grid (2 rows of 5)
                SlotGrid(
                    savedBalls = savedBalls,
                    selectedSlot = selectedSlot,
                    previewTheme = previewTheme,
                    frame = frame,
                    isDark = isDark,
                    density = density,
                    onSlotTap = { idx ->
                        val existing = savedBalls.firstOrNull { it.first == idx }
                        if (existing != null) { selectedSlot = idx; loadSlotIntoCarousels(existing.second) }
                        else if (allUnlocked()) {
                            Storage.saveCustomBall(idx, currentConfig())
                            savedBalls = loadBalls()
                            selectedSlot = idx
                        }
                    },
                    onSlotDelete = { idx ->
                        Storage.deleteCustomBall(idx)
                        savedBalls = loadBalls()
                        if (selectedSlot == idx) selectedSlot = savedBalls.firstOrNull()?.first ?: 0
                    }
                )

                // Component carousels
                ComponentCarouselRow(ID_SKIN, "SKIN", skinType, rankOf(ID_SKIN), previewTheme, previewState, frame, density,
                    onTap = { onComponentTapped(ID_SKIN, it) }, onBringToFront = { bringToFront(ID_SKIN) })
                ComponentCarouselRow(ID_TAIL, "TAIL", tailType, rankOf(ID_TAIL), previewTheme, previewState, frame, density,
                    onTap = { onComponentTapped(ID_TAIL, it) }, onBringToFront = { bringToFront(ID_TAIL) })
                ComponentCarouselRow(ID_PADDLE, "PADDLE", paddleType, rankOf(ID_PADDLE), previewTheme, previewState, frame, density,
                    onTap = { onComponentTapped(ID_PADDLE, it) }, onBringToFront = { bringToFront(ID_PADDLE) })
            }
        }

        if (meterPopupVisible) {
            com.runoutzone.pockpock.components.MeterLockedPopup(Storage.unlockProgress) { meterPopupVisible = false }
        }
        if (adLimitPopupVisible) {
            AdLimitPopup(Storage.minutesUntilNextAd()) { adLimitPopupVisible = false }
        }
    }
}

@Composable
private fun SlotGrid(
    savedBalls: List<Pair<Int, CustomBallConfig>>,
    selectedSlot: Int,
    previewTheme: ColorTheme,
    frame: Int,
    isDark: Boolean,
    density: androidx.compose.ui.unit.Density,
    onSlotTap: (Int) -> Unit,
    onSlotDelete: (Int) -> Unit,
) {
    val slotDp = with(density) { (Settings.screenRatio * 3f).toDp() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..4) {
                    val idx = row * 5 + col
                    SlotCell(
                        index = idx,
                        config = savedBalls.firstOrNull { it.first == idx }?.second,
                        unlocked = Storage.isSlotUnlocked(idx),
                        selected = idx == selectedSlot,
                        theme = previewTheme,
                        frame = frame,
                        isDark = isDark,
                        sizeDp = slotDp,
                        onTap = { onSlotTap(idx) },
                        onLongPress = { onSlotDelete(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    index: Int,
    config: CustomBallConfig?,
    unlocked: Boolean,
    selected: Boolean,
    theme: ColorTheme,
    frame: Int,
    isDark: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = if (isDark) Color(0xFF1A2A3E) else Color(0xFFE8E8F0)
    val border = if (selected) Color(0xFF52B6F2) else Color(0xFFF25252)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(shape)
            .background(bg)
            .then(if (unlocked) Modifier.border(if (selected) 3.dp else 1.5.dp, border, shape) else Modifier)
            .pointerInput(unlocked, config) {
                if (unlocked) detectTapGestures(onTap = { onTap() }, onLongPress = { if (config != null) onLongPress() })
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            !unlocked -> Text("${Storage.slotRequiredPercent(index)}%", color = Color(0xFF888899), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            config != null -> {
                val renderer = remember(config, theme) {
                    BallStyleFactory.buildCustomRenderer(config, theme).also { it.isHigh = theme.isWarm }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    renderer.x = size.width / 2f
                    renderer.y = size.height / 2f
                    renderer.radius = Settings.ballRadius
                    renderer.strokeWidth = Settings.strokeWidth
                    renderer.frame = frame
                    renderer.effectEnabled = false
                    renderer.fillColor = theme.main.primary
                    renderer.strokeColor = theme.main.secondary
                    renderer.baseFillColor = theme.main.primary
                    with(renderer) { draw() }
                }
            }
            else -> Text("+", color = Color(0xFF888899), fontSize = 20.sp)
        }
    }
}

@Composable
private fun ComponentCarouselRow(
    component: Int,
    label: String,
    selectedType: BallType,
    layerRank: Int,
    theme: ColorTheme,
    state: Int,
    frame: Int,
    density: androidx.compose.ui.unit.Density,
    onTap: (BallType) -> Unit,
    onBringToFront: () -> Unit,
) {
    val itemDp = with(density) { (Settings.screenRatio * 3.6f).toDp() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (LocalDarkMode.current) PaintBucket.white else Color(0xFF333333),
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.weight(1f))
            // Layer order: tap to bring this component to the front
            Text(
                text = if (layerRank == 2) "front" else "▲ front",
                color = Color(0xFF52B6F2),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onBringToFront() } }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (type in COMPONENT_TYPES) {
                val unlocked = isComponentUnlocked(component, type)
                val lockState = when {
                    type == selectedType -> OptionLockState.UnlockedSelected
                    unlocked -> OptionLockState.UnlockedAvailable
                    else -> OptionLockState.LockedAd
                }
                val renderer = remember(component, type, theme) { buildPartRenderer(component, type, theme) }
                StyleOptionButton(
                    state = lockState,
                    onTap = { onTap(type) },
                    size = itemDp
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val amp = if (component == ID_TAIL) size.height * 0.12f else 0f
                        renderer.x = cx
                        renderer.y = cy + amp * sin(2f * PI.toFloat() * frame / 60f)
                        renderer.radius = Settings.ballRadius
                        renderer.strokeWidth = Settings.strokeWidth
                        renderer.frame = frame
                        renderer.shielded = state == STATE_SHIELD
                        renderer.inertLocked = state == STATE_INERT
                        renderer.fillColor = theme.main.primary
                        renderer.strokeColor = theme.main.secondary
                        renderer.baseFillColor = theme.main.primary
                        renderer.effectEnabled = component == ID_PADDLE
                        if (component == ID_PADDLE) {
                            renderer.effect.cbcOrbitAngleDeg = (frame * 1.2f) % 360f
                        }
                        with(renderer) { draw() }
                    }
                }
            }
        }
    }
}

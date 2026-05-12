package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import enums.BallType
import utility.PaintBucket
import utility.Storage

@Composable
fun BallUnlockScreen(onBack: () -> Unit) {
    val unlockProgress by remember { mutableIntStateOf(Storage.unlockProgress) }
    val displayTypes = remember { BallType.entries.filter { it != BallType.Random } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaintBucket.backgroundColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("BALL TYPES", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "Unlock Progress: $unlockProgress / 100",
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LinearProgressIndicator(
            progress = { unlockProgress / 100f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color(0xFF6666AA),
            trackColor = Color(0xFF333344)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayTypes) { ballType ->
                BallTypeCard(
                    ballType = ballType,
                    isUnlocked = isBallTypeUnlocked(ballType, unlockProgress)
                )
            }
        }
    }
}

private fun isBallTypeUnlocked(type: BallType, unlockProgress: Int): Boolean {
    val adsLeft = 100 - unlockProgress
    return when (type) {
        BallType.Classic -> true
        BallType.Prism, BallType.Plasma -> adsLeft == 0
        BallType.Random -> unlockProgress >= 100
        else -> {
            val ordinal = type.ordinal
            ordinal in 1..9 && adsLeft <= 100 - ordinal * 10
        }
    }
}

@Composable
private fun BallTypeCard(ballType: BallType, isUnlocked: Boolean) {
    val bgColor = if (isUnlocked) Color(0xFF2A2A3A) else Color(0xFF1A1A22)
    val nameColor = if (isUnlocked) Color.White else Color(0xFF666666)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isUnlocked) {
                    Text("🔒", fontSize = 32.sp)
                } else {
                    Text("●", fontSize = 40.sp, color = ballTypeColor(ballType))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ballType.name,
                    color = nameColor,
                    fontSize = 14.sp,
                    fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun ballTypeColor(type: BallType): Color = when (type) {
    BallType.Classic -> Color(0xFFf59da0)
    BallType.Neon    -> Color(0xFF39FF14)
    BallType.Ghost   -> Color(0xAAEEEEFF)
    BallType.Fire    -> Color(0xFFFF6600)
    BallType.Ice     -> Color(0xFF88DDFF)
    BallType.Galaxy  -> Color(0xFF9966FF)
    BallType.Spinner -> Color(0xFFFFCC00)
    BallType.Metal   -> Color(0xFFBBBBBB)
    BallType.Pixel   -> Color(0xFF00FF88)
    BallType.Rainbow -> Color(0xFFFF4488)
    BallType.Prism   -> Color(0xFFFFFFFF)
    BallType.Plasma  -> Color(0xFFFF00FF)
    BallType.Chicken -> Color(0xFFFFDD44)
    BallType.Random  -> Color(0xFFAAAAAA)
}

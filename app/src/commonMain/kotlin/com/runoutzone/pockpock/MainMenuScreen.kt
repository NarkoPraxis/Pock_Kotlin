package com.runoutzone.pockpock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utility.PaintBucket

@Composable
fun MainMenuScreen(
    onPlayTapped: () -> Unit,
    onSinglePlayerTapped: () -> Unit,
    onSettingsTapped: () -> Unit,
    onBallsTapped: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaintBucket.backgroundColor)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "POCK",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            MenuButton(text = "PLAY", onClick = onPlayTapped)
            MenuButton(text = "PLAY SOLO", onClick = onSinglePlayerTapped)
            MenuButton(text = "SETTINGS", onClick = onSettingsTapped)
            MenuButton(text = "BALL TYPES", onClick = onBallsTapped)
            PlatformMenuExtras()
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF444466),
            contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

package utility

import androidx.compose.ui.Modifier

expect fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier

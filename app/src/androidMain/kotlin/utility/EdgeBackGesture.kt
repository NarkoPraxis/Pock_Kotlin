package utility

import androidx.compose.ui.Modifier

// Android handles back navigation via the system edge-swipe gesture natively.
actual fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier = this

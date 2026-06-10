package com.runoutzone.pockpock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon-only share button for the main menu. Renders the shared `ic_menu_share` drawable and wires
 * the platform-specific share action (+ one-time share-reward grant). [iconSize] lets the caller
 * scale the glyph with the screen so it matches the neighboring settings icon at any size.
 */
expect @Composable fun PlatformShareButton(modifier: Modifier, iconSize: Dp = 32.dp)

/**
 * Icon-only localization button for the main menu. Renders the shared `ic_menu_localization`
 * drawable and opens the platform language picker. [iconSize] scales the glyph with the screen.
 */
expect @Composable fun PlatformLanguageButton(modifier: Modifier, iconSize: Dp = 32.dp)

expect @Composable fun PlatformBallUnlockTop()
expect @Composable fun PlatformBallUnlockBottom()
expect @Composable fun ImmersiveModeEffect()

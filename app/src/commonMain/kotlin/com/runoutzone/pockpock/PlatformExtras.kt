package com.runoutzone.pockpock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Icon-only share button for the main menu. Renders the shared `ic_menu_share` drawable and wires
 * the platform-specific share action (+ one-time share-reward grant).
 */
expect @Composable fun PlatformShareButton(modifier: Modifier)

/**
 * Icon-only localization button for the main menu. Renders the shared `ic_menu_localization`
 * drawable and opens the platform language picker.
 */
expect @Composable fun PlatformLanguageButton(modifier: Modifier)

expect @Composable fun PlatformBallUnlockTop()
expect @Composable fun PlatformBallUnlockBottom()
expect @Composable fun ImmersiveModeEffect()

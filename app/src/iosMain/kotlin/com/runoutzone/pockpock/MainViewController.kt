package com.runoutzone.pockpock

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import utility.Sounds
import utility.Storage

@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    Storage.initialize(null)
    Sounds.initialize(null)
    return ComposeUIViewController { AppRoot() }
}

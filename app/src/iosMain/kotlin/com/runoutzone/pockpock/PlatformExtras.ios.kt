@file:OptIn(ExperimentalForeignApi::class)
package com.runoutzone.pockpock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSLocale
import platform.Foundation.NSMutableArray
import platform.Foundation.NSUserDefaults
import pock_kotlin.app.generated.resources.*

@Composable actual fun PlatformMenuExtras() {
    var showPicker by remember { mutableStateOf(false) }
    var languageFlag by remember { mutableStateOf(getCurrentLanguageFlag()) }
    var showRestartPrompt by remember { mutableStateOf(false) }

    TextButton(onClick = { showPicker = true }) {
        Text(languageFlag, fontSize = 24.sp)
    }

    if (showPicker) {
        val languages = listOf(
            "🇺🇸  English" to "",
            "🇩🇪  Deutsch" to "de",
            "🇪🇸  Español" to "es",
            "🇫🇷  Français" to "fr",
            "🇯🇵  日本語" to "ja",
            "🇨🇳  中文" to "zh"
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languages.forEach { (label, code) ->
                        TextButton(onClick = {
                            showPicker = false
                            val defaults = NSUserDefaults.standardUserDefaults
                            if (code.isEmpty()) {
                                defaults.removeObjectForKey("AppleLanguages")
                                defaults.removeObjectForKey("app_language_pref")
                            } else {
                                val arr = NSMutableArray()
                                arr.addObject(code)
                                defaults.setObject(arr, forKey = "AppleLanguages")
                                defaults.setObject(code, forKey = "app_language_pref")
                            }
                            defaults.synchronize()
                            languageFlag = getCurrentLanguageFlag()
                            showRestartPrompt = true
                        }) {
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (showRestartPrompt) {
        AlertDialog(
            onDismissRequest = { showRestartPrompt = false },
            text = { Text("Please restart the app to apply the language change.") },
            confirmButton = {
                TextButton(onClick = { showRestartPrompt = false }) { Text("OK") }
            }
        )
    }
}

private fun getCurrentLanguageFlag(): String {
    val langCode = NSUserDefaults.standardUserDefaults.stringForKey("app_language_pref")?.take(2) ?: "en"
    return when (langCode) {
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        "fr" -> "🇫🇷"
        "ja" -> "🇯🇵"
        "zh" -> "🇨🇳"
        else -> "🇺🇸"
    }
}

@Composable actual fun PlatformBallUnlockTop() {}
@Composable actual fun PlatformBallUnlockBottom() {}
@Composable actual fun ImmersiveModeEffect() {}

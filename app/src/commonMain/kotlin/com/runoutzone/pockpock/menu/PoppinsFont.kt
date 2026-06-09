package com.runoutzone.pockpock.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pock_kotlin.app.generated.resources.Res
import pock_kotlin.app.generated.resources.poppins_bold
import pock_kotlin.app.generated.resources.poppins_bold_italic
import pock_kotlin.app.generated.resources.poppins_italic
import pock_kotlin.app.generated.resources.poppins_light
import pock_kotlin.app.generated.resources.poppins_light_italic
import pock_kotlin.app.generated.resources.poppins_medium
import pock_kotlin.app.generated.resources.poppins_regular
import pock_kotlin.app.generated.resources.poppins_semibold

/**
 * Poppins — the brand typeface used by the redesigned UIOverhaul menu screens
 * (MainMenuScreen, ScoreCalibrationScreen, …). Bundled from Plans/UIOverhaul/Poppins.
 *
 * The mockups specify Poppins-LightItalic for menu labels and Poppins-Bold for the big
 * score numerals; the full weight range is wired up so future screens can use any of them
 * via [FontWeight] / [FontStyle] on a `TextStyle`.
 *
 * Must be called from a composable scope (resource fonts load through the font resolver).
 */
@Composable
fun poppinsFamily(): FontFamily = FontFamily(
    Font(Res.font.poppins_light, FontWeight.Light, FontStyle.Normal),
    Font(Res.font.poppins_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(Res.font.poppins_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.poppins_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.poppins_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(Res.font.poppins_bold, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.poppins_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

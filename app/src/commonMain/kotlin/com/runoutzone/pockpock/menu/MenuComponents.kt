package com.runoutzone.pockpock.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utility.PaintBucket

/**
 * Reusable UI primitives for the redesigned menu screens (translated from the UIOverhaul SVG
 * mockups). These are intentionally generic so the same building blocks can render the other
 * screen designs (Ball Select, Graphics, Sound, etc.) as they are migrated.
 *
 * Conventions:
 *  - Chrome colors come from fixed [PaintBucket] brand accents (menuAccent*), never the
 *    hue-customizable player colors or the dark-mode toggle.
 *  - Icons are authored white and tinted at the call site.
 */

/** Which screen edge a [EdgePill] is anchored to (the rounded cap is on the opposite, inner end). */
enum class PillSide { Start, End }

/**
 * Right-anchored parallelogram shape: vertical right edge, vertical-ish slanted left edge where the
 * bottom-left corner extends further toward the start than the top-left corner.
 *
 * Derived from the Main.svg button polygon (top-left 316.51 / bottom-left 239.98 over a 204px tall
 * button ⇒ slant ≈ 0.375 × height).
 */
fun slantedButtonShape(slantFraction: Float = 0.375f): Shape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val slant = size.height * slantFraction
        val path = Path().apply {
            moveTo(slant, 0f)          // top-left (inset)
            lineTo(size.width, 0f)     // top-right
            lineTo(size.width, size.height) // bottom-right
            lineTo(0f, size.height)    // bottom-left (full start)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A slanted, right-anchored brand button: blue parallelogram, white italic label, trailing white
 * glyph(s). Used for "Play a Friend" / "Play AI".
 */
@Composable
fun SlantedMenuButton(
    text: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 84.dp,
    color: Color = PaintBucket.menuAccentBlue,
    contentColor: Color = PaintBucket.white,
    fontSize: androidx.compose.ui.unit.TextUnit = 30.sp,
    onClick: () -> Unit,
    trailingIcons: @Composable RowScope.() -> Unit,
) {
    val shape = remember { slantedButtonShape() }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(start = height * 0.55f, end = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
            )
            Box(modifier = Modifier.size(16.dp))
            trailingIcons()
        }
    }
}

/**
 * A capsule "pill" anchored to a screen edge. The cap on the *inner* end is fully rounded; the
 * outer end is square so it sits flush against the screen edge. Used for the bottom icon tray and
 * the customize/progress pill.
 */
@Composable
fun EdgePill(
    side: PillSide,
    modifier: Modifier = Modifier,
    color: Color = PaintBucket.menuAccentBlue,
    content: @Composable RowScope.() -> Unit,
) {
    val cap = CornerSize(50)
    val zero = CornerSize(0)
    val shape = when (side) {
        PillSide.Start -> RoundedCornerShape(topStart = zero, bottomStart = zero, topEnd = cap, bottomEnd = cap)
        PillSide.End -> RoundedCornerShape(topStart = cap, bottomStart = cap, topEnd = zero, bottomEnd = zero)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** A square, tappable icon button. Icon art is authored white and tinted to [tint]. */
@Composable
fun MenuIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    tint: Color = PaintBucket.white,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size + 16.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit
        )
    }
}

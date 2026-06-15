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
import androidx.compose.ui.text.style.TextAlign
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
    // Label scales with the button so it keeps the SVG's ~0.36×height proportion on every screen
    // (72px label over a 204px button ⇒ 0.353). A fixed sp would read correct on a tablet-sized
    // button but oversized on a phone-sized one.
    fontSize: androidx.compose.ui.unit.TextUnit = (height.value * 0.365f).sp,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
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
            // Equal *visual* start/end inset, both proportional to the button height so they scale
            // with the screen (0.269×height ≈ 28dp on a 104dp tablet button). The right edge is
            // vertical, so the end inset reads directly. The left edge is slanted, so at the label's
            // vertical center the filled background has already receded inward by slant/2
            // (= height * slantFraction / 2). Add that back so the gap from the slanted edge to the
            // label matches the gap on the right.
            .padding(start = height * 0.288f + height * (0.375f / 2f), end = height * 0.269f),
        contentAlignment = Alignment.CenterStart
    ) {
        // The button wraps its content so each label gets symmetric left/right padding inside the
        // rectangle. Buttons are right-anchored at the call site, so the trailing glyphs still line
        // up across every button regardless of label length.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                textAlign = TextAlign.End,
                color = contentColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                softWrap = false,
            )
            // Gap between the label and the trailing glyph(s), scaled with the button height.
            Box(modifier = Modifier.size(height * 0.269f))
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
    // Inner horizontal inset for the icon row. Callers pass a height-proportional value so it scales
    // with the screen instead of staying a fixed 18dp that looks oversized on a phone-sized pill.
    contentPadding: androidx.compose.ui.unit.Dp = 18.dp,
    // When the whole pill is tappable, pass the handler here instead of adding `.clickable` to
    // `modifier`. Applying it inside (after `.clip(shape)`) lets the press ripple follow the pill's
    // rounded shape; a caller-side `.clickable` runs before the clip and the ripple stays a rectangle.
    onClick: (() -> Unit)? = null,
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
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = contentPadding),
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
    size: androidx.compose.ui.unit.Dp = 32.dp,
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

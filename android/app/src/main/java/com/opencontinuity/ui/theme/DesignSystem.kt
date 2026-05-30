package com.opencontinuity.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Typography
// ─────────────────────────────────────────────────────────────────────────────

val EtherealDisplayStyle = TextStyle(
    fontSize     = 40.sp,
    fontWeight   = FontWeight.Bold,
    letterSpacing = (-0.5).sp
)

val EtherealHeadlineLg = TextStyle(
    fontSize     = 28.sp,
    fontWeight   = FontWeight.Bold,
    letterSpacing = (-0.2).sp
)

val EtherealHeadlineMd = TextStyle(
    fontSize   = 22.sp,
    fontWeight = FontWeight.SemiBold
)

val EtherealBodyLg = TextStyle(
    fontSize   = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp
)

val EtherealBodyMd = TextStyle(
    fontSize   = 14.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 20.sp
)

val EtherealLabelCaps = TextStyle(
    fontSize      = 10.sp,
    fontWeight    = FontWeight.SemiBold,
    letterSpacing = 1.sp
)

// ─────────────────────────────────────────────────────────────────────────────
// Shapes
// ─────────────────────────────────────────────────────────────────────────────

val CrystalCardShape   = RoundedCornerShape(24.dp)
val PillShape          = RoundedCornerShape(50)
val SmallCardShape     = RoundedCornerShape(16.dp)
val IconContainerShape = RoundedCornerShape(12.dp)

// ─────────────────────────────────────────────────────────────────────────────
// GLASSMORPHISM CARD — Proper warm rose glass effect
//
// The key to matching the reference:
//  1) Warm dark-rose semi-transparent FILL (not near-clear white)
//  2) Vertical gradient from slightly lighter top to darker bottom (glass depth)
//  3) Top-edge bright "glass rim" highlight line
//  4) Asymmetric border — brighter on top-left, dimmer on bottom-right
//  5) Subtle inner radial glow
// ─────────────────────────────────────────────────────────────────────────────

// Warm card interior colors
private val GlassTop    = Color(0xFF3D1828)   // warm dark rose — top of card
private val GlassMid    = Color(0xFF2A1020)   // slightly darker middle
private val GlassBottom = Color(0xFF1E0B18)   // deepest at bottom

// Border edge colors
private val GlassRimBright  = Color(0x55FF78B7)  // bright pink rim (top edge)
private val GlassRimDim     = Color(0x18FF4080)  // dim at bottom edge
private val GlassRimSide    = Color(0x2AFF5090)  // side edges

@Composable
fun CrystalCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CrystalCardShape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            // ── 1. Warm glass fill gradient (top-to-bottom depth) ─────────
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to GlassTop,
                        0.4f to GlassMid,
                        1.0f to GlassBottom
                    )
                )
            )
            // ── 2. Subtle inner radial glow (pink light from inside) ───────
            .drawBehind {
                // Soft radial pink glow in upper-left of card
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x22FF4F96),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.minDimension * 0.9f
                    )
                )
            }
            // ── 3. Top glass-rim highlight + asymmetric border ─────────────
            .drawWithContent {
                drawContent()
                val cornerRad = 24.dp.toPx()

                // Top highlight line — bright shimmer across the top edge
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x44FFFFFF),
                            Color(0x88FFFFFF),
                            Color(0x44FFFFFF),
                            Color.Transparent
                        )
                    ),
                    size  = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(cornerRad),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Full border with gradient (bright top-left, dim bottom-right)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f  to Color(0x55FF78B7),   // bright pink — top left
                            0.3f  to Color(0x33FF5090),
                            0.6f  to Color(0x18FF3070),
                            1.0f  to Color(0x22FF78B7)    // subtle at bottom right
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cornerRad),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// AMBIENT BACKGROUND — Rich atmospheric rose/burgundy glows
// The reference has very prominent radial glows — NOT subtle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    // Base — solid deep OLED black
    Box(modifier = modifier.fillMaxSize().background(EtherealBackground))

    // TOP-LEFT — strong rose-burgundy glow (biggest atmospheric element)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xBB4B0826),   // rich dark rose, very opaque
                        0.4f to Color(0x554B0826),
                        1.0f to Color(0x004B0826)
                    ),
                    center = Offset(0f, 0f),
                    radius = 1800f
                )
            )
    )

    // BOTTOM-RIGHT — deep burgundy counter-glow
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0x993E0022),
                        0.5f to Color(0x443E0022),
                        1.0f to Color(0x003E0022)
                    ),
                    center = Offset(2400f, 3600f),
                    radius = 2000f
                )
            )
    )

    // CENTER BLUSH — gentle warm haze across the middle
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0x222D0E1A),
                        0.5f to Color(0x112D0E1A),
                        1.0f to Color(0x002D0E1A)
                    ),
                    center = Offset(700f, 1500f),
                    radius = 1600f
                )
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// GLOWING AVATAR RING — gradient rotating border like the reference
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlowingAvatarRing(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // Outer glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HotPink.copy(alpha = 0.5f),
                            HotPink.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        radius = this.size.minDimension * 0.65f
                    )
                )
                // Gradient ring border
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            HotPink,
                            Color(0xFFFF9EC8),
                            HotPink.copy(alpha = 0.3f),
                            HotPink,
                        )
                    ),
                    radius = this.size.minDimension / 2f - 2.dp.toPx(),
                    style  = Stroke(width = 2.5.dp.toPx())
                )
            }
    ) {
        // Inner clip for the avatar image/icon
        Box(
            modifier = Modifier
                .size(size - 6.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(EtherealSurfaceContainer),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GLOWING PINK BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlowingPinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .drawBehind {
                // Pink glow shadow underneath button
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HotPink.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        radius = size.width * 0.6f
                    ),
                    cornerRadius = CornerRadius(50f),
                    size  = Size(size.width, size.height + 20f),
                    topLeft = Offset(0f, 10f)
                )
            },
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = HotPink,
            contentColor   = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = EtherealBodyLg.copy(fontWeight = FontWeight.SemiBold))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ETHEREAL OUTLINED BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EtherealOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(PillShape)
            // Glass background
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GlassTop, GlassBottom)
                )
            )
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x44FF78B7),
                            Color(0x22FF5090)
                        )
                    ),
                    cornerRadius = CornerRadius(50f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = PillShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EtherealPrimary),
            border = null
        ) {
            if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = EtherealBodyLg.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EtherealSectionHeader(
    title: String,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = title, style = EtherealHeadlineMd, color = Color.White.copy(alpha = 0.95f))
        if (trailingText != null) {
            TextButton(onClick = { onTrailingClick?.invoke() }) {
                Text(text = trailingText, style = EtherealBodyMd, color = EtherealPrimary)
                Text(text = " ›",         style = EtherealBodyMd, color = EtherealPrimary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS TOGGLE ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container — mini glass circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3D1828), Color(0xFF1E0B18))
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0x44FF78B7), Color(0x11FF5090), Color(0x44FF78B7))
                        ),
                        radius = size.minDimension / 2f - 0.5.dp.toPx(),
                        style  = Stroke(width = 1.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = EtherealPrimary, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title,    style = EtherealBodyLg.copy(fontWeight = FontWeight.Medium), color = EtherealOnSurface)
            Text(text = subtitle, style = EtherealBodyMd, color = EtherealOnSurfaceVariant.copy(alpha = 0.7f))
        }

        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor    = Color.White,
                checkedTrackColor    = HotPink.copy(alpha = 0.7f),
                uncheckedThumbColor  = EtherealOnSurfaceVariant.copy(alpha = 0.6f),
                uncheckedTrackColor  = Color(0xFF2A1020)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ETHEREAL DIVIDER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EtherealDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x33FF78B7),
                        Color.Transparent
                    )
                )
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PINK ICON CONTAINER (tool grid)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PinkIconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(brush = Brush.linearGradient(colors = listOf(EtherealSurfaceContainerHighest, EtherealSurfaceContainer))),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EtherealPrimary, modifier = Modifier.size(size * 0.55f))
    }
}

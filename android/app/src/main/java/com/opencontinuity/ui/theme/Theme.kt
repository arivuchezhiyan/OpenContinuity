package com.opencontinuity.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────────────────
// Ethereal Noir — Rosy Glassmorphism Color Palette
// ─────────────────────────────────────────────────────────────────────────────

// Background / Surface
val EtherealBackground       = Color(0xFF050207) // Deep OLED void
val EtherealSurface          = Color(0xFF1C1013)
val EtherealSurfaceContainer = Color(0xFF291C20)
val EtherealSurfaceContainerLow  = Color(0xFF25181C)
val EtherealSurfaceContainerHigh = Color(0xFF35262A)
val EtherealSurfaceContainerHighest = Color(0xFF403135)
val EtherealSurfaceBright    = Color(0xFF453539)
val EtherealSurfaceVariant   = Color(0xFF403135)

// Primary — Rose Pink
val EtherealPrimary              = Color(0xFFFFB1C8)
val EtherealOnPrimary            = Color(0xFF650032)
val EtherealPrimaryContainer     = Color(0xFFFF5A9B)
val EtherealOnPrimaryContainer   = Color(0xFF630032)
val EtherealInversePrimary       = Color(0xFFB31A62)

// Secondary
val EtherealSecondary            = Color(0xFFFFB0CE)
val EtherealOnSecondary          = Color(0xFF63003B)
val EtherealSecondaryContainer   = Color(0xFF8A1656)
val EtherealOnSecondaryContainer = Color(0xFFFF99C3)

// Tertiary
val EtherealTertiary             = Color(0xFFFFB1C7)
val EtherealOnTertiary           = Color(0xFF650032)
val EtherealTertiaryContainer    = Color(0xFFF9619A)
val EtherealOnTertiaryContainer  = Color(0xFF630031)

// On-Surface
val EtherealOnBackground    = Color(0xFFF5DCE1)
val EtherealOnSurface       = Color(0xFFF5DCE1)
val EtherealOnSurfaceVariant= Color(0xFFDFBEC6)
val EtherealInverseSurface  = Color(0xFFF5DCE1)
val EtherealInverseOnSurface= Color(0xFF3B2C30)

// Outline
val EtherealOutline         = Color(0xFFA68990)
val EtherealOutlineVariant  = Color(0xFF584047)

// Error
val EtherealError           = Color(0xFFFFB4AB)
val EtherealOnError         = Color(0xFF690005)
val EtherealErrorContainer  = Color(0xFF93000A)
val EtherealOnErrorContainer= Color(0xFFFFDAD6)

// Accent glows (used directly in composables)
val HotPink                 = Color(0xFFFF4F96)
val DeepPink                = Color(0xFFFF78B7)
val ActiveGreen             = Color(0xFF4ADE80)

// ─────────────────────────────────────────────────────────────────────────────

private val EtherealNoir = darkColorScheme(
    primary                = EtherealPrimary,
    onPrimary              = EtherealOnPrimary,
    primaryContainer       = EtherealPrimaryContainer,
    onPrimaryContainer     = EtherealOnPrimaryContainer,
    inversePrimary         = EtherealInversePrimary,

    secondary              = EtherealSecondary,
    onSecondary            = EtherealOnSecondary,
    secondaryContainer     = EtherealSecondaryContainer,
    onSecondaryContainer   = EtherealOnSecondaryContainer,

    tertiary               = EtherealTertiary,
    onTertiary             = EtherealOnTertiary,
    tertiaryContainer      = EtherealTertiaryContainer,
    onTertiaryContainer    = EtherealOnTertiaryContainer,

    background             = EtherealBackground,
    onBackground           = EtherealOnBackground,

    surface                = EtherealSurface,
    onSurface              = EtherealOnSurface,
    surfaceVariant         = EtherealSurfaceVariant,
    onSurfaceVariant       = EtherealOnSurfaceVariant,
    surfaceTint            = EtherealPrimary,
    inverseSurface         = EtherealInverseSurface,
    inverseOnSurface       = EtherealInverseOnSurface,

    outline                = EtherealOutline,
    outlineVariant         = EtherealOutlineVariant,

    error                  = EtherealError,
    onError                = EtherealOnError,
    errorContainer         = EtherealErrorContainer,
    onErrorContainer       = EtherealOnErrorContainer,

    scrim                  = Color(0xFF000000)
)

@Composable
fun OpenContinuityTheme(
    // Always force dark / Ethereal Noir — override system setting
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disabled — always use our Ethereal Noir palette
    content: @Composable () -> Unit
) {
    val colorScheme = EtherealNoir

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = EtherealBackground.toArgb()
            window.navigationBarColor = EtherealBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

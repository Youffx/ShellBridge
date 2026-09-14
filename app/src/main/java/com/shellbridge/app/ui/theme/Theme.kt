package com.shellbridge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val ThemeState = androidx.compose.runtime.mutableStateOf(TerminalThemes.catppuccinMocha)

fun setCurrentPalette(palette: TerminalPalette) {
    ThemeState.value = palette
}

fun currentPalette(): TerminalPalette = ThemeState.value

@Composable
fun ShellBridgeTheme(content: @Composable () -> Unit) {
    val palette = remember { ThemeState }
    val colorScheme = darkColorScheme(
        primary = palette.value.accent,
        secondary = palette.value.cyan,
        tertiary = palette.value.magenta,
        background = palette.value.background,
        surface = palette.value.surface,
        surfaceVariant = palette.value.surfaceVariant,
        onPrimary = palette.value.background,
        onSecondary = palette.value.background,
        onTertiary = palette.value.background,
        onBackground = palette.value.foreground,
        onSurface = palette.value.onSurface,
        onSurfaceVariant = palette.value.foreground,
        error = palette.value.red,
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

package com.alghanim.qibla.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    secondary = EmeraldSecondary,
    tertiary = MintGlow,
    background = SlateBackground,
    surface = SlateSurface,
    onPrimary = SlateBackground,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight
)

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    secondary = EmeraldSecondary,
    tertiary = MintGlow,
    background = SlateBackground,
    surface = SlateSurface,
    onPrimary = SlateBackground,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun QiblaTheme(
    darkTheme: Boolean = true, // Force Dark theme for premium look
    dynamicColor: Boolean = false, // Disable dynamic colors to keep premium slate-emerald-gold theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
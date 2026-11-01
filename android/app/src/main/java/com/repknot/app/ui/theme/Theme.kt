package com.repknot.app.ui.theme

import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RepknotColors = darkColorScheme(
    primary = Color(0xFFC6D6A4),
    onPrimary = Color(0xFF283219),
    background = Color(0xFF171715),
    onBackground = Color(0xFFE8E5DE),
    surface = Color(0xFF171715),
    onSurface = Color(0xFFE8E5DE),
    surfaceVariant = Color(0xFF292925),
    onSurfaceVariant = Color(0xFFC5C5BA),
)

/** Always dark; use wallpaper colors where available. */
@Composable
fun RepknotTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current) else RepknotColors
    MaterialTheme(colorScheme = colors, content = content)
}

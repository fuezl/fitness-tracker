package ru.fuezl.gymdiary.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.fuezl.gymdiary.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF006A6A),
    tertiary = Color(0xFF8A5100),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD77F),
    secondary = Color(0xFF55D7D5),
    tertiary = Color(0xFFFFB95C),
)

@Composable
fun GymDiaryTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

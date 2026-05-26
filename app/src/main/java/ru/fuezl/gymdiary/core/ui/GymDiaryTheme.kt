package ru.fuezl.gymdiary.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.fuezl.gymdiary.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6B4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F2E4),
    onPrimaryContainer = Color(0xFF062017),
    secondary = Color(0xFF375E70),
    secondaryContainer = Color(0xFFD4EAF4),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFFF8FAF7),
    surface = Color(0xFFFDFDF9),
    surfaceVariant = Color(0xFFE1E7E0),
    outlineVariant = Color(0xFFC5CCC4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD8AF),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA8F5C9),
    secondary = Color(0xFFA9CEE0),
    secondaryContainer = Color(0xFF20495A),
    tertiary = Color(0xFFFFB95C),
    tertiaryContainer = Color(0xFF693C00),
    background = Color(0xFF101412),
    surface = Color(0xFF171C19),
    surfaceVariant = Color(0xFF404942),
    outlineVariant = Color(0xFF404942)
)

private val GymShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val GymTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun GymDiaryTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = colors, typography = GymTypography, shapes = GymShapes, content = content)
}

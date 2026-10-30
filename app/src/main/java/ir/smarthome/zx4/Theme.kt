package ir.smarthome.zx4

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Medium),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontFamily = Vazir),
    headlineSmall = base.headlineSmall.copy(fontFamily = Vazir),
    titleLarge = base.titleLarge.copy(fontFamily = Vazir),
    titleMedium = base.titleMedium.copy(fontFamily = Vazir),
    titleSmall = base.titleSmall.copy(fontFamily = Vazir),
    bodyLarge = base.bodyLarge.copy(fontFamily = Vazir),
    bodyMedium = base.bodyMedium.copy(fontFamily = Vazir),
    bodySmall = base.bodySmall.copy(fontFamily = Vazir),
    labelLarge = base.labelLarge.copy(fontFamily = Vazir),
    labelMedium = base.labelMedium.copy(fontFamily = Vazir),
    labelSmall = base.labelSmall.copy(fontFamily = Vazir),
)

/** mode: 0 = سیستم، 1 = روشن، 2 = زغالی تیره */
@Composable
fun AppTheme(mode: Int, accentIdx: Int, content: @Composable () -> Unit) {
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val accent = Color(Palette[accentIdx.coerceIn(0, Palette.lastIndex)])
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFE6E6E6),
            onPrimary = Color(0xFF202020),
            primaryContainer = Color(0xFF414141),
            onPrimaryContainer = Color(0xFFF7F7F7),
            secondary = Color(0xFFCACACA),
            onSecondary = Color(0xFF202020),
            secondaryContainer = Color(0xFF3A3A3A),
            onSecondaryContainer = Color(0xFFF4F4F4),
            background = Color(0xFF202020),
            onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF292929),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF353535),
            onSurfaceVariant = Color(0xFFD0D0D0),
            outline = Color(0xFF777777),
            outlineVariant = Color(0xFF4A4A4A),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFF1B1B1B),
            surfaceContainerLow = Color(0xFF242424),
            surfaceContainer = Color(0xFF292929),
            surfaceContainerHigh = Color(0xFF303030),
            surfaceContainerHighest = Color(0xFF393939),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEDEDED),
            onPrimaryContainer = Color(0xFF202020),
            secondary = Color(0xFF505050),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAEAEA),
            onSecondaryContainer = Color(0xFF242424),
            background = Color.White,
            onBackground = Color(0xFF171717),
            surface = Color.White,
            onSurface = Color(0xFF171717),
            surfaceVariant = Color(0xFFF1F1F1),
            onSurfaceVariant = Color(0xFF555555),
            outline = Color(0xFF858585),
            outlineVariant = Color(0xFFD8D8D8),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF5F5F5),
            surfaceContainerHigh = Color(0xFFEFEFEF),
            surfaceContainerHighest = Color(0xFFE8E8E8),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

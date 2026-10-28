package ir.smarthome.zx4

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/** mode: 0 = سیستم ، 1 = روشن ، 2 = تیره (زغالی خنثی) */
@Composable
fun AppTheme(mode: Int, accentIdx: Int, content: @Composable () -> Unit) {
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val a = Color(Palette[accentIdx % Palette.size])
    val scheme = if (dark) {
        // Dark mode uses neutral charcoal; the accent must not tint the whole UI green or navy.
        val charcoal = Color(0xFF303030)
        darkColorScheme(
            primary = Color(0xFFD6D6D6), onPrimary = Color(0xFF202020),
            primaryContainer = charcoal, onPrimaryContainer = Color(0xFFF2F2F2),
            secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF202020),
            secondaryContainer = charcoal, onSecondaryContainer = Color(0xFFF2F2F2),
            background = Color(0xFF121212), onBackground = Color(0xFFEDEDED),
            surface = Color(0xFF1E1E1E), onSurface = Color(0xFFEDEDED),
            surfaceVariant = Color(0xFF2B2B2B), onSurfaceVariant = Color(0xFFB3B3B3),
            outline = Color(0xFF6E6E6E), outlineVariant = Color(0xFF3A3A3A), surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF262626),
            surfaceContainerHighest = Color(0xFF2E2E2E),
        )
    } else {
        val pc = lerp(Color.White, a, 0.18f)
        val onPc = lerp(a, Color.Black, 0.6f)
        lightColorScheme(
            primary = a, onPrimary = Color.White,
            primaryContainer = pc, onPrimaryContainer = onPc,
            secondaryContainer = pc, onSecondaryContainer = onPc,
            background = Color(0xFFF4F6F6), surface = Color.White,
            surfaceVariant = Color(0xFFE9ECEC), onSurfaceVariant = Color(0xFF5A6060),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8F8),
            surfaceContainer = Color(0xFFF1F4F4), surfaceContainerHigh = Color(0xFFEBEFEF),
            surfaceContainerHighest = Color(0xFFE5E9E9),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

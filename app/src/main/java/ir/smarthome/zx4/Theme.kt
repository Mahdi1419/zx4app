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
            primaryContainer = Color(0xFF393939), onPrimaryContainer = Color(0xFFF5F5F5),
            secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF202020),
            secondaryContainer = Color(0xFF393939), onSecondaryContainer = Color(0xFFF5F5F5),
            background = Color(0xFF101114), onBackground = Color(0xFFF4F4F6),
            surface = Color(0xFF202126), onSurface = Color(0xFFF4F4F6),
            surfaceVariant = Color(0xFF303137), onSurfaceVariant = Color(0xFFC2C3CC),
            outline = Color(0xFF858690), outlineVariant = Color(0xFF41424A), surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFF111216), surfaceContainerLow = Color(0xFF191A20),
            surfaceContainer = Color(0xFF202126), surfaceContainerHigh = Color(0xFF292A31),
            surfaceContainerHighest = Color(0xFF34353D),
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

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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF4F7F7), surface = Color.White,
    surfaceVariant = Color(0xFFE6ECEC), onSurfaceVariant = Color(0xFF506060),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0D1314), surface = Color(0xFF162021),
    surfaceVariant = Color(0xFF223132), onSurfaceVariant = Color(0xFFA9BDBD),
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}

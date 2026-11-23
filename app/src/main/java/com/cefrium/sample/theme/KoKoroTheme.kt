package com.cefrium.sample.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cefrium.sample.R

enum class SamuraiThemeId(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean,
) {
    MURASAKI_SHOGUN(
        id = "murasaki_shogun",
        displayName = "Murasaki Shogun",
        subtitle = "Lacca notte, viola ametista e oro imperiale",
        isDark = true,
    ),
    KUROGANE_BLADE(
        id = "kurogane_blade",
        displayName = "Kurogane Blade",
        subtitle = "Ossidiana forgiata, cremisi katana e oro",
        isDark = true,
    ),
    WASHI_IVORY(
        id = "washi_ivory",
        displayName = "Washi Ivory",
        subtitle = "Carta washi diurna, indaco inchiostro e oro",
        isDark = false,
    );

    companion object {
        fun fromId(raw: String?): SamuraiThemeId =
            entries.firstOrNull { it.id == raw } ?: MURASAKI_SHOGUN
    }
}

@Immutable
data class SamuraiPalette(
    val goldAccent: Color,
    val katanaPrimary: Color,
    val katanaGlow: Color,
    val seigaihaStroke: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val boostActive: Color,
    val incognitoSurface: Color,
    val incognitoAccent: Color,
    val thermalOk: Color,
    val thermalWarm: Color,
    val thermalHot: Color,
)

private val MurasakiColorScheme = darkColorScheme(
    primary = Color(0xFFA855F7),
    onPrimary = Color(0xFF140724),
    primaryContainer = Color(0xFF2B1845),
    onPrimaryContainer = Color(0xFFF3E4FF),
    secondary = Color(0xFFF5C542),
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF332610),
    onSecondaryContainer = Color(0xFFFFEBA8),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF02261A),
    background = Color(0xFF0B0914),
    onBackground = Color(0xFFF3EFFB),
    surface = Color(0xFF120E20),
    onSurface = Color(0xFFF3EFFB),
    surfaceVariant = Color(0xFF1C1630),
    onSurfaceVariant = Color(0xFFB5AACB),
    surfaceContainerLowest = Color(0xFF080610),
    surfaceContainerLow = Color(0xFF110D1E),
    surfaceContainer = Color(0xFF171229),
    surfaceContainerHigh = Color(0xFF211A38),
    surfaceContainerHighest = Color(0xFF2B2247),
    outline = Color(0xFF473A69),
    outlineVariant = Color(0xFF2E2447),
    error = Color(0xFFFF5C77),
    onError = Color(0xFF30000D),
)

private val MurasakiExtras = SamuraiPalette(
    goldAccent = Color(0xFFF5C542),
    katanaPrimary = Color(0xFFA855F7),
    katanaGlow = Color(0xFFC084FC),
    seigaihaStroke = Color(0x18A855F7),
    glassSurface = Color(0xE6151026),
    glassBorder = Color(0x55F5C542),
    boostActive = Color(0xFFF5C542),
    incognitoSurface = Color(0xFF1B1238),
    incognitoAccent = Color(0xFFC084FC),
    thermalOk = Color(0xFF34D399),
    thermalWarm = Color(0xFFFBBF24),
    thermalHot = Color(0xFFFF5C77),
)

private val KuroganeColorScheme = darkColorScheme(
    primary = Color(0xFFE63946),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3D1016),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFD4AF37),
    onSecondary = Color(0xFF1E1700),
    secondaryContainer = Color(0xFF2E250B),
    onSecondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF002030),
    background = Color(0xFF090A0D),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF1A1D25),
    onSurfaceVariant = Color(0xFFA9B0C0),
    surfaceContainerLowest = Color(0xFF060709),
    surfaceContainerLow = Color(0xFF0F1116),
    surfaceContainer = Color(0xFF151820),
    surfaceContainerHigh = Color(0xFF1E222D),
    surfaceContainerHighest = Color(0xFF272C3A),
    outline = Color(0xFF444A5B),
    outlineVariant = Color(0xFF2A2F3D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF330005),
)

private val KuroganeExtras = SamuraiPalette(
    goldAccent = Color(0xFFD4AF37),
    katanaPrimary = Color(0xFFE63946),
    katanaGlow = Color(0xFFFF6B78),
    seigaihaStroke = Color(0x18D4AF37),
    glassSurface = Color(0xE612141B),
    glassBorder = Color(0x66D4AF37),
    boostActive = Color(0xFFE63946),
    incognitoSurface = Color(0xFF1A1326),
    incognitoAccent = Color(0xFFE0B0FF),
    thermalOk = Color(0xFF34D399),
    thermalWarm = Color(0xFFF59E0B),
    thermalHot = Color(0xFFE63946),
)

private val WashiColorScheme = lightColorScheme(
    primary = Color(0xFF5B21B6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF230854),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF3E1C00),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1B1726),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1726),
    surfaceVariant = Color(0xFFECE6DC),
    onSurfaceVariant = Color(0xFF5A5368),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFF2ECE2),
    surfaceContainerHigh = Color(0xFFE8E0D4),
    surfaceContainerHighest = Color(0xFFDFD6C7),
    outline = Color(0xFFB5AA98),
    outlineVariant = Color(0xFFD9D0C2),
    error = Color(0xFFBE123C),
    onError = Color(0xFFFFFFFF),
)

private val WashiExtras = SamuraiPalette(
    goldAccent = Color(0xFFC28A17),
    katanaPrimary = Color(0xFF5B21B6),
    katanaGlow = Color(0xFF7C3AED),
    seigaihaStroke = Color(0x145B21B6),
    glassSurface = Color(0xF2FAF7F2),
    glassBorder = Color(0x55C28A17),
    boostActive = Color(0xFFBE123C),
    incognitoSurface = Color(0xFF261D3D),
    incognitoAccent = Color(0xFFD8B4FE),
    thermalOk = Color(0xFF059669),
    thermalWarm = Color(0xFFD97706),
    thermalHot = Color(0xFFBE123C),
)

val LocalSamuraiPalette = staticCompositionLocalOf { MurasakiExtras }

val CinzelFamily = FontFamily(Font(R.font.cinzel, FontWeight.Normal))
val JakartaFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val MonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val KoKoroTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = CinzelFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = CinzelFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.8.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = CinzelFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.6.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

val KoKoroShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun KoKoroTheme(
    themeId: SamuraiThemeId,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeId) {
        SamuraiThemeId.MURASAKI_SHOGUN -> MurasakiColorScheme
        SamuraiThemeId.KUROGANE_BLADE -> KuroganeColorScheme
        SamuraiThemeId.WASHI_IVORY -> WashiColorScheme
    }
    val extras = when (themeId) {
        SamuraiThemeId.MURASAKI_SHOGUN -> MurasakiExtras
        SamuraiThemeId.KUROGANE_BLADE -> KuroganeExtras
        SamuraiThemeId.WASHI_IVORY -> WashiExtras
    }
    CompositionLocalProvider(LocalSamuraiPalette provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KoKoroTypography,
            shapes = KoKoroShapes,
            content = content,
        )
    }
}

/**
 * Draws a subtle Japanese Seigaiha (concentric wave) Samurai engraving behind panels.
 */
fun Modifier.seigaihaBackdrop(strokeColor: Color, radiusDp: Dp = 26.dp): Modifier =
    this.drawBehind {
        val r = radiusDp.toPx()
        val dx = r * 2f
        val dy = r * 0.9f
        var row = 0
        var y = 0f
        while (y < size.height + r) {
            val offsetX = if (row % 2 == 0) 0f else r
            var x = -r + offsetX
            while (x < size.width + r) {
                for (ring in 1..3) {
                    val rr = r * (ring / 3f)
                    drawCircle(
                        color = strokeColor,
                        radius = rr,
                        center = Offset(x, y),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                x += dx
            }
            y += dy
            row++
        }
    }

/**
 * Precision Samurai-bordered container card.
 */
@Composable
fun SamuraiCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = LocalSamuraiPalette.current.glassBorder.copy(alpha = 0.35f),
    cornerRadius: Dp = 16.dp,
    tonalElevation: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = tonalElevation,
        border = BorderStroke(1.dp, borderColor),
        content = content,
    )
}

/**
 * Animated Katana-slash loading beam along the browser viewport top edge.
 */
@Composable
fun KatanaSlashProgressBar(
    enabled: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val samurai = LocalSamuraiPalette.current
    val transition = rememberInfiniteTransition(label = "katana_slash")
    val phase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animationsEnabled) 1050 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "katana_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp),
    ) {
        val w = size.width
        val y = size.height / 2f
        drawLine(
            color = samurai.katanaPrimary.copy(alpha = 0.22f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = size.height,
        )
        val beamWidth = w * 0.38f
        val head = w * phase
        val tail = (head - beamWidth).coerceAtLeast(0f)
        val clampedHead = head.coerceAtMost(w)
        if (clampedHead > tail) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        samurai.katanaPrimary,
                        samurai.goldAccent,
                        Color.White,
                    ),
                    startX = tail,
                    endX = clampedHead,
                ),
                start = Offset(tail, y),
                end = Offset(clampedHead, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
        }
    }
}

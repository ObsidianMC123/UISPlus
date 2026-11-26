package vn.uisplus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import vn.uisplus.R
import vn.uisplus.data.Prefs
import vn.uisplus.data.SubjectColors
import java.time.LocalTime

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Bo mau cua app. contrast = mau "dam nhat" de pha (den o nen sang, trang o nen toi). */
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val sub: Color,
    val faint: Color,
    val line: Color,
    val soft: Color,
    val brand: Color,
    val brand2: Color,
    val violet: Color,
    val contrast: Color,
) {
    companion object {
        // v1.4: 1 mau nhan duy nhat (do PTIT), nen trung tinh, brand2 = brand de moi gradient cu thanh mau dac.
        val Light = Palette(
            dark = false, bg = Color(0xFFF5F5F6), card = Color.White, ink = Color(0xFF15171C),
            sub = Color(0xFF5E6470), faint = Color(0xFF9399A3), line = Color(0xFFE4E5E8), soft = Color(0xFFEFF0F2),
            brand = Color(0xFFC8102E), brand2 = Color(0xFFC8102E), violet = Color(0xFF5B6B85), contrast = Color.Black,
        )
        val Dark = Palette(
            dark = true, bg = Color(0xFF0F1115), card = Color(0xFF171A20), ink = Color(0xFFECEDEF),
            sub = Color(0xFFA0A6B0), faint = Color(0xFF6B717C), line = Color(0xFF262A31), soft = Color(0xFF1F232A),
            brand = Color(0xFFF2545F), brand2 = Color(0xFFF2545F), violet = Color(0xFF94A3B8), contrast = Color.White,
        )
        val Amoled = Palette(
            dark = true, bg = Color.Black, card = Color(0xFF0B0B0C), ink = Color(0xFFF2F2F3),
            sub = Color(0xFF9CA1AA), faint = Color(0xFF666B74), line = Color(0xFF1C1D20), soft = Color(0xFF141518),
            brand = Color(0xFFF2545F), brand2 = Color(0xFFF2545F), violet = Color(0xFF94A3B8), contrast = Color.White,
        )
    }
}

/**
 * Mau dung chung. Doc tu [palette] (snapshot state) nen moi cho dung Ui.X tu doi mau
 * khi doi theme, khong can sua tung man hinh.
 */
object Ui {
    var palette by mutableStateOf(Palette.Light)

    val isDark: Boolean get() = palette.dark
    val Bg: Color get() = palette.bg
    val Card: Color get() = palette.card
    val Ink: Color get() = palette.ink
    val Sub: Color get() = palette.sub
    val Faint: Color get() = palette.faint
    val Line: Color get() = palette.line
    val Soft: Color get() = palette.soft
    val Contrast: Color get() = palette.contrast
    val Brand: Color get() = palette.brand
    val Brand2: Color get() = palette.brand2
    val Violet: Color get() = palette.violet
    val Green = Color(0xFF16A34A)
    val Amber = Color(0xFFD97706)
    val Sky = Color(0xFF0284C7)

    /** Giu ten cu cho code cu, nhung gio la mau dac (het gradient). */
    val BrandGradient: Brush get() = SolidColor(Brand)
    /** Nen nhat cua mau nhan (chip/tab dang chon). */
    val BrandSoft: Color get() = Brand.copy(alpha = if (palette.dark) 0.18f else 0.10f)
}

/** Design token v1.4 — chi dung cac muc nay. */
object Tok {
    // Bo goc: 4 muc
    val RChip = 8.dp
    val RButton = 12.dp
    val RCard = 16.dp
    val RSheet = 24.dp
    // Khoang cach
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 24.dp
    val S6 = 32.dp
    // Chuyen dong: "emphasized" cua Material 3 + spring khong nay
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val DurShort = 150
    const val DurMedium = 300
    const val DurLong = 450
    /** So: chu so dung deu (tabular) */
    const val TNUM = "tnum"
}

/** Mau mon hoc; o nen toi thi dim nhe cho do choi. */
fun subjectColor(maMon: String): Color {
    val c = Color(SubjectColors.argb(maMon))
    return if (Ui.isDark) lerp(c, Ui.Card, 0.12f) else c
}

fun resolveDark(mode: Int, systemDark: Boolean, fromH: Int, toH: Int, hour: Int): Boolean = when (mode) {
    1 -> false
    2, 3 -> true
    4 -> if (fromH == toH) false else if (fromH > toH) hour >= fromH || hour < toH else hour in fromH until toH
    else -> systemDark
}

fun targetPalette(mode: Int, dark: Boolean): Palette = when {
    !dark -> Palette.Light
    mode == 3 -> Palette.Amoled
    else -> Palette.Dark
}

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.inter().copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun UisTheme(prefs: Prefs = Prefs(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    if (prefs.themeMode == 4) {
        LaunchedEffect(Unit) {
            while (true) {
                hour = LocalTime.now().hour
                delay(20_000)
            }
        }
    }
    val dark = resolveDark(prefs.themeMode, systemDark, prefs.darkFromH, prefs.darkToH, hour)
    val ctx = LocalContext.current
    val dyn = prefs.dynamicColor && Build.VERSION.SDK_INT >= 31
    var target = targetPalette(prefs.themeMode, dark)
    if (dyn && Build.VERSION.SDK_INT >= 31) {
        val ds = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        target = target.copy(brand = ds.primary, brand2 = ds.primary, violet = ds.secondary)
    }

    // Doi theme = chuyen mau tu tu (khong nhay trang)
    val spec = tween<Color>(600, easing = FastOutSlowInEasing)
    val bg by animateColorAsState(target.bg, spec, label = "bg")
    val card by animateColorAsState(target.card, spec, label = "card")
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val sub by animateColorAsState(target.sub, spec, label = "sub")
    val faint by animateColorAsState(target.faint, spec, label = "faint")
    val line by animateColorAsState(target.line, spec, label = "line")
    val soft by animateColorAsState(target.soft, spec, label = "soft")
    val brand by animateColorAsState(target.brand, spec, label = "brand")
    val brand2 by animateColorAsState(target.brand2, spec, label = "brand2")
    val violet by animateColorAsState(target.violet, spec, label = "violet")
    val contrast by animateColorAsState(target.contrast, spec, label = "contrast")
    val p = Palette(dark, bg, card, ink, sub, faint, line, soft, brand, brand2, violet, contrast)

    // Lan dau: gan ngay de khung hinh dau tien da dung mau
    remember { Ui.palette = p; 0 }
    SideEffect { if (Ui.palette != p) Ui.palette = p }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    val scheme = if (dark) darkColorScheme(
        primary = p.brand, onPrimary = Color.White, secondary = p.brand2, tertiary = p.violet,
        background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        surfaceVariant = p.soft, onSurfaceVariant = p.sub, outline = p.line, outlineVariant = p.line,
        surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.soft,
    ) else lightColorScheme(
        primary = p.brand, onPrimary = Color.White, secondary = p.brand2, tertiary = p.violet,
        background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        surfaceVariant = Color(0xFFF1F2F6), onSurfaceVariant = p.sub, outline = p.line, outlineVariant = p.line,
        surfaceContainerLow = Color(0xFFF8F9FC), surfaceContainer = p.card, surfaceContainerHigh = p.card,
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

package vn.uisplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.uisplus.ui.theme.Ui
import kotlin.math.cos
import kotlin.math.sin

/** Nhan xuong thi lun nhe, tha ra bat lo xo. */
fun Modifier.bounceClick(scaleDown: Float = 0.97f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // v1.4: lun nhe, tha ra hoi ve mem (khong nay tung tung)
    val target = if (pressed) maxOf(scaleDown, 0.95f) else 1f
    val scale by animateFloatAsState(
        target,
        if (pressed) spring(dampingRatio = 1f, stiffness = 1400f) else spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "bounce",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Hien dan tung item: truot len + mo dan + phong nhe, tre theo index. */
@Composable
fun Stagger(index: Int, key: Any? = Unit, baseDelay: Long = 0, content: @Composable () -> Unit) {
    val p = remember(key) { Animatable(0f) }
    val dy = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(key) {
        delay(baseDelay + (index.coerceAtMost(10) * 40L))
        p.animateTo(1f, tween(420, easing = vn.uisplus.ui.theme.Tok.EmphasizedDecel))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = (p.value * 1.6f).coerceIn(0f, 1f)
            translationY = (1f - p.value) * dy
        }
    ) { content() }
}

/** Nen "cuc quang" — 3 dom mau troi lo lung. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, colors: List<Color> = listOf(Ui.Brand, Ui.Brand2, Ui.Violet)) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a")
    val b by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(19000, easing = LinearEasing)), label = "b")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.55f
        fun blob(c: Color, cx: Float, cy: Float, rr: Float) {
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.12f), c.copy(alpha = 0f)), center = Offset(cx, cy), radius = rr),
                radius = rr, center = Offset(cx, cy),
            )
        }
        blob(colors[0], w * (0.2f + 0.15f * cos(a)), h * (0.18f + 0.08f * sin(a)), r)
        blob(colors[1], w * (0.85f + 0.12f * sin(b)), h * (0.35f + 0.1f * cos(b)), r * 0.9f)
        blob(colors[2], w * (0.5f + 0.2f * cos(b + 1f)), h * (0.85f + 0.06f * sin(a + 2f)), r * 1.1f)
    }
}

/**
 * v1.4: do bong 2 lop trung tinh + vien 1dp thay cho bong phat sang mau.
 * Giu chu ky giong Modifier.shadow de thay the 1-1; mau truyen vao bi bo qua.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.elev(
    elevation: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    clip: Boolean = false,
    ambientColor: Color = Color.Black,
    spotColor: Color = Color.Black,
): Modifier = composed {
    val e = when {
        elevation >= 16.dp -> 6.dp
        elevation >= 8.dp -> 3.dp
        elevation > 0.dp -> 1.dp
        else -> 0.dp
    }
    this
        .shadow(
            if (Ui.isDark) 0.dp else e, shape, clip,
            ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f),
        )
        .border(1.dp, Ui.Line, shape)
}

/** v1.4: chi chay khi force=true (skeleton). Moi cho trang tri cu tu tat. */
fun Modifier.shimmer(color: Color = Color.White, alpha: Float = 0.55f, force: Boolean = false): Modifier =
    if (!force) this else shimmerImpl(color, alpha)

private fun Modifier.shimmerImpl(color: Color, alpha: Float): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                start = Offset(w * x - w * 0.3f, 0f),
                end = Offset(w * x + w * 0.3f, size.height),
            )
        )
    }
}

/** Dong ho so kieu "odometer": moi chu so truot len khi doi. */
@Composable
fun OdometerText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(spring(stiffness = 500f)) { it } + fadeIn(tween(160)))
                        .togetherWith(slideOutVertically(spring(stiffness = 500f)) { -it } + fadeOut(tween(160)))
                },
                label = "odo$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Vong tien do co dau tron. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier, stroke: Dp = 6.dp, track: Color, color: Color) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = 60f), label = "ring")
    Canvas(modifier) {
        val sw = stroke.toPx()
        val sz = Size(size.width - sw, size.height - sw)
        val tl = Offset(sw / 2, sw / 2)
        drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Cham nhap nhay (dang hoc). */
@Composable
fun PulseDot(color: Color, modifier: Modifier, dotSize: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 2.4f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "al")
    Canvas(modifier) {
        val r = dotSize.toPx() / 2
        drawCircle(color.copy(alpha = a), radius = r * s)
        drawCircle(color, radius = r)
    }
}

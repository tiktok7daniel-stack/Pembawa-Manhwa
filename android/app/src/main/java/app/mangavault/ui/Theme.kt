package app.mangavault.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangavault.R
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ palette

val Violet = Color(0xFF8B7CFF)
val Pink = Color(0xFFFF5D8F)
val Peach = Color(0xFFFFB86B)
val Mint = Color(0xFF5EE6D0)
val Danger = Color(0xFFFF6B7A)
val AccentH = Brush.horizontalGradient(listOf(Violet, Pink, Peach))
val AccentD = Brush.linearGradient(listOf(Violet, Pink, Peach))

@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val stroke: Color,
    val strokeFade: Color,
    val sheet: Color,
    val blobs: List<Color>,
)

val DarkPalette = Palette(
    true, Color(0xFF0B0B10), Color(0xFFF5F5F7), Color(0xFF9C9CAE), Color(0xFF5A5A6C),
    Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f), Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f),
    Color(0xF2121219), listOf(Color(0xFF3A2E9A), Color(0xFF8C2A5C), Color(0xFF155A68)),
)
val LightPalette = Palette(
    false, Color(0xFFEDEEF4), Color(0xFF14141B), Color(0xFF5E5F72), Color(0xFFA3A4B5),
    Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.5f), Color.White, Color.Black.copy(alpha = 0.06f),
    Color(0xF7F6F7FB), listOf(Color(0xFFC6BEFF), Color(0xFFFFC4D6), Color(0xFFB8EEE4)),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light), Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium), Font(R.font.poppins_semibold, FontWeight.SemiBold),
)
val TajawalFamily = FontFamily(
    Font(R.font.tajawal_light, FontWeight.Light), Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium), Font(R.font.tajawal_bold, FontWeight.SemiBold), Font(R.font.tajawal_bold, FontWeight.Bold),
)

@Composable
fun MangaVaultTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Pink, background = p.bg, surface = p.bg, onSurface = p.text, onBackground = p.text)
    else lightColorScheme(primary = Pink, background = p.bg, surface = p.bg, onSurface = p.text, onBackground = p.text)
    val font = if (LocalLang.current == "ar") TajawalFamily else Poppins
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides p,
            LocalTextStyle provides TextStyle(fontFamily = font, color = p.text, fontSize = 15.sp),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ surfaces

/** Slow-moving blurred colour fields behind everything. */
@Composable
fun Aurora(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.bg)
        val w = size.width
        val h = size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        }
        blob(p.blobs[0], w * (0.05f + 0.35f * a), h * (0.08f + 0.10f * b), w * 0.95f)
        blob(p.blobs[1], w * (1.0f - 0.35f * b), h * (0.52f + 0.12f * a), w * 0.85f)
        blob(p.blobs[2], w * (0.25f + 0.45f * b), h * (1.0f - 0.08f * a), w * 0.80f)
    }
}

@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier {
    val p = LocalPalette.current
    return this.clip(shape)
        .background(Brush.verticalGradient(listOf(p.glassTop, p.glassBottom)), shape)
        .border(1.dp, Brush.verticalGradient(listOf(p.stroke, p.strokeFade)), shape)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(26.dp), padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(shape).padding(padding), content = content)
}

/** Click with a soft spring "press" instead of a ripple. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Fades and lifts content in, staggered by [index]. */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * 36.dp.toPx() }) { content() }
}

// ------------------------------------------------------------------ text helpers

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14) =
    Text(text, modifier, color = LocalPalette.current.muted, fontSize = size.sp, lineHeight = (size * 1.55).sp)

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, color = LocalPalette.current.muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)

// ------------------------------------------------------------------ controls

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "btn")
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sx by shimmer.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sx")
    Box(
        modifier.fillMaxWidth().height(62.dp).graphicsLayer { this.alpha = alpha }
            .shadow(22.dp, CircleShape, ambientColor = Pink, spotColor = Pink)
            .clip(CircleShape).background(AccentH)
            .drawBehind {
                if (loading) {
                    val x = size.width * sx
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), x - 200f, x + 200f))
                }
            }
            .bouncyClick(enabled && !loading, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(46.dp))
            Text(text, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                if (loading) Spinner(22.dp, Color.White)
                else Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun GlassChip(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.then(if (selected) Modifier.clip(CircleShape).background(AccentH) else Modifier.glass(CircleShape))
            .bouncyClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Glass capsule with a sliding gradient thumb. */
@Composable
fun Segmented(options: List<String>, index: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BoxWithConstraints(modifier.fillMaxWidth().height(50.dp).glass(CircleShape).padding(4.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * index, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(10.dp, CircleShape, spotColor = Pink).clip(CircleShape).background(AccentH))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val c by animateColorAsState(if (i == index) Color.White else p.muted, label = "segc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, color = c, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.55f, stiffness = 600f), label = "tg")
    val p = LocalPalette.current
    Box(
        Modifier.width(54.dp).height(32.dp).then(if (checked) Modifier.clip(CircleShape).background(AccentH) else Modifier.glass(CircleShape))
            .clickable { onChange(!checked) }.padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(if (checked) Color.White else p.muted))
    }
}

@Composable
fun CheckOrb(checked: Boolean) {
    val scale by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "chk")
    val p = LocalPalette.current
    Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, if (checked) Color.Transparent else p.faint, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(AccentD), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun Spinner(size: Dp, color: Color = Pink) {
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(size).rotate(r)) {
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, color)), 0f, 300f, false, style = Stroke(size.toPx() * 0.12f, cap = StrokeCap.Round))
    }
}

/** Neumorphic dial with a glowing gradient arc, like a balance ring. */
@Composable
fun RingProgress(progress: Float, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            // soft outer depth
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = if (p.dark) 0.55f else 0.12f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.08f else 0.9f), Color.Black.copy(alpha = if (p.dark) 0.35f else 0.06f)),
                Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r * 0.92f, c)
            drawCircle(p.bg, r * 0.70f, c)
            val stroke = r * 0.09f
            val arcR = r * 0.81f
            val tl = Offset(c.x - arcR, c.y - arcR)
            val sz = Size(arcR * 2, arcR * 2)
            drawArc(p.faint.copy(alpha = 0.35f), 0f, 360f, false, tl, sz, style = Stroke(stroke))
            val sweep = Brush.sweepGradient(listOf(Violet, Pink, Peach, Mint, Violet), c)
            rotate(-90f, c) {
                drawArc(sweep, 0f, 360f * anim, false, tl, sz, alpha = 0.25f, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(sweep, 0f, 360f * anim, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

// ------------------------------------------------------------------ brand & icons

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).shadow(12.dp, CircleShape, spotColor = Pink).clip(CircleShape).background(AccentD)
                .drawBehind {
                    val w = size.width
                    val s = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
                    drawRoundRect(Color.White, Offset(w * 0.3f, w * 0.24f), Size(w * 0.4f, w * 0.52f), CornerRadius(w * 0.07f), style = s)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.36f), Offset(w * 0.5f, w * 0.6f), w * 0.075f, StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.62f), Offset(w * 0.42f, w * 0.52f), w * 0.075f, StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.62f), Offset(w * 0.58f, w * 0.52f), w * 0.075f, StrokeCap.Round)
                },
        )
        Spacer(Modifier.width(10.dp))
        Text("MangaVault", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFamily = Poppins)
    }
}

/** Chain-link glyph drawn in code. */
@Composable
fun LinkGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        rotate(-45f) {
            val stroke = Stroke(width = w * 0.1f)
            drawRoundRect(color, Offset(w * 0.02f, h * 0.33f), Size(w * 0.56f, h * 0.34f), CornerRadius(h * 0.17f), style = stroke)
            drawRoundRect(color, Offset(w * 0.42f, h * 0.33f), Size(w * 0.56f, h * 0.34f), CornerRadius(h * 0.17f), style = stroke)
        }
    }
}

val DownloadIcon: ImageVector = materialIcon(name = "MvDownload") {
    materialPath {
        moveTo(5f, 20f); horizontalLineTo(19f); verticalLineTo(18f); horizontalLineTo(5f); close()
        moveTo(19f, 9f); horizontalLineTo(15f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(5f)
        lineTo(12f, 16f); close()
    }
}

package app.mangavault.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangavault.R

// ------------------------------------------------------------------ palette

/** "Mana" blue: the one accent colour, used for energy, focus and progress. */
val Mana = Color(0xFF3D8BFF)
val ManaGlow = Color(0xFF6FE3FF)
val ManaDeep = Color(0xFF2448FF)
val Arcane = Color(0xFF8C5CFF)
val Success = Color(0xFF2EE6A6)
val Danger = Color(0xFFFF3D5E)
val Amber = Color(0xFFFFB547)
val ManaH = Brush.horizontalGradient(listOf(ManaDeep, Mana, ManaGlow))
val ManaV = Brush.verticalGradient(listOf(ManaGlow, Mana, ManaDeep))

@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val lineHi: Color,
    val sheet: Color,
    val shine: Color,
    val glowTop: Color,
    val glowSide: Color,
    val dots: Color,
)

val DarkPalette = Palette(
    dark = true, bg = Color(0xFF05060A), surface = Color(0xFF0A0C13), surfaceHi = Color(0xFF111522),
    text = Color(0xFFEEF2FF), muted = Color(0xFF8790AB), faint = Color(0xFF3C4258),
    line = Color.White.copy(alpha = 0.07f), lineHi = Color(0xFF6FA8FF).copy(alpha = 0.30f), sheet = Color(0xFF0B0D15),
    shine = Color.White.copy(alpha = 0.06f), glowTop = Mana.copy(alpha = 0.20f), glowSide = Arcane.copy(alpha = 0.10f),
    dots = Color.White.copy(alpha = 0.055f),
)
val LightPalette = Palette(
    dark = false, bg = Color(0xFFF1F3F8), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFF8F9FC),
    text = Color(0xFF0A0D16), muted = Color(0xFF5A6276), faint = Color(0xFFB7BCCB),
    line = Color.Black.copy(alpha = 0.08f), lineHi = Mana.copy(alpha = 0.40f), sheet = Color(0xFFFFFFFF),
    shine = Color.White.copy(alpha = 0.7f), glowTop = Mana.copy(alpha = 0.12f), glowSide = Arcane.copy(alpha = 0.06f),
    dots = Color.Black.copy(alpha = 0.05f),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// ------------------------------------------------------------------ type

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold), Font(R.font.poppins_semibold, FontWeight.Bold),
)
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold), Font(R.font.tajawal_bold, FontWeight.Bold),
)
/** Condensed, angular display face for headings and numbers. */
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Normal), Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold), Font(R.font.rajdhani_bold, FontWeight.Bold),
)
val Changa = FontFamily(
    Font(R.font.changa_semibold, FontWeight.Normal), Font(R.font.changa_semibold, FontWeight.Medium),
    Font(R.font.changa_semibold, FontWeight.SemiBold), Font(R.font.changa_bold, FontWeight.Bold),
)

@Immutable
class Fonts(val body: FontFamily, val display: FontFamily)

val LocalFonts = staticCompositionLocalOf { Fonts(Poppins, Rajdhani) }

@Composable
fun display(): FontFamily = LocalFonts.current.display

@Composable
fun MangaVaultTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Mana, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text)
    else lightColorScheme(primary = Mana, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text)
    val fonts = if (LocalLang.current == "ar") Fonts(Tajawal, Changa) else Fonts(Poppins, Rajdhani)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides p,
            LocalFonts provides fonts,
            LocalTextStyle provides TextStyle(fontFamily = fonts.body, color = p.text, fontSize = 15.sp),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ shapes

val PanelShape = RoundedCornerShape(6.dp)
val BladeShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
val SmallBlade = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
val TagShape = RoundedCornerShape(4.dp)

// ------------------------------------------------------------------ surfaces

/** Obsidian backdrop: a cold glow from above, a faint HUD dot grid that fades out downwards. Static: drawn once per size. */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val step = 22.dp.toPx()
            val pts = ArrayList<Offset>()
            var y = step / 2
            while (y < size.height * 0.55f) {
                var x = step / 2
                while (x < size.width) { pts.add(Offset(x, y)); x += step }
                y += step
            }
            val top = Brush.radialGradient(listOf(p.glowTop, Color.Transparent), Offset(size.width * 0.5f, -size.width * 0.15f), size.width * 1.15f)
            val side = Brush.radialGradient(listOf(p.glowSide, Color.Transparent), Offset(size.width * 1.1f, size.height * 0.7f), size.width * 0.9f)
            val fade = Brush.verticalGradient(listOf(Color.Transparent, p.bg), 0f, size.height * 0.55f)
            onDrawBehind {
                drawRect(p.bg)
                drawPoints(pts, PointMode.Points, p.dots, 1.6.dp.toPx(), StrokeCap.Round)
                drawRect(fade, size = Size(size.width, size.height * 0.56f))
                drawRect(top)
                drawRect(side)
            }
        },
    )
}

/**
 * A "system window" panel: dark glass, hairline border, and bright corner brackets.
 * [accent] tints the brackets; pass [glow] for a coloured outer light on key panels.
 */
@Composable
fun Modifier.panel(shape: Shape = PanelShape, accent: Color = Mana, brackets: Boolean = true, glow: Boolean = false, fill: Boolean = true): Modifier {
    val p = LocalPalette.current
    return this
        .then(if (glow) Modifier.shadow(24.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
        .clip(shape)
        .then(if (fill) Modifier.background(Brush.verticalGradient(listOf(p.surfaceHi, p.surface))) else Modifier)
        .border(1.dp, if (glow) accent.copy(alpha = 0.45f) else p.line, shape)
        .then(
            if (!brackets) Modifier else Modifier.drawWithContent {
                drawContent()
                val l = 12.dp.toPx()
                val s = 1.5.dp.toPx()
                val i = 0.5.dp.toPx()
                val c = accent.copy(alpha = 0.9f)
                val w = size.width
                val h = size.height
                drawLine(c, Offset(i, i), Offset(l, i), s)
                drawLine(c, Offset(i, i), Offset(i, l), s)
                drawLine(c, Offset(w - i, h - i), Offset(w - l, h - i), s)
                drawLine(c, Offset(w - i, h - i), Offset(w - i, h - l), s)
            },
        )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = Mana,
    glow: Boolean = false,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.panel(accent = accent, glow = glow).padding(padding), content = content)
}

/** Click with a short, firm press-scale instead of a ripple. */
@Composable
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Shimmering placeholder block. */
@Composable
fun Modifier.skeleton(shape: Shape = TagShape): Modifier {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "skx")
    return this.clip(shape).background(p.surfaceHi).drawBehind {
        val cx = size.width * x
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, p.shine, Color.Transparent), cx - size.width * 0.35f, cx + size.width * 0.35f))
    }
}

// ------------------------------------------------------------------ text

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = LocalPalette.current.muted, fontSize = size.sp, lineHeight = (size * 1.5).sp, maxLines = maxLines)

/** Small uppercase section label with an accent tick. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Mana) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(12.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = LocalPalette.current.muted, fontSize = 12.sp, letterSpacing = 2.4.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = display())
    }
}

/** Bracketed system tag: [ TEXT ]. */
@Composable
fun SystemTag(text: String, color: Color = Mana) {
    Row(
        Modifier.clip(TagShape).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.35f), TagShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = color, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, fontFamily = display())
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Ico.ArrowRight,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(200), label = "btn")
    val t = rememberInfiniteTransition(label = "btnShine")
    val sx by t.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sx")
    Box(
        modifier.fillMaxWidth().height(58.dp).graphicsLayer { this.alpha = alpha }
            .then(if (enabled) Modifier.shadow(20.dp, BladeShape, ambientColor = Mana, spotColor = Mana) else Modifier)
            .clip(BladeShape).background(ManaH)
            .drawBehind {
                // a slow highlight sweeping across the blade
                val x = size.width * sx
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (loading) 0.35f else 0.16f), Color.Transparent),
                    Offset(x - 160f, 0f), Offset(x + 160f, size.height)))
                drawLine(Color.White.copy(alpha = 0.35f), Offset(14.dp.toPx(), 1f), Offset(size.width, 1f), 1.dp.toPx())
            }
            .press(enabled && !loading, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(text.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.8.sp,
                fontFamily = display(), maxLines = 1, textAlign = TextAlign.Center)
            if (loading) {
                Spacer(Modifier.width(12.dp))
                Spinner(18.dp, Color.White)
            } else if (icon != null) {
                Spacer(Modifier.width(10.dp))
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Outlined secondary action. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color? = null) {
    val p = LocalPalette.current
    val c = color ?: p.text
    Row(
        modifier.height(52.dp).clip(BladeShape).background(c.copy(alpha = 0.06f)).border(1.dp, c.copy(alpha = 0.28f), BladeShape)
            .press(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), color = c, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.6.sp, fontFamily = display(), maxLines = 1)
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) Mana.copy(alpha = 0.16f) else p.surfaceHi, tween(180), label = "chipBg")
    val stroke by animateColorAsState(if (selected) Mana.copy(alpha = 0.7f) else p.line, tween(180), label = "chipLine")
    val fg = if (selected) (if (p.dark) ManaGlow else ManaDeep) else p.text
    Row(
        modifier.height(38.dp).clip(SmallBlade).background(bg).border(1.dp, stroke, SmallBlade)
            .press(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text.uppercase(), color = fg, fontSize = 13.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, fontFamily = display(), maxLines = 1)
    }
}

/** Square-ish icon button. */
@Composable
fun IconTile(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val p = LocalPalette.current
    Box(modifier.size(44.dp).panel(brackets = false).press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint ?: p.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Segmented(options: List<String>, index: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BoxWithConstraints(modifier.fillMaxWidth().height(46.dp).clip(PanelShape).background(p.surfaceHi).border(1.dp, p.line, PanelShape).padding(3.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().clip(TagShape).background(Mana.copy(alpha = 0.16f))
                .border(1.dp, Mana.copy(alpha = 0.65f), TagShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val c by animateColorAsState(if (i == index) (if (p.dark) ManaGlow else ManaDeep) else p.muted, tween(180), label = "segc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.uppercase(), color = c, fontSize = 13.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, fontFamily = display(), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 700f), label = "tg")
    val track by animateColorAsState(if (checked) Mana.copy(alpha = 0.22f) else p.surfaceHi, tween(180), label = "tgBg")
    val line by animateColorAsState(if (checked) Mana else p.lineHi.copy(alpha = 0.25f), tween(180), label = "tgLine")
    Box(
        Modifier.width(50.dp).height(28.dp).clip(TagShape).background(track).border(1.dp, line, TagShape)
            .clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }.padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).clip(RoundedCornerShape(3.dp)).background(if (checked) ManaH else SolidColor(p.faint)))
    }
}

@Composable
fun CheckMark(checked: Boolean) {
    val p = LocalPalette.current
    val scale by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 800f), label = "chk")
    Box(Modifier.size(22.dp).clip(TagShape).border(1.5.dp, if (checked) Mana else p.faint, TagShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(ManaV), contentAlignment = Alignment.Center) {
            Icon(Ico.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun Spinner(size: Dp, color: Color = Mana) {
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(size).rotate(r)) {
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, color)), 0f, 300f, false, style = Stroke(size.toPx() * 0.13f, cap = StrokeCap.Round))
    }
}

/**
 * Segmented energy gauge. Filled cells glow, the leading cell pulses while [active].
 */
@Composable
fun EnergyBar(progress: Float, modifier: Modifier = Modifier, cells: Int = 28, active: Boolean = true, color: Color = Mana) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "energy")
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "pl")
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val gap = 3.dp.toPx()
        val cw = (size.width - gap * (cells - 1)) / cells
        val filled = anim * cells
        for (i in 0 until cells) {
            val x = i * (cw + gap)
            val frac = (filled - i).coerceIn(0f, 1f)
            drawRect(p.faint.copy(alpha = 0.28f), Offset(x, 0f), Size(cw, size.height))
            if (frac > 0f) {
                val c = if (frac < 1f && active) color.copy(alpha = 0.35f + 0.65f * pulse) else color
                drawRect(c, Offset(x, 0f), Size(cw * frac, size.height))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(x, 0f), Size(cw * frac, 1.5.dp.toPx()))
            }
        }
    }
}

/** Thin progress line. */
@Composable
fun LineBar(progress: Float, modifier: Modifier = Modifier, brush: Brush = ManaH) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "line")
    Box(modifier.fillMaxWidth().height(3.dp).background(p.faint.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().background(brush))
    }
}

// ------------------------------------------------------------------ brand

/** Faceted crystal mark with a downward blade. */
@Composable
fun LogoMark(size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, 0f); lineTo(w, w * 0.5f); lineTo(w * 0.5f, w); lineTo(0f, w * 0.5f); close()
        }
        drawPath(path, ManaV)
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
        val s = Stroke(w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arrow = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, w * 0.27f); lineTo(w * 0.5f, w * 0.66f)
            moveTo(w * 0.36f, w * 0.52f); lineTo(w * 0.5f, w * 0.66f); lineTo(w * 0.64f, w * 0.52f)
        }
        drawPath(arrow, Color.White, style = s)
    }
}

@Composable
fun Wordmark() {
    // The brand always reads left-to-right, also in Arabic.
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { WordmarkRow() }
}

@Composable
private fun WordmarkRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark()
        Spacer(Modifier.width(10.dp))
        Text("MANGA", fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.5.sp, fontFamily = Rajdhani)
        Text("VAULT", fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 2.5.sp, fontFamily = Rajdhani, color = Mana)
    }
}

// ------------------------------------------------------------------ icons (stroke, 24 grid)

private fun strokeIcon(name: String, mirror: Boolean = false, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror)
    for (d in paths) {
        b.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object Ico {
    val Download = strokeIcon("dl", false, "M21 15v4a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-4", "M7 10l5 5l5 -5", "M12 15V3")
    val Link = strokeIcon("link", false,
        "M10 13a5 5 0 0 0 7.54 0.54l3 -3a5 5 0 0 0 -7.07 -7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0 -7.54 -0.54l-3 3a5 5 0 0 0 7.07 7.07l1.71 -1.71")
    val Paste = strokeIcon("paste", false,
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2h2",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1H9a1 1 0 0 1 -1 -1V3a1 1 0 0 1 1 -1z")
    val Globe = strokeIcon("globe", false, "M22 12a10 10 0 1 1 -20 0a10 10 0 1 1 20 0z", "M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0 -20", "M2 12h20")
    val Home = strokeIcon("home", false, "M3 10l9 -7l9 7v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z", "M9 22V13h6v9")
    val Sliders = strokeIcon("sliders", false, "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4")
    val Search = strokeIcon("search", false, "M19 11a8 8 0 1 1 -16 0a8 8 0 1 1 16 0z", "M21 21l-4.3 -4.3")
    val ArrowLeft = strokeIcon("back", true, "M19 12H5", "M12 19l-7 -7l7 -7")
    val ArrowRight = strokeIcon("fwd", true, "M5 12h14", "M12 5l7 7l-7 7")
    val Close = strokeIcon("close", false, "M18 6L6 18", "M6 6l12 12")
    val Check = strokeIcon("check", false, "M20 6L9 17l-5 -5")
    val Alert = strokeIcon("alert", false,
        "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71 -3L13.71 3.86a2 2 0 0 0 -3.42 0z", "M12 9v4", "M12 17h0.01")
    val Retry = strokeIcon("retry", false, "M21 12a9 9 0 1 1 -9 -9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")
    val Folder = strokeIcon("folder", false,
        "M4 20h16a2 2 0 0 0 2 -2V8a2 2 0 0 0 -2 -2h-7.93a2 2 0 0 1 -1.66 -0.9l-0.82 -1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0 -2 2v13a2 2 0 0 0 2 2z")
    val Archive = strokeIcon("file", false, "M14 2H6a2 2 0 0 0 -2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2V8z", "M14 2v6h6", "M10 12h4", "M10 16h4")
    val Trash = strokeIcon("trash", false, "M3 6h18", "M19 6v14a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V6", "M8 6V4a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2")
    val Zap = strokeIcon("zap", false, "M13 2L3 14h9l-1 8l10 -12h-9l1 -8z")
    val Clock = strokeIcon("clock", false, "M22 12a10 10 0 1 1 -20 0a10 10 0 1 1 20 0z", "M12 6v6l4 2")
    val Layers = strokeIcon("layers", false, "M12 2L2 7l10 5l10 -5l-10 -5z", "M2 17l10 5l10 -5", "M2 12l10 5l10 -5")
    val Book = strokeIcon("book", false, "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0 -3 -3H2z", "M22 3h-6a4 4 0 0 0 -4 4v14a3 3 0 0 1 3 -3h7z")
    val Shield = strokeIcon("shield", false, "M12 22s8 -4 8 -10V5l-8 -3l-8 3v7c0 6 8 10 8 10z")
    val Activity = strokeIcon("activity", false, "M22 12h-4l-3 9L9 3l-3 9H2")
    val Languages = strokeIcon("lang", false, "M5 8l6 6", "M4 14l6 -6l2 -3", "M2 5h12", "M7 2h1", "M22 22l-5 -10l-5 10", "M14 18h6")
    val Moon = strokeIcon("moon", false, "M12 3a6 6 0 0 0 9 9a9 9 0 1 1 -9 -9z")
    val Shrink = strokeIcon("shrink", false, "M15 15l6 6", "M15 19.8V15h4.8", "M9 19.8V15H4.2", "M9 15l-6 6", "M15 4.2V9h4.8", "M15 9l6 -6", "M9 4.2V9H4.2", "M9 9L3 3")
    val Upload = strokeIcon("upload", false, "M21 15v4a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2v-4", "M17 8l-5 -5l-5 5", "M12 3v12")
    val Share = strokeIcon("share", false, "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-8", "M16 6l-4 -4l-4 4", "M12 2v13")
    val Database = strokeIcon("db", false, "M21 5c0 1.66 -4 3 -9 3S3 6.66 3 5s4 -3 9 -3s9 1.34 9 3z", "M3 5v14c0 1.66 4 3 9 3s9 -1.34 9 -3V5", "M3 12c0 1.66 4 3 9 3s9 -1.34 9 -3")
}

package app.mangavault.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.Font
import app.mangavault.R

val Yellow = Color(0xFFFFC61A)
val Orange = Color(0xFFFF7A1A)
val Red = Color(0xFFE5322D)
val BrandBrush = Brush.horizontalGradient(listOf(Red, Orange))

private val DarkScheme = darkColorScheme(
    background = Color(0xFF0A0A0C), surface = Color(0xFF131317), surfaceVariant = Color(0xFF1B1B20),
    onBackground = Color(0xFFF2EFE8), onSurface = Color(0xFFF2EFE8), onSurfaceVariant = Color(0xFF9A9AA3),
    primary = Orange, onPrimary = Color.White, secondary = Yellow, outline = Color(0xFF2E2E35), error = Color(0xFFFF6B6B),
)
private val LightScheme = lightColorScheme(
    background = Color(0xFFFAF6EC), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF1ECDF),
    onBackground = Color(0xFF15151A), onSurface = Color(0xFF15151A), onSurfaceVariant = Color(0xFF5E5E68),
    primary = Orange, onPrimary = Color.White, secondary = Color(0xFFB88900), outline = Color(0xFFD8D2C2), error = Color(0xFFC0392B),
)

val Display = FontFamily(Font(R.font.archivo_black))
val DisplayAr = FontFamily(Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))
val Mono = FontFamily(Font(R.font.space_mono), Font(R.font.space_mono_bold, FontWeight.Bold))
val BodyAr = FontFamily(Font(R.font.tajawal_medium, FontWeight.Medium))

/** Heavy display face for headings; Arabic gets Tajawal since Archivo has no Arabic glyphs. */
@Composable
fun displayFont(): FontFamily = if (LocalLang.current == "ar") DisplayAr else Display

@Composable
fun MangaVaultTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

/** Dotted-grid backdrop like the reference. */
fun Modifier.dotted(): Modifier = drawBehind {
    val step = 22.dp.toPx()
    val pts = ArrayList<Offset>()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) { pts.add(Offset(x, y)); x += step }
        y += step
    }
    drawPoints(pts, PointMode.Points, Color.Gray.copy(alpha = 0.16f), 2.2.dp.toPx(), StrokeCap.Round)
}

@Composable
fun BoxCard(modifier: Modifier = Modifier, border: Color = MaterialTheme.colorScheme.outline, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, border, RoundedCornerShape(10.dp)),
        content = content,
    )
}

@Composable
fun BrandButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true, search: Boolean = false) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp))
            .background(if (enabled) BrandBrush else Brush.horizontalGradient(listOf(Color(0xFF55555C), Color(0xFF55555C))))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                Box(Modifier.size(10.dp))
            } else if (search) {
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Box(Modifier.size(10.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit, selected: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Yellow else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) Yellow else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Yellow, Orange, Red)))
                .drawBehind {
                    val w = size.width
                    val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
                    drawRoundRect(Color.White, Offset(w * 0.26f, w * 0.2f), androidx.compose.ui.geometry.Size(w * 0.48f, w * 0.6f),
                        androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = s)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.34f), Offset(w * 0.5f, w * 0.62f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.64f), Offset(w * 0.4f, w * 0.52f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.5f, w * 0.64f), Offset(w * 0.6f, w * 0.52f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                },
        )
        Box(Modifier.size(10.dp))
        Text("MangaVault", color = Yellow, fontWeight = FontWeight.Black, fontSize = 22.sp, fontFamily = FontFamily.SansSerif)
    }
}

/** Chain-link glyph drawn in code (no icon pack needed). */
@Composable
fun LinkGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        rotate(-45f) {
            val stroke = Stroke(width = w * 0.1f)
            drawRoundRect(color, Offset(w * 0.02f, h * 0.33f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.34f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.17f), style = stroke)
            drawRoundRect(color, Offset(w * 0.42f, h * 0.33f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.34f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.17f), style = stroke)
        }
    }
}

/** Small monospace label in a dark tab, like "SECURITY CHECK" / "01 · PASTE". */
@Composable
fun Tag(text: String, bg: Color = MaterialTheme.colorScheme.surfaceVariant, fg: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, color = fg)
    }
}

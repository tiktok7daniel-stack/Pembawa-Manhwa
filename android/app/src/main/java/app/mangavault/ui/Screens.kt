package app.mangavault.ui

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.download.CState
import app.mangavault.download.DownloadState
import app.mangavault.download.JobStatus
import app.mangavault.download.Mode
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val gradSpan = SpanStyle(brush = AccentH, fontWeight = FontWeight.SemiBold)

@Composable
private fun TopRow(title: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(46.dp).glass(CircleShape).bouncyClick(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = LocalPalette.current.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        if (title != null) Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else Spacer(Modifier.weight(1f))
        trailing()
    }
}

// ================================================================ Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val lang = LocalLang.current
    val theme by Prefs.theme.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp)) {
        item {
            Reveal(0) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo()
                    Spacer(Modifier.weight(1f))
                    GlassChip(if (lang == "ar") "EN" else "ع", { Prefs.setLang(if (lang == "ar") "en" else "ar") })
                    Spacer(Modifier.width(8.dp))
                    ThemeSwitch(theme != "light") { Prefs.setTheme(if (it) "dark" else "light") }
                }
            }
        }
        item {
            Reveal(1) {
                Column(Modifier.padding(top = 34.dp, bottom = 26.dp)) {
                    Muted(t("welcome"), size = 15)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(gradSpan) { append(t("h_a")) }
                            append(t("h_b"))
                            withStyle(gradSpan) { append(t("h_c")) }
                            append(t("h_d"))
                        },
                        fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.height(14.dp))
                    Muted(t("lead"), size = 15)
                }
            }
        }
        item {
            Reveal(2) {
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth().glass(CircleShape).padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkGlyph(p.muted)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).padding(vertical = 18.dp)) {
                            if (vm.url.isEmpty()) Muted(t("ph_url"), size = 15)
                            BasicTextField(
                                vm.url, { vm.url = it }, singleLine = true,
                                textStyle = TextStyle(color = p.text, fontSize = 15.sp, textDirection = TextDirection.Ltr),
                                cursorBrush = SolidColor(Pink),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { vm.parse() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        GlassChip(t("paste"), {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                            if (!txt.isNullOrBlank()) vm.url = Regex("https?://\\S+").find(txt)?.value ?: txt.trim()
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(AccentD), contentAlignment = Alignment.Center) {
                            Text("✦", color = Color.White, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("browser"), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Muted(t("browser_d"), size = 12)
                        }
                        GlassToggle(vm.browserMode) { vm.browserMode = it }
                    }
                    Spacer(Modifier.height(14.dp))
                    GradientButton(t("cta"), { vm.parse() }, loading = vm.loading, enabled = vm.url.isNotBlank())
                }
            }
        }
        item {
            vm.errorKey?.let { key ->
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Danger.copy(alpha = 0.12f))
                        .border(1.dp, Danger.copy(alpha = 0.45f), RoundedCornerShape(22.dp)).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Danger), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(t(key) + if (vm.errorDetail.isNotEmpty()) " (${vm.errorDetail})" else "", fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    if (vm.verifyUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        GlassChip(t("verify_btn"), { vm.showVerify = true }, selected = true)
                    }
                }
            }
        }
        item {
            Reveal(3) {
                Column(Modifier.padding(top = 30.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("how"), Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                        Muted(t("swipe"), size = 13)
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        item {
            Reveal(4) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(listOf("s1", "s2", "s3")) { k ->
                        GlassCard(Modifier.width(230.dp).height(190.dp)) {
                            Box(Modifier.size(44.dp).shadow(14.dp, CircleShape, spotColor = Pink).clip(CircleShape).background(AccentD), contentAlignment = Alignment.Center) {
                                Text(k.drop(1), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(t(k + "_t"), fontWeight = FontWeight.Medium, fontSize = 17.sp)
                            Spacer(Modifier.height(6.dp))
                            Muted(t(k + "_d"), size = 13)
                        }
                    }
                }
            }
        }
        item {
            Reveal(5) { Muted(t("disclaimer"), Modifier.fillMaxWidth().padding(top = 26.dp), 12) }
        }
    }
}

/** Sun/moon capsule switch. */
@Composable
private fun ThemeSwitch(dark: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (dark) 30.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "theme")
    Box(Modifier.width(66.dp).height(36.dp).glass(CircleShape).clickable { onChange(!dark) }.padding(3.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Text("☀", fontSize = 13.sp, color = LocalPalette.current.muted)
            Text("☾", fontSize = 13.sp, color = LocalPalette.current.muted)
        }
        Box(Modifier.padding(start = x).size(30.dp).shadow(8.dp, CircleShape, spotColor = Violet).clip(CircleShape).background(AccentD), contentAlignment = Alignment.Center) {
            Text(if (dark) "☾" else "☀", fontSize = 14.sp, color = Color.White)
        }
    }
}

// ================================================================ Result

@Composable
fun ResultScreen(vm: AppViewModel, onDownload: () -> Unit) {
    val s = vm.series ?: return
    val p = LocalPalette.current
    val word = t("chapter")
    var query by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val filtered = remember(s, query) { s.chapters.filter { query.isBlank() || chapterLabel(it, word).contains(query.trim(), true) } }

    Column(Modifier.fillMaxSize()) {
        TopRow(t("chapters_title"), { vm.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Reveal(0) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (s.cover != null) {
                                AsyncImage(
                                    s.cover, null,
                                    Modifier.width(104.dp).height(150.dp).shadow(18.dp, RoundedCornerShape(18.dp), spotColor = Violet).clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Label(s.sourceName)
                                Spacer(Modifier.height(4.dp))
                                Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (s.description.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(s.description, color = p.muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(buildAnnotatedString { withStyle(gradSpan) { append(s.chapters.size.toString()) } }, fontSize = 30.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Muted(t("chapters"), Modifier.padding(bottom = 6.dp), 13)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Reveal(1) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().glass(CircleShape).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            InlineField(query, { query = it }, t("search_ch"), Modifier.weight(1f).padding(vertical = 15.dp), KeyboardType.Text)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InlineField(from, { from = it }, t("from"), Modifier.width(84.dp).glass(CircleShape).padding(horizontal = 16.dp, vertical = 12.dp), KeyboardType.Decimal)
                            Muted("→")
                            InlineField(to, { to = it }, t("to"), Modifier.width(84.dp).glass(CircleShape).padding(horizontal = 16.dp, vertical = 12.dp), KeyboardType.Decimal)
                            GlassChip(t("range"), { vm.selectRange(from.toDoubleOrNull(), to.toDoubleOrNull()) })
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassChip(t("all"), { vm.selectAll() }, vm.selected.size == s.chapters.size)
                            GlassChip(t("first10"), { vm.selectFirst(10) })
                            GlassChip(t("last10"), { vm.selectLast(10) })
                            GlassChip(t("none"), { vm.selectNone() }, vm.selected.isEmpty())
                        }
                    }
                }
            }
            items(filtered, key = { it.id }) { c ->
                val on = c.id in vm.selected
                val tint by animateColorAsState(if (on) Pink.copy(alpha = 0.13f) else Color.Transparent, label = "row")
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).background(tint).clickable { vm.toggle(c.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckOrb(on)
                    Spacer(Modifier.width(14.dp))
                    Text(chapterLabel(c, word), Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(p.sheet)
                .border(1.dp, Brush.verticalGradient(listOf(p.stroke, Color.Transparent)), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(t("format"))
            Segmented(listOf(t("fmt_single"), t("fmt_zip")), if (vm.mode == Mode.SINGLE) 0 else 1, { vm.mode = if (it == 0) Mode.SINGLE else Mode.PER_CHAPTER })
            Muted(t(if (vm.mode == Mode.SINGLE) "fmt_single_d" else "fmt_zip_d"), size = 12)
            GradientButton("${t("download")} · ${vm.selected.size}", onDownload, enabled = vm.selected.isNotEmpty())
        }
    }
}

@Composable
private fun InlineField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, type: KeyboardType) {
    val p = LocalPalette.current
    Box(modifier) {
        if (value.isEmpty()) Muted(hint, size = 14)
        BasicTextField(
            value, onChange, singleLine = true, cursorBrush = SolidColor(Pink),
            textStyle = TextStyle(color = p.text, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ================================================================ Progress

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val job by DownloadState.job.collectAsState()
    val word = t("chapter")
    val j = job ?: return
    Column(Modifier.fillMaxSize()) {
        TopRow(j.title, { vm.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Reveal(0) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        RingProgress(j.percent / 100f, Modifier.size(250.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Muted(t("progress"), size = 13)
                                Text("${j.percent}%", fontSize = 48.sp, fontWeight = FontWeight.Light)
                                Muted("${j.doneChapters} / ${j.chapters.size} ${t("chapters")}", size = 12)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        val (status, color) = when (j.status) {
                            JobStatus.RUNNING -> t("st_running") to Peach
                            JobStatus.DONE -> t("st_done") to Mint
                            JobStatus.ERROR -> (t("st_error") + (j.error?.let { ": $it" } ?: "")) to Danger
                            JobStatus.CANCELLED -> t("st_cancelled") to p.muted
                        }
                        Row(Modifier.glass(CircleShape).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(status, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(18.dp))
                        BarChart(j.chapters.map { c ->
                            Triple(
                                c.chapter.number ?: "", c.state,
                                when (c.state) { CState.DONE, CState.PARTIAL, CState.FAILED -> 1f; else -> if (c.total > 0) c.done.toFloat() / c.total else 0f },
                            )
                        }, j.chapters.indexOfFirst { it.state == CState.ACTIVE })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            items(j.chapters, key = { it.chapter.id }) { c ->
                val frac = when (c.state) { CState.DONE, CState.PARTIAL, CState.FAILED -> 1f; else -> if (c.total > 0) c.done.toFloat() / c.total else 0f }
                val animFrac by androidx.compose.animation.core.animateFloatAsState(frac, tween(500), label = "row")
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chapterLabel(c.chapter, word), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(t("s_" + c.state.name.lowercase()) + if (c.state == CState.ACTIVE && c.total > 0) "  ${c.done}/${c.total}" else "", size = 12)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(p.faint.copy(alpha = 0.3f))) {
                        Box(
                            Modifier.fillMaxWidth(animFrac).fillMaxHeight().clip(CircleShape).background(
                                when (c.state) {
                                    CState.DONE -> Brush.horizontalGradient(listOf(Mint, Violet))
                                    CState.FAILED, CState.PARTIAL -> SolidColor(Danger)
                                    else -> AccentH
                                },
                            ),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (j.status) {
                JobStatus.RUNNING -> GlassChip(t("cancel"), { vm.cancelDownload() }, modifier = Modifier.fillMaxWidth())
                JobStatus.DONE -> {
                    j.savedPath?.let { Muted("${t("saved_in")}: $it", size = 12) }
                    GradientButton(t("open_dl"), { openDownloads(ctx) })
                }
                else -> Unit
            }
        }
    }
}

/** Glowing vertical bars for the chapters around the one being downloaded. */
@Composable
private fun BarChart(items: List<Triple<String, CState, Float>>, active: Int) {
    if (items.isEmpty()) return
    val p = LocalPalette.current
    val window = 12
    val start = (if (active < 0) items.size - window else active - window / 2).coerceIn(0, maxOf(0, items.size - window))
    val slice = items.drop(start).take(window)
    Row(Modifier.fillMaxWidth().height(150.dp).glass(RoundedCornerShape(26.dp)).padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        slice.forEach { (num, state, frac) ->
            val h by androidx.compose.animation.core.animateFloatAsState(0.08f + 0.92f * frac, tween(600, easing = FastOutSlowInEasing), label = "bar")
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val brush = when (state) {
                    CState.DONE -> Brush.verticalGradient(listOf(Mint, Violet))
                    CState.ACTIVE -> Brush.verticalGradient(listOf(Peach, Pink))
                    CState.FAILED, CState.PARTIAL -> SolidColor(Danger)
                    CState.QUEUED -> SolidColor(p.faint.copy(alpha = 0.4f))
                }
                Box(Modifier.weight(1f).width(8.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(h).shadow(if (state == CState.ACTIVE) 10.dp else 0.dp, CircleShape, spotColor = Pink).clip(CircleShape).background(brush))
                }
                Spacer(Modifier.height(6.dp))
                Text(num.take(4), fontSize = 9.sp, color = if (state == CState.ACTIVE) p.text else p.muted, maxLines = 1)
            }
        }
    }
}

fun openDownloads(ctx: Context) {
    try {
        ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Downloads/MangaVault", Toast.LENGTH_LONG).show()
    }
}

// ================================================================ Downloads & Settings

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val items by Prefs.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopRow(t("nav_downloads")) { if (items.isNotEmpty()) GlassChip(t("hist_clear"), { Prefs.clearHistory() }) }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(90.dp).shadow(30.dp, CircleShape, spotColor = Pink).clip(CircleShape).background(AccentD), contentAlignment = Alignment.Center) {
                    Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(20.dp))
                Muted(t("hist_empty"), size = 15)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.withIndex().toList(), key = { it.index }) { (i, h) -> Reveal(i.coerceAtMost(8)) { HistoryCard(h) { openDownloads(ctx) } } }
            }
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryItem, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).bouncyClick(onClick = onOpen).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(AccentD), contentAlignment = Alignment.Center) {
            Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted("${h.chapters} ${t("chapters")} · ${h.files} ${t("files")}", size = 12)
            Muted(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.time)), size = 11)
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val lang by Prefs.lang.collectAsState()
    val theme by Prefs.theme.collectAsState()
    val browser by Prefs.browserDefault.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopRow(t("nav_settings"))
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Reveal(0) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Label(t("set_lang"))
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf("English", "العربية"), if (lang == "ar") 1 else 0, { Prefs.setLang(if (it == 1) "ar" else "en") })
                        Spacer(Modifier.height(18.dp))
                        Label(t("set_theme"))
                        Spacer(Modifier.height(10.dp))
                        val themes = listOf("dark", "light", "system")
                        Segmented(listOf(t("theme_dark"), t("theme_light"), t("theme_system")), themes.indexOf(theme).coerceAtLeast(0), { Prefs.setTheme(themes[it]) })
                    }
                }
            }
            item {
                Reveal(1) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("set_browser"), fontWeight = FontWeight.Medium)
                                Muted(t("browser_d"), size = 12)
                            }
                            GlassToggle(browser) { Prefs.setBrowser(it) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("set_cache"), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            GlassChip(t("clear"), {
                                ctx.cacheDir.deleteRecursively()
                                Toast.makeText(ctx, Strings.get(lang, "cache_done"), Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
            }
            item { Reveal(2) { Muted(t("about"), size = 12) } }
            item { Reveal(3) { Muted("MangaVault v3.0", size = 11) } }
        }
    }
}

// ================================================================ Sheets

/** Bottom sheet shown while a link is analysed: orbiting rings + rotating status lines. */
@Composable
fun FindingSheet(url: String, onCancel: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(4000); if (step < 2) step++ }
    }
    SheetFrame(t("searching"), t("find_t"), onCancel) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Orbit() }
        androidx.compose.animation.AnimatedContent(step, label = "steps") { s ->
            Muted(t(listOf("find_1", "find_2", "find_3")[s]), Modifier.fillMaxWidth(), 14)
        }
        Spacer(Modifier.height(12.dp))
        UrlBox(url)
    }
}

@Composable
private fun Orbit() {
    val t = rememberInfiniteTransition(label = "orbit")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "a")
    val b by t.animateFloat(360f, 0f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "b")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Canvas(Modifier.size(130.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Pink.copy(alpha = 0.45f), Color.Transparent), c, r * pulse), r * pulse, c)
        androidx.compose.ui.graphics.drawscope.rotate(a, c) {
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, Violet, Pink), c), 0f, 260f, false,
                androidx.compose.ui.geometry.Offset(c.x - r * 0.8f, c.y - r * 0.8f), androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
                style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        }
        androidx.compose.ui.graphics.drawscope.rotate(b, c) {
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, Peach, Mint), c), 0f, 200f, false,
                androidx.compose.ui.geometry.Offset(c.x - r * 0.55f, c.y - r * 0.55f), androidx.compose.ui.geometry.Size(r * 1.1f, r * 1.1f),
                style = Stroke(r * 0.06f, cap = StrokeCap.Round))
        }
        drawCircle(Brush.linearGradient(listOf(Violet, Pink, Peach)), r * 0.2f * pulse, c)
    }
}

/** Visible browser where the user passes a site's human check themselves; cookies are shared with the downloader. */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifySheet(url: String, onDone: () -> Unit, onClose: () -> Unit) {
    SheetFrame(t("verify_tag"), t("verify_t"), onClose, fill = true) {
        Muted(t("verify_d"), size = 13)
        Spacer(Modifier.height(10.dp))
        AndroidView(
            factory = { c ->
                android.webkit.WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = app.mangavault.source.BROWSER_UA
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = android.webkit.WebViewClient()
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(t("verify_done"), {
            android.webkit.CookieManager.getInstance().flush()
            onDone()
        })
    }
}

@Composable
private fun UrlBox(url: String) {
    Row(Modifier.fillMaxWidth().glass(CircleShape).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(AccentH))
        Spacer(Modifier.width(12.dp))
        Text(url, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun SheetFrame(tag: String, title: String, onClose: () -> Unit, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(
            (if (fill) Modifier.fillMaxSize().padding(top = 48.dp) else Modifier.fillMaxWidth())
                .clip(shape).background(p.sheet).border(1.dp, Brush.verticalGradient(listOf(p.stroke, Color.Transparent)), shape)
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 22.dp).let { if (fill) it.weight(1f) else it }) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(CircleShape).background(p.faint))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(tag)
                        Spacer(Modifier.height(4.dp))
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(Modifier.size(44.dp).glass(CircleShape).bouncyClick(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, null, tint = p.text, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

package app.mangavault.ui

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.download.CState
import app.mangavault.download.ChapterProgress
import app.mangavault.download.DownloadState
import app.mangavault.download.Format
import app.mangavault.download.JobState
import app.mangavault.download.JobStatus
import app.mangavault.download.Mode
import app.mangavault.source.Series
import app.mangavault.source.Sources
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val manaSpan = SpanStyle(brush = ManaH)

@Composable
private fun TopBar(title: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconTile(Ico.ArrowLeft, onBack)
            Spacer(Modifier.width(12.dp))
        }
        if (title != null) {
            Text(title.uppercase(), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                fontFamily = display(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color = Mana, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(SmallBlade).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.35f), SmallBlade),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.48f).dp)) }
}

// ================================================================ Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val lang = LocalLang.current
    val history by Prefs.history.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                Chip(if (lang == "ar") "EN" else "عربي", { Prefs.setLang(if (lang == "ar") "en" else "ar") }, icon = Ico.Languages)
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 18.dp)) {
                SystemTag(t("tag"))
                Spacer(Modifier.height(12.dp))
                Text(
                    buildAnnotatedString {
                        append(t("hero_1"))
                        append("\n")
                        withStyle(manaSpan) { append(t("hero_2")) }
                    },
                    fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = if (lang == "ar") 34.sp else 40.sp, lineHeight = if (lang == "ar") 46.sp else 42.sp, letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                Muted(t("lead"), size = 14)
            }
        }
        item { LinkPanel(vm, ctx) }
        item {
            AnimatedVisibility(vm.errorKey != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val key = vm.errorKey ?: ""
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(PanelShape).background(Danger.copy(alpha = 0.08f))
                        .border(1.dp, Danger.copy(alpha = 0.45f), PanelShape).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Ico.Alert, null, tint = Danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(key) + if (vm.errorDetail.isNotEmpty()) " (${vm.errorDetail})" else "", fontSize = 14.sp, lineHeight = 21.sp)
                        if (vm.verifyUrl != null) {
                            Spacer(Modifier.height(12.dp))
                            Chip(t("verify_btn"), { vm.showVerify = true }, selected = true, icon = Ico.Shield)
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(t("recent"), Modifier.weight(1f))
                    Text(t("see_all").uppercase(), Modifier.clickable { vm.tab = Tab.DOWNLOADS }.padding(6.dp), color = Mana, fontSize = 12.sp,
                        letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, fontFamily = display())
                }
            }
            items(history.take(3), key = { "h" + it.time }) { h ->
                HistoryRow(h) { openDownloads(ctx) }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            item {
                SectionLabel(t("how"), Modifier.padding(top = 30.dp, bottom = 12.dp))
                Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                    listOf("s1", "s2", "s3").forEachIndexed { i, k ->
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("0${i + 1}", color = Mana, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.width(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t(k + "_t").uppercase(), fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.2.sp)
                                Muted(t(k + "_d"), size = 13)
                            }
                        }
                        if (i < 2) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(p.line))
                    }
                }
            }
        }
        item { Muted(t("disclaimer"), Modifier.fillMaxWidth().padding(top = 24.dp), 11) }
    }
}

@Composable
private fun LinkPanel(vm: AppViewModel, ctx: Context) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val fieldLine by animateColorAsState(if (focused) Mana else p.line, tween(180), label = "field")
    Panel(Modifier.fillMaxWidth(), glow = focused || vm.url.isNotBlank(), padding = 16.dp) {
        SectionLabel(t("link_label"))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(PanelShape).background(p.bg.copy(alpha = if (p.dark) 0.7f else 1f))
                .border(1.dp, fieldLine, PanelShape).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.Link, null, tint = if (focused) Mana else p.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (vm.url.isEmpty()) Muted(t("ph_url"), size = 14, maxLines = 1)
                BasicTextField(
                    vm.url, { vm.url = it }, singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp, textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(Mana),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.parse() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            if (vm.url.isNotEmpty()) {
                Box(Modifier.size(40.dp).clickable { vm.url = "" }, contentAlignment = Alignment.Center) {
                    Icon(Ico.Close, null, tint = p.muted, modifier = Modifier.size(16.dp))
                }
            } else {
                Chip(t("paste"), {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val txt = try { cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() } catch (_: Exception) { null }
                    if (!txt.isNullOrBlank()) vm.url = Regex("https?://\\S+").find(txt)?.value ?: txt.trim()
                }, icon = Ico.Paste)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(PanelShape).clickable { vm.browserMode = !vm.browserMode }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Ico.Globe, if (vm.browserMode) Mana else p.muted, 38)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("browser"), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Muted(t("browser_d"), size = 12)
            }
            Toggle(vm.browserMode) { vm.browserMode = it }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(t("cta"), { vm.parse() }, loading = vm.loading, enabled = vm.url.isNotBlank())
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
    var showRange by remember { mutableStateOf(false) }
    val filtered = remember(s, query) {
        val q = query.trim()
        if (q.isEmpty()) s.chapters else s.chapters.filter { chapterLabel(it, word).contains(q, true) }
    }
    val all = vm.selected.size == s.chapters.size

    Column(Modifier.fillMaxSize()) {
        TopBar(t("chapters_title"), { vm.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "hero") { SeriesHero(s) }
            item(key = "controls") {
                Column(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).clip(PanelShape).background(p.surface).border(1.dp, p.line, PanelShape).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.Search, null, tint = p.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        InlineField(query, { query = it }, t("search_ch"), Modifier.weight(1f), KeyboardType.Text)
                        if (query.isNotEmpty()) Icon(Ico.Close, null, tint = p.muted, modifier = Modifier.size(16.dp).clickable { query = "" })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t("all"), { vm.selectAll() }, all)
                        Chip(t("first10"), { vm.selectFirst(10) })
                        Chip(t("last10"), { vm.selectLast(10) })
                        Chip(t("range"), { showRange = !showRange }, showRange)
                        Chip(t("none"), { vm.selectNone() }, vm.selected.isEmpty())
                    }
                    AnimatedVisibility(showRange, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val box = Modifier.weight(1f).height(44.dp).clip(PanelShape).background(p.surface).border(1.dp, p.line, PanelShape)
                            Box(box.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { InlineField(from, { from = it }, t("from"), Modifier.fillMaxWidth(), KeyboardType.Decimal) }
                            Icon(Ico.ArrowRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
                            Box(box.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { InlineField(to, { to = it }, t("to"), Modifier.fillMaxWidth(), KeyboardType.Decimal) }
                            Chip(t("apply"), { vm.selectRange(from.toDoubleOrNull(), to.toDoubleOrNull()) }, selected = true)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(t("selected"), Modifier.weight(1f))
                        Text("${vm.selected.size}", color = Mana, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(" / ${s.chapters.size}", color = p.muted, fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
            items(filtered, key = { it.id }) { c -> ChapterRow(c.number, chapterLabel(c, word), c.id in vm.selected) { vm.toggle(c.id) } }
            if (filtered.isEmpty()) item(key = "empty") { Muted(t("no_match"), Modifier.fillMaxWidth().padding(24.dp), 13) }
        }
        Column(
            Modifier.fillMaxWidth().background(p.sheet)
                .drawWithContent { drawContent(); drawLine(Mana.copy(alpha = 0.5f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val formats = Format.values().toList()
            Segmented(formats.map { it.name }, formats.indexOf(vm.format), { vm.pickFormat(formats[it]) })
            Segmented(listOf(t("fmt_single"), t("fmt_zip")), if (vm.mode == Mode.SINGLE) 0 else 1, { vm.mode = if (it == 0) Mode.SINGLE else Mode.PER_CHAPTER })
            PrimaryButton(
                if (all) "${t("download_all")} · ${s.chapters.size}" else "${t("download")} · ${vm.selected.size}",
                onDownload, icon = Ico.Download, enabled = vm.selected.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun SeriesHero(s: Series) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val request = remember(s.cover) {
        s.cover?.let { ImageRequest.Builder(ctx).data(it).addHeader("Referer", Sources.referer(s)).crossfade(250).build() }
    }
    Box(Modifier.fillMaxWidth().clip(PanelShape)) {
        if (request != null) {
            AsyncImage(request, null, Modifier.matchParentSize().graphicsLayer { alpha = 0.22f }, contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(p.bg.copy(alpha = 0.2f), p.bg.copy(alpha = 0.92f)))))
        Row(Modifier.fillMaxWidth().panel(fill = false).padding(14.dp)) {
            Box(Modifier.width(104.dp).height(150.dp).shadow(18.dp, PanelShape, spotColor = Mana).skeleton(PanelShape).border(1.dp, Mana.copy(alpha = 0.5f), PanelShape)) {
                if (request != null) AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Ico.Book, null, tint = p.muted, modifier = Modifier.size(34.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (s.sourceName.isNotBlank()) SystemTag(s.sourceName)
                Spacer(Modifier.height(8.dp))
                Text(s.title, fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 26.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (s.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.description, color = p.muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(buildAnnotatedString { withStyle(manaSpan) { append(s.chapters.size.toString()) } },
                        fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(t("chapters").uppercase(), Modifier.padding(bottom = 5.dp), color = p.muted, fontFamily = display(),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(number: String?, label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (on) Mana.copy(alpha = if (p.dark) 0.09f else 0.07f) else p.surface, tween(160), label = "rowBg")
    val line by animateColorAsState(if (on) Mana.copy(alpha = 0.45f) else p.line, tween(160), label = "rowLine")
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(PanelShape).background(bg).border(1.dp, line, PanelShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (on) ManaV else SolidColor(Color.Transparent)))
        Text(
            number?.take(6) ?: "—", Modifier.width(64.dp).padding(start = 12.dp), color = if (on) Mana else p.muted,
            fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
        )
        Text(label, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        CheckMark(on)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun InlineField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, type: KeyboardType) {
    val p = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Muted(hint, size = 14, maxLines = 1)
        BasicTextField(
            value, onChange, singleLine = true, cursorBrush = SolidColor(Mana),
            textStyle = TextStyle(color = p.text, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ================================================================ Progress

private fun fmtBytes(b: Double): String = when {
    b >= 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
    b >= 1024.0 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
    b >= 1024.0 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> String.format(Locale.US, "%.0f B", b)
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private fun statusColor(s: JobStatus) = when (s) {
    JobStatus.RUNNING -> Mana
    JobStatus.DONE -> Success
    JobStatus.ERROR -> Danger
    JobStatus.CANCELLED -> Amber
}

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val job by DownloadState.job.collectAsState()
    val j = job ?: return
    val word = t("chapter")
    val running = j.status == JobStatus.RUNNING

    // Live transfer rate, sampled once a second and smoothed so the number reads calmly.
    var speed by remember { mutableDoubleStateOf(0.0) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var lastB = DownloadState.job.value?.bytes ?: 0L
        var lastT = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            val b = DownloadState.job.value?.bytes ?: 0L
            val tNow = SystemClock.elapsedRealtime()
            val inst = (b - lastB).coerceAtLeast(0) * 1000.0 / (tNow - lastT).coerceAtLeast(1)
            speed = if (speed == 0.0) inst else speed * 0.55 + inst * 0.45
            lastB = b; lastT = tNow; now = tNow
        }
    }
    val elapsed = (if (running) now else j.finishedAt.takeIf { it > 0 } ?: now) - j.startedAt
    val frac = j.fraction
    val eta = if (running && frac > 0.02f) ((elapsed / frac) * (1 - frac)).toLong() else -1L

    Column(Modifier.fillMaxSize()) {
        TopBar(j.title, { vm.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "core") { ProgressCore(j, running, if (running) speed else if (elapsed > 0) j.bytes * 1000.0 / elapsed else 0.0, eta, elapsed, word) }
            item(key = "label") { SectionLabel(t("chapters_title"), Modifier.padding(top = 16.dp, bottom = 6.dp)) }
            items(j.chapters, key = { it.chapter.id }) { c -> ProgressRow(c, word) }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val failed = j.chapters.count { it.state == CState.FAILED }
            when (j.status) {
                JobStatus.RUNNING -> GhostButton(t("cancel"), { vm.cancelDownload() }, Modifier.fillMaxWidth(), Ico.Close, Danger)
                JobStatus.DONE -> {
                    j.savedPath?.let { path ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ico.Folder, null, tint = LocalPalette.current.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Muted(path, size = 12, maxLines = 1)
                        }
                    }
                    PrimaryButton(t("open_dl"), { openDownloads(ctx) }, icon = Ico.Folder)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (failed > 0) GhostButton("${t("retry_failed")} · $failed", { vm.retryFailed() }, Modifier.weight(1f), Ico.Retry, Amber)
                        GhostButton(t("new_dl"), { vm.newDownload() }, Modifier.weight(1f), Ico.Link)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("retry"), { vm.retryFailed() }, Modifier.weight(1f), Ico.Retry, Mana)
                    GhostButton(t("new_dl"), { vm.newDownload() }, Modifier.weight(1f), Ico.Link)
                }
            }
        }
    }
}

@Composable
private fun ProgressCore(j: JobState, running: Boolean, speed: Double, eta: Long, elapsed: Long, word: String) {
    val p = LocalPalette.current
    val color = statusColor(j.status)
    Panel(Modifier.fillMaxWidth(), accent = color, glow = running, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SystemTag(
                    when (j.status) {
                        JobStatus.RUNNING -> t("st_running")
                        JobStatus.DONE -> t("st_done")
                        JobStatus.ERROR -> t("st_error")
                        JobStatus.CANCELLED -> t("st_cancelled")
                    },
                    color,
                )
            }
            if (running) Spinner(18.dp, color)
            else Icon(if (j.status == JobStatus.DONE) Ico.Check else Ico.Alert, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(j.percent, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) }, label = "pct") { pct ->
                Text(
                    buildAnnotatedString { withStyle(SpanStyle(brush = if (running) ManaH else SolidColor(color))) { append(pct.toString()) } },
                    fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 84.sp,
                )
            }
            Text("%", Modifier.padding(bottom = 14.dp, start = 4.dp), color = p.muted, fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(bottom = 12.dp), horizontalAlignment = Alignment.End) {
                Text("${j.doneChapters}/${j.chapters.size}", fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Text(t("chapters").uppercase(), color = p.muted, fontFamily = display(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        EnergyBar(j.fraction, active = running, color = color)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().clip(PanelShape).background(p.bg.copy(alpha = if (p.dark) 0.55f else 0.6f)).border(1.dp, p.line, PanelShape)) {
            Stat(Ico.Zap, t("speed"), if (speed > 0) "${fmtBytes(speed)}/s" else "—", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(58.dp).background(p.line))
            Stat(Ico.Layers, t("pages"), "${j.pagesDone}/${j.pagesTotal}", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(58.dp).background(p.line))
            if (running) Stat(Ico.Clock, t("eta"), if (eta >= 0) fmtTime(eta) else "—", Modifier.weight(1f))
            else Stat(Ico.Database, t("size"), fmtBytes(j.bytes.toDouble()), Modifier.weight(1f))
        }
        val active = j.chapters.filter { it.state == CState.ACTIVE }
        if (running && active.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("now").uppercase(), color = Mana, fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(Modifier.width(10.dp))
                Text(active.joinToString("  ·  ") { it.chapter.number?.let { n -> "$word $n" } ?: chapterLabel(it.chapter, word) },
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (j.status == JobStatus.ERROR && !j.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Muted(j.error, size = 12)
        }
        if (!running) {
            Spacer(Modifier.height(10.dp))
            Muted("${t("elapsed")}: ${fmtTime(elapsed)}", size = 12)
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mana, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label.uppercase(), color = p.muted, fontFamily = display(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.4.sp, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun ProgressRow(c: ChapterProgress, word: String) {
    val p = LocalPalette.current
    val color = when (c.state) {
        CState.DONE -> Success
        CState.ACTIVE -> Mana
        CState.PARTIAL -> Amber
        CState.FAILED -> Danger
        CState.QUEUED -> p.faint
    }
    val frac = when (c.state) {
        CState.DONE, CState.PARTIAL, CState.FAILED -> 1f
        else -> if (c.total > 0) c.done.toFloat() / c.total else 0f
    }
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(p.surface).border(1.dp, if (c.state == CState.ACTIVE) Mana.copy(alpha = 0.4f) else p.line, PanelShape)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                when (c.state) {
                    CState.ACTIVE -> Spinner(18.dp, Mana)
                    CState.DONE -> Icon(Ico.Check, null, tint = Success, modifier = Modifier.size(18.dp))
                    CState.PARTIAL, CState.FAILED -> Icon(Ico.Alert, null, tint = color, modifier = Modifier.size(17.dp))
                    CState.QUEUED -> Box(Modifier.size(7.dp).background(p.faint))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(chapterLabel(c.chapter, word), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (c.state == CState.QUEUED) p.muted else p.text)
            Spacer(Modifier.width(8.dp))
            val right = when (c.state) {
                CState.ACTIVE -> if (c.total > 0) "${c.done}/${c.total}" else "…"
                CState.PARTIAL -> "-${c.failedPages}"
                else -> t("s_" + c.state.name.lowercase())
            }
            Text(right.uppercase(), color = color, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        }
        if (c.state == CState.ACTIVE) LineBar(frac)
        if (c.state == CState.FAILED && !c.error.isNullOrBlank()) {
            Text(c.error, Modifier.padding(start = 48.dp, end = 12.dp, bottom = 10.dp), color = Danger.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 2,
                style = TextStyle(textDirection = TextDirection.Ltr))
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
        TopBar(t("nav_downloads")) { if (items.isNotEmpty()) Chip(t("hist_clear"), { Prefs.clearHistory() }, icon = Ico.Trash) }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(bottom = 120.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(84.dp).panel(glow = true), contentAlignment = Alignment.Center) {
                    Icon(Ico.Download, null, tint = Mana, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(t("hist_empty_t").uppercase(), fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Muted(t("hist_empty"), size = 13)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { "d" + it.time }) { h -> HistoryRow(h) { openDownloads(ctx) } }
            }
        }
    }
}

@Composable
private fun HistoryRow(h: HistoryItem, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().panel(brackets = false).press(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(if (h.files > 1) Ico.Folder else if (h.path.endsWith(".pdf", true)) Ico.Book else Ico.Archive, Mana, 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted("${h.chapters} ${t("chapters")} · ${h.files} ${t("files")} · " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.time)), size = 12, maxLines = 1)
        }
        Icon(Ico.ArrowRight, null, tint = LocalPalette.current.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val lang by Prefs.lang.collectAsState()
    val theme by Prefs.theme.collectAsState()
    val browser by Prefs.browserDefault.collectAsState()
    val speed by Prefs.speed.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("nav_settings"))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    SectionLabel(t("set_speed"))
                    Spacer(Modifier.height(10.dp))
                    val speeds = listOf("balanced", "fast", "turbo")
                    Segmented(listOf(t("sp_balanced"), t("sp_fast"), t("sp_turbo")), speeds.indexOf(speed).coerceAtLeast(0), { Prefs.setSpeed(speeds[it]) })
                    Spacer(Modifier.height(8.dp))
                    Muted(t("sp_${speed}_d"), size = 12)
                }
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    SectionLabel(t("set_lang"))
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf("English", "العربية"), if (lang == "ar") 1 else 0, { Prefs.setLang(if (it == 1) "ar" else "en") })
                    Spacer(Modifier.height(18.dp))
                    SectionLabel(t("set_theme"))
                    Spacer(Modifier.height(10.dp))
                    val themes = listOf("dark", "light", "system")
                    Segmented(listOf(t("theme_dark"), t("theme_light"), t("theme_system")), themes.indexOf(theme).coerceAtLeast(0), { Prefs.setTheme(themes[it]) })
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), padding = 6.dp) {
                    SettingRow(Ico.Globe, t("set_browser"), t("browser_d"), { Prefs.setBrowser(!browser) }) { Toggle(browser) { Prefs.setBrowser(it) } }
                    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
                    SettingRow(Ico.Trash, t("set_cache"), t("set_cache_d"), {
                        try { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) { }
                        Toast.makeText(ctx, Strings.get(lang, "cache_done"), Toast.LENGTH_SHORT).show()
                    }) { Icon(Ico.ArrowRight, null, tint = LocalPalette.current.muted, modifier = Modifier.size(16.dp)) }
                }
            }
            item { Muted(t("about"), Modifier.padding(horizontal = 4.dp), 12) }
            item {
                val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" } }
                Text("MANGAVAULT · v$version", Modifier.padding(horizontal = 4.dp), color = LocalPalette.current.faint, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PanelShape).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, Mana, 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Muted(desc, size = 12)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

// ================================================================ Sheets

/** Shown while a link is analysed: skeleton chapter rows under a sweeping scan line. */
@Composable
fun FindingSheet(url: String, onCancel: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3500); if (step < 2) step++ }
    }
    SheetFrame(t("searching"), t("find_t"), onCancel) {
        Scanner()
        Spacer(Modifier.height(14.dp))
        AnimatedContent(step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "steps") { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(14.dp)
                Spacer(Modifier.width(10.dp))
                Muted(t(listOf("find_1", "find_2", "find_3")[s]), size = 13)
            }
        }
        Spacer(Modifier.height(12.dp))
        UrlBox(url)
        Spacer(Modifier.height(14.dp))
        GhostButton(t("cancel"), onCancel, Modifier.fillMaxWidth(), Ico.Close)
    }
}

@Composable
private fun Scanner() {
    val p = LocalPalette.current
    val tr = rememberInfiniteTransition(label = "scan")
    val y by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "y")
    Column(
        Modifier.fillMaxWidth().panel().drawWithContent {
            drawContent()
            val yy = size.height * y
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Mana.copy(alpha = 0.16f)), yy - 60f, yy), Offset(0f, yy - 60f),
                androidx.compose.ui.geometry.Size(size.width, 60f))
            drawLine(ManaGlow.copy(alpha = 0.85f), Offset(0f, yy), Offset(size.width, yy), 1.5.dp.toPx())
        }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp).height(74.dp).skeleton(PanelShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(14.dp).skeleton())
                Box(Modifier.fillMaxWidth(0.5f).height(10.dp).skeleton())
                Box(Modifier.fillMaxWidth(0.3f).height(18.dp).skeleton())
            }
        }
        repeat(3) { i ->
            Row(Modifier.fillMaxWidth().height(34.dp).clip(PanelShape).background(p.surface).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(26.dp).height(12.dp).skeleton())
                Spacer(Modifier.width(12.dp))
                Box(Modifier.fillMaxWidth(0.55f - i * 0.1f).height(10.dp).skeleton())
            }
        }
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
            modifier = Modifier.fillMaxWidth().weight(1f).clip(PanelShape).border(1.dp, LocalPalette.current.line, PanelShape),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(t("verify_done"), {
            android.webkit.CookieManager.getInstance().flush()
            onDone()
        }, icon = Ico.Shield)
    }
}

@Composable
private fun UrlBox(url: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(PanelShape).background(p.surfaceHi).border(1.dp, p.line, PanelShape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.Link, null, tint = Mana, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(url, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun SheetFrame(tag: String, title: String, onClose: () -> Unit, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            (if (fill) Modifier.fillMaxSize().padding(top = 40.dp) else Modifier.fillMaxWidth())
                .background(p.sheet)
                .drawWithContent { drawContent(); drawLine(Mana, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 18.dp).let { if (fill) it.weight(1f) else it }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SystemTag(tag)
                        Spacer(Modifier.height(8.dp))
                        Text(title.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = display(), letterSpacing = 1.sp)
                    }
                    IconTile(Ico.Close, onClose)
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

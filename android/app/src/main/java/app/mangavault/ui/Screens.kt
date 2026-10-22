package app.mangavault.ui

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangavault.data.HistoryItem
import app.mangavault.data.Prefs
import app.mangavault.download.CState
import app.mangavault.download.DownloadState
import app.mangavault.download.JobStatus
import app.mangavault.download.Mode
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14) =
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = size.sp, lineHeight = (size * 1.5).sp)

@Composable
private fun Title(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val theme by Prefs.theme.collectAsState()
    val center = androidx.compose.ui.text.style.TextAlign.Center
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Logo()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(if (lang == "ar") "EN" else "ع", { Prefs.setLang(if (lang == "ar") "en" else "ar") })
                        Chip(if (theme == "light") "☾" else "☀", { Prefs.setTheme(if (theme == "light") "dark" else "light") })
                    }
                }
                Spacer(Modifier.height(30.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BoxCard {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).rotate(45f).background(Yellow))
                            Spacer(Modifier.width(12.dp))
                            Text(t("badge"), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(t("h1a"), Modifier.fillMaxWidth(), fontFamily = displayFont(), fontSize = 44.sp, lineHeight = 50.sp,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = center)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(brush = Brush.verticalGradient(listOf(Yellow, Orange)), shadow = Shadow(Red, Offset(7f, 7f), 0f))) { append(t("h1b")) }
                    },
                    Modifier.fillMaxWidth(), fontFamily = displayFont(), fontSize = 50.sp, lineHeight = 58.sp, textAlign = center,
                )
                Spacer(Modifier.height(16.dp))
                val hl = SpanStyle(color = Yellow, background = Yellow.copy(alpha = 0.14f), fontWeight = FontWeight.Bold)
                Text(
                    buildAnnotatedString {
                        append(t("lead_a")); withStyle(hl) { append(t("lead_hl1")) }
                        append(t("lead_b")); withStyle(hl) { append(t("lead_hl2")) }
                        append(t("lead_c"))
                    },
                    Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), fontSize = 18.sp, lineHeight = 28.sp, textAlign = center,
                )
                Spacer(Modifier.height(24.dp))

                BoxCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinkGlyph(MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.weight(1f).padding(vertical = 18.dp)) {
                                if (vm.url.isEmpty()) Muted(t("ph_url"), size = 16)
                                BasicTextField(
                                    vm.url, { vm.url = it }, singleLine = true,
                                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, textDirection = TextDirection.Ltr),
                                    cursorBrush = SolidColor(Orange),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { vm.parse() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Chip(t("paste"), {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                                if (!txt.isNullOrBlank()) vm.url = Regex("https?://\\S+").find(txt)?.value ?: txt.trim()
                            })
                        }
                        BrandButton(t("parse"), { vm.parse() }, loading = vm.loading, enabled = vm.url.isNotBlank(), search = true)
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.browserMode = !vm.browserMode },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(vm.browserMode, { vm.browserMode = it }, colors = CheckboxDefaults.colors(checkedColor = Orange))
                            Column {
                                Text(t("browser"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Muted(t("browser_d"), size = 12)
                            }
                        }
                    }
                }
                vm.errorKey?.let { key ->
                    Spacer(Modifier.height(12.dp))
                    BoxCard(Modifier.fillMaxWidth(), border = MaterialTheme.colorScheme.error) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                                    Text("✕", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(t(key) + if (vm.errorDetail.isNotEmpty()) " (${vm.errorDetail})" else "", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            if (vm.verifyUrl != null) BrandButton(t("verify_btn"), { vm.showVerify = true })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Muted(t("disclaimer"), Modifier.fillMaxWidth(), 13)
                Spacer(Modifier.height(20.dp))
                BoxCard(Modifier.fillMaxWidth(), border = Yellow.copy(alpha = 0.6f)) {
                    Column(Modifier.background(Yellow.copy(alpha = 0.07f)).padding(16.dp)) {
                        Tag(t("promo_t"), Yellow, androidx.compose.ui.graphics.Color.Black)
                        Spacer(Modifier.height(10.dp))
                        Text(t("promo_d"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("how"), fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t("swipe"), fontFamily = Mono, letterSpacing = 2.sp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 10.dp),
            ) {
                item { StepCard("01", t("s1_t"), -1.5f) { StepPaste() } }
                item { StepCard("02", t("s2_t"), 1.2f) { StepPick() } }
                item { StepCard("03", t("s3_t"), -1f) { StepRead() } }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun StepCard(no: String, label: String, tilt: Float, content: @Composable () -> Unit) {
    BoxCard(Modifier.width(268.dp).height(250.dp).rotate(tilt)) {
        Column {
            Tag("$no · $label")
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun StepPaste() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted(t("s1_d"), size = 13)
        Box(Modifier.fillMaxWidth().border(2.dp, Yellow).padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(t("demo_url"), fontFamily = Mono, fontSize = 12.sp, color = Yellow, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(5.dp)).background(BrandBrush), contentAlignment = Alignment.Center) {
            Text(t("parse"), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StepPick() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString { append("Ch. 1 "); withStyle(SpanStyle(color = Orange)) { append("→") }; append(" 10") },
            fontFamily = Display, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface,
        )
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            repeat(22) { i ->
                Box(Modifier.weight(1f).fillMaxSize().background(if (i < 10) Yellow else MaterialTheme.colorScheme.outline))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(t("first10"), Yellow, androidx.compose.ui.graphics.Color.Black)
            Tag(t("last10"))
        }
    }
}

@Composable
private fun StepRead() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CBZ", fontFamily = Display, fontSize = 44.sp, style = TextStyle(brush = Brush.verticalGradient(listOf(Yellow, Orange))))
        Text("✓ " + t("demo_saved"), color = androidx.compose.ui.graphics.Color(0xFF4ADE80), fontFamily = Mono, fontSize = 11.sp)
        Muted(t("s3_d"), size = 13)
    }
}

/** Bottom sheet shown while a link is being analysed. */
@Composable
fun FindingSheet(url: String, onCancel: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(4000); if (step < 2) step++ }
    }
    SheetFrame(t("searching"), t("find_t"), onCancel) {
        Muted(t(listOf("find_1", "find_2", "find_3")[step]), size = 15)
        Spacer(Modifier.height(14.dp))
        UrlBox(url)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Yellow, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
}

/** Visible browser where the user passes a site's human check themselves; cookies are shared with the downloader. */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifySheet(url: String, onDone: () -> Unit, onClose: () -> Unit) {
    SheetFrame(t("verify_tag"), t("verify_t"), onClose, fill = true) {
        Muted(t("verify_d"), size = 14)
        Spacer(Modifier.height(10.dp))
        UrlBox(url)
        Spacer(Modifier.height(10.dp))
        androidx.compose.ui.viewinterop.AndroidView(
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
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(12.dp))
        BrandButton(t("verify_done"), {
            android.webkit.CookieManager.getInstance().flush()
            onDone()
        })
    }
}

@Composable
private fun UrlBox(url: String) {
    Row(
        Modifier.fillMaxWidth().border(1.5.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Yellow))
        Spacer(Modifier.width(12.dp))
        Text(url, fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun SheetFrame(tag: String, title: String, onClose: () -> Unit, fill: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            (if (fill) Modifier.fillMaxSize().padding(top = 40.dp) else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(MaterialTheme.colorScheme.surface),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Red, Orange, Yellow))))
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 20.dp).let { if (fill) it.weight(1f) else it }) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Tag(tag)
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                }
                Spacer(Modifier.height(14.dp))
                Text(title, fontFamily = displayFont(), fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

// ---------------------------------------------------------------- Result

@Composable
fun ResultScreen(vm: AppViewModel, onDownload: () -> Unit) {
    val s = vm.series ?: return
    val word = t("chapter")
    var query by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val filtered = remember(s, query) { s.chapters.filter { query.isBlank() || chapterLabel(it, word).contains(query.trim(), true) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("←  " + t("back"), { vm.back() })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)) {
            item {
                BoxCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (s.cover != null) {
                            AsyncImage(s.cover, null, Modifier.width(96.dp).height(138.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.sourceName.uppercase(), color = Yellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 2.sp)
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (s.description.isNotBlank()) Muted(s.description, Modifier.padding(top = 4.dp), 13)
                            Text("${s.chapters.size} ${t("chapters")}", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SmallField(query, { query = it }, t("search_ch"), Modifier.fillMaxWidth(), KeyboardType.Text)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallField(from, { from = it }, "1", Modifier.width(64.dp), KeyboardType.Decimal)
                    Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SmallField(to, { to = it }, "∞", Modifier.width(64.dp), KeyboardType.Decimal)
                    Chip(t("range"), { vm.selectRange(from.toDoubleOrNull(), to.toDoubleOrNull()) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("all"), { vm.selectAll() })
                    Chip(t("first10"), { vm.selectFirst(10) })
                    Chip(t("last10"), { vm.selectLast(10) })
                    Chip(t("none"), { vm.selectNone() })
                }
                Spacer(Modifier.height(8.dp))
            }
            items(filtered, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.toggle(c.id) }
                        .background(if (c.id in vm.selected) Orange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (c.id in vm.selected) Orange.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(c.id in vm.selected, { vm.toggle(c.id) }, colors = CheckboxDefaults.colors(checkedColor = Orange))
                    Text(chapterLabel(c, word), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        BoxCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatRow(vm.mode == Mode.SINGLE, t("fmt_single"), t("fmt_single_d")) { vm.mode = Mode.SINGLE }
                FormatRow(vm.mode == Mode.PER_CHAPTER, t("fmt_zip"), t("fmt_zip_d")) { vm.mode = Mode.PER_CHAPTER }
                BrandButton("${t("download")} (${vm.selected.size})", onDownload, enabled = vm.selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun SmallField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, type: KeyboardType) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) Muted(hint, size = 14)
        BasicTextField(
            value, onChange, singleLine = true, cursorBrush = SolidColor(Orange),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FormatRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .border(1.dp, if (selected) Yellow else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick)
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Muted(desc, size = 12)
        }
    }
}

// ---------------------------------------------------------------- Progress

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val job by DownloadState.job.collectAsState()
    val word = t("chapter")
    val j = job
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("←  " + t("back"), { vm.back() })
        }
        if (j == null) return@Column
        BoxCard(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(14.dp)) {
                Text(j.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                val status = when (j.status) {
                    JobStatus.RUNNING -> "${t("st_running")} ${j.percent}% · ${j.doneChapters}/${j.chapters.size}"
                    JobStatus.DONE -> t("st_done")
                    JobStatus.ERROR -> t("st_error") + (j.error?.let { ": $it" } ?: "")
                    JobStatus.CANCELLED -> t("st_cancelled")
                }
                Text(status, color = if (j.status == JobStatus.ERROR) MaterialTheme.colorScheme.error else Yellow, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator({ j.percent / 100f }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = Orange, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(j.chapters, key = { it.chapter.id }) { c ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(chapterLabel(c.chapter, word), Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val label = t("s_" + c.state.name.lowercase()) + if (c.state == CState.ACTIVE && c.total > 0) " ${c.done}/${c.total}" else ""
                                Muted(label, size = 13)
                            }
                            val p = when (c.state) { CState.DONE, CState.PARTIAL, CState.FAILED -> 1f; else -> if (c.total > 0) c.done.toFloat() / c.total else 0f }
                            LinearProgressIndicator(
                                { p }, Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = when (c.state) { CState.DONE -> androidx.compose.ui.graphics.Color(0xFF4ADE80); CState.FAILED, CState.PARTIAL -> MaterialTheme.colorScheme.error; else -> Orange },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (j.status) {
                    JobStatus.RUNNING -> Chip(t("cancel"), { vm.cancelDownload() })
                    JobStatus.DONE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        j.savedPath?.let { Muted("${t("saved_in")}: $it", size = 13) }
                        BrandButton(t("open_dl"), { openDownloads(ctx) })
                    }
                    else -> Unit
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

fun openDownloads(ctx: Context) {
    try {
        ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Downloads/MangaVault", Toast.LENGTH_LONG).show()
    }
}

// ---------------------------------------------------------------- Downloads & Settings

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val items by Prefs.history.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title(t("nav_downloads"))
            if (items.isNotEmpty()) Chip(t("hist_clear"), { Prefs.clearHistory() })
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) Muted(t("hist_empty"), size = 15)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { h -> HistoryCard(h) { openDownloads(ctx) } }
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryItem, onOpen: () -> Unit) {
    BoxCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp)) {
            Text(h.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Muted("${h.chapters} ${t("chapters")} · ${h.files} ${t("files")} · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.time)), size = 13)
            Text(h.path, color = Yellow, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val lang by Prefs.lang.collectAsState()
    val theme by Prefs.theme.collectAsState()
    val browser by Prefs.browserDefault.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title(t("nav_settings"))
        BoxCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("set_lang"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("English", { Prefs.setLang("en") }, lang == "en")
                    Chip("العربية", { Prefs.setLang("ar") }, lang == "ar")
                }
                Text(t("set_theme"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("theme_dark"), { Prefs.setTheme("dark") }, theme == "dark")
                    Chip(t("theme_light"), { Prefs.setTheme("light") }, theme == "light")
                    Chip(t("theme_system"), { Prefs.setTheme("system") }, theme == "system")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("set_browser"), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(browser, { Prefs.setBrowser(it) })
                }
                Chip(t("set_cache"), {
                    ctx.cacheDir.deleteRecursively()
                    Toast.makeText(ctx, Strings.get(lang, "cache_done"), Toast.LENGTH_SHORT).show()
                })
            }
        }
        Muted(t("about"), size = 13)
        Muted("v2.0.0", size = 12)
    }
}

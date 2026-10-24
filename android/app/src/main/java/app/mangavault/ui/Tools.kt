package app.mangavault.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangavault.download.CompressJob
import app.mangavault.download.CompressState
import app.mangavault.download.FileKind
import app.mangavault.download.JobStatus
import app.mangavault.download.Level

/** Shrink an existing PDF / CBZ / ZIP: pick a file, choose how small, watch it shrink, open or share the result. */
@Composable
fun ToolsScreen(vm: AppViewModel, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val job by CompressState.job.collectAsState()
    val running = job?.status == JobStatus.RUNNING
    Column(Modifier.fillMaxSize()) {
        TopBar(t("nav_tools"))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    SystemTag(t("cmp_tag"))
                    Spacer(Modifier.height(12.dp))
                    Text(t("cmp_title"), fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp)
                    Spacer(Modifier.height(8.dp))
                    Muted(t("cmp_lead"), size = 13)
                }
            }
            item {
                val f = vm.picked
                if (f == null) {
                    Column(
                        Modifier.fillMaxWidth().height(150.dp).clip(PanelShape).background(p.surface)
                            .drawBehind {
                                drawRoundRect(Mana.copy(alpha = 0.55f), cornerRadius = CornerRadius(6.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                            }
                            .press(enabled = !running, onClick = onPick),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        IconBadge(Ico.Upload, Mana, 48)
                        Spacer(Modifier.height(12.dp))
                        Text(t("cmp_pick").uppercase(), fontFamily = display(), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
                        Muted("PDF · CBZ · ZIP", size = 12)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().panel().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (f.kind == FileKind.PDF) Ico.Book else Ico.Archive, Mana, 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                (if (f.kind == FileKind.PDF) "PDF" else f.name.substringAfterLast('.', "ZIP").uppercase()) + "  ·  " + fmtBytes(f.size.toDouble()),
                                color = Mana, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (!running) Chip(t("cmp_change"), onPick)
                    }
                }
                if (vm.pickError) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.Alert, null, tint = Danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("cmp_bad"), color = Danger, fontSize = 13.sp)
                    }
                }
            }
            if (vm.picked != null) {
                item { LevelPanel(vm, running) }
                item {
                    PrimaryButton(t("cmp_go"), { if (!vm.startCompress()) Toast.makeText(ctx, Strings.get(vm.langNow(), "cmp_need_target"), Toast.LENGTH_SHORT).show() },
                        icon = Ico.Shrink, loading = running, enabled = !running)
                }
            }
            job?.let { j -> item { CompressPanel(j, vm) } }
        }
    }
}

@Composable
private fun LevelPanel(vm: AppViewModel, running: Boolean) {
    val p = LocalPalette.current
    val levels = Level.values().toList()
    val kind = vm.picked?.kind
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel(t("cmp_codec"))
        Spacer(Modifier.height(10.dp))
        Segmented(
            if (kind == FileKind.PDF) listOf("PDF · JPEG", "CBZ · WebP") else listOf("JPEG", "WebP"),
            if (vm.webp) 1 else 0, { if (!running) vm.chooseWebp(it == 1) },
        )
        Spacer(Modifier.height(6.dp))
        Muted(t(if (vm.webp) (if (kind == FileKind.PDF) "codec_cbz_d" else "codec_webp_d") else "codec_jpeg_d"), size = 12)
        Spacer(Modifier.height(16.dp))
        SectionLabel(t("cmp_level"))
        Spacer(Modifier.height(10.dp))
        Segmented(listOf(t("lv_high"), t("lv_bal"), t("lv_small"), t("lv_target")), levels.indexOf(vm.level), { if (!running) vm.level = levels[it] })
        Spacer(Modifier.height(8.dp))
        Muted(t("lv_" + vm.level.name.lowercase() + "_d"), size = 12)
        Spacer(Modifier.height(12.dp))
        // Measured on sample pages of this very file, before anything is written.
        Row(Modifier.fillMaxWidth().clip(PanelShape).background(p.bg.copy(alpha = if (p.dark) 0.55f else 0.6f)).border(1.dp, p.line, PanelShape)) {
            val est = vm.estimate
            listOf(t("lv_high") to est?.high, t("lv_bal") to est?.balanced, t("lv_small") to est?.small).forEachIndexed { i, (label, v) ->
                if (i > 0) Box(Modifier.width(1.dp).height(52.dp).background(p.line))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Text(label.uppercase(), color = p.muted, fontFamily = display(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.2.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    when {
                        v != null -> Text("≈ " + fmtBytes(v.toDouble()), fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        vm.estimating -> Box(Modifier.fillMaxWidth(0.8f).height(14.dp).skeleton())
                        else -> Text("—", fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        AnimatedVisibility(vm.level == Level.TARGET, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.width(120.dp).height(44.dp).clip(PanelShape).background(p.bg.copy(alpha = if (p.dark) 0.7f else 1f))
                            .border(1.dp, Mana.copy(alpha = 0.6f), PanelShape).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            vm.targetMb, { v -> vm.targetMb = v.filter { it.isDigit() }.take(5) }, singleLine = true, cursorBrush = SolidColor(Mana),
                            textStyle = TextStyle(color = p.text, fontSize = 18.sp, fontFamily = Rajdhani, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        Text("MB", color = p.muted, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    listOf("20", "50", "100").forEach { mb -> Chip("$mb MB", { vm.targetMb = mb }, selected = vm.targetMb == mb) }
                }
                val target = (vm.targetMb.toLongOrNull() ?: 0) * 1048576L
                val est = vm.estimate
                if (target > 0 && est != null) {
                    // What this target really costs in quality, judged against the measured presets.
                    val g = est.grade(target)
                    val c = when (g) { 0, 1 -> Success; 2 -> Mana; 3 -> Amber; else -> Danger }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(PanelShape).background(c.copy(alpha = 0.10f)).border(1.dp, c.copy(alpha = 0.4f), PanelShape).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (g <= 2) Ico.Check else Ico.Alert, null, tint = c, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${t("grade")}: ${t("grade_$g")}", color = c, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Muted(t("grade_${g}_d"), size = 12)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressPanel(j: CompressJob, vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val running = j.status == JobStatus.RUNNING
    val color = if (j.noGain) Amber else statusColor(j.status)
    val pct = (j.fraction * 100).toInt()
    val saved = if (j.inBytes > 0 && j.outBytes > 0) (100 - j.outBytes * 100 / j.inBytes).toInt() else 0
    Panel(Modifier.fillMaxWidth(), accent = color, glow = running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SystemTag(
                    when {
                        running -> t("cmp_running")
                        j.status == JobStatus.DONE && j.noGain -> t("cmp_nogain_tag")
                        j.status == JobStatus.DONE -> t("st_done")
                        j.status == JobStatus.CANCELLED -> t("st_cancelled")
                        else -> t("st_error")
                    },
                    color,
                )
            }
            if (running) Spinner(18.dp, color)
        }
        Spacer(Modifier.height(12.dp))
        if (running) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(buildAnnotatedString { withStyle(SpanStyle(brush = ManaH)) { append(pct.toString()) } },
                    fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp)
                Text("%", Modifier.padding(bottom = 10.dp, start = 4.dp), color = p.muted, fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                Spacer(Modifier.weight(1f))
                if (j.total > 0) Text("${j.done}/${j.total}", Modifier.padding(bottom = 10.dp), fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            EnergyBar(j.fraction, active = true)
        } else if (j.status == JobStatus.DONE && !j.noGain) {
            // Before → after, the number that matters.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtBytes(j.inBytes.toDouble()), color = p.muted, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Ico.ArrowRight, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(buildAnnotatedString { withStyle(SpanStyle(brush = ManaH)) { append(fmtBytes(j.outBytes.toDouble())) } },
                    fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            }
            Text("-$saved%", color = Success, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(PanelShape).background(p.bg.copy(alpha = if (p.dark) 0.55f else 0.6f)).border(1.dp, p.line, PanelShape)) {
            CStat(t("cmp_before"), fmtBytes(j.inBytes.toDouble()), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(56.dp).background(p.line))
            CStat(t("cmp_now"), fmtBytes(j.outBytes.toDouble()), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(56.dp).background(p.line))
            if (running) CStat(t("cmp_expected"), if (j.projected > 0) "≈ " + fmtBytes(j.projected.toDouble()) else "—", Modifier.weight(1f))
            else CStat(t("cmp_saved"), if (saved > 0 && !j.noGain) "$saved%" else "—", Modifier.weight(1f))
        }
        if (j.noGain) {
            Spacer(Modifier.height(10.dp))
            Muted(t("cmp_nogain"), size = 12)
        }
        if (j.status == JobStatus.ERROR && !j.error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(j.error, color = Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        when {
            running -> GhostButton(t("cancel"), { vm.cancelCompress() }, Modifier.fillMaxWidth(), Ico.Close, Danger)
            j.status == JobStatus.DONE && !j.noGain -> {
                j.outPath?.let { path ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.Folder, null, tint = p.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Muted(path, size = 11, maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("cmp_open"), { openFile(ctx, j.outUri, j.mime) }, Modifier.weight(1f), Ico.Book, Mana)
                    GhostButton(t("cmp_share"), { shareFile(ctx, j.outUri, j.mime) }, Modifier.weight(1f), Ico.Share)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun CStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(label.uppercase(), color = LocalPalette.current.muted, fontFamily = display(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.4.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
    }
}

private fun openFile(ctx: Context, uri: String?, mime: String) {
    if (uri == null) { openDownloads(ctx); return }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        openDownloads(ctx)
    }
}

private fun shareFile(ctx: Context, uri: String?, mime: String) {
    if (uri == null) { openDownloads(ctx); return }
    try {
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Downloads/MangaVault/Compressed", Toast.LENGTH_LONG).show()
    }
}

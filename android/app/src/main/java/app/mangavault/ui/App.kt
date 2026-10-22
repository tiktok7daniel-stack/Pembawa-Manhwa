package app.mangavault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mangavault.data.Prefs

@Composable
fun MangaVaultApp(vm: AppViewModel, onDownload: () -> Unit) {
    val lang by Prefs.lang.collectAsState()
    val theme by Prefs.theme.collectAsState()
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    CompositionLocalProvider(
        LocalLang provides lang,
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MangaVaultTheme(dark) {
            Box(Modifier.fillMaxSize()) {
                Backdrop()
                BackHandler(enabled = vm.loading || vm.showVerify || vm.screen != Screen.MAIN || vm.tab != Tab.HOME) {
                    when {
                        vm.showVerify -> vm.showVerify = false
                        vm.loading -> vm.cancelParse()
                        vm.screen != Screen.MAIN -> vm.back()
                        else -> vm.tab = Tab.HOME
                    }
                }
                val key = if (vm.screen == Screen.MAIN) vm.tab.name else vm.screen.name
                AnimatedContent(
                    key,
                    Modifier.fillMaxSize().statusBarsPadding(),
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }).togetherWith(fadeOut(tween(120)))
                    },
                    label = "screens",
                ) { k ->
                    when (k) {
                        Screen.RESULT.name -> ResultScreen(vm, onDownload)
                        Screen.PROGRESS.name -> ProgressScreen(vm)
                        Tab.DOWNLOADS.name -> DownloadsScreen()
                        Tab.SETTINGS.name -> SettingsScreen()
                        else -> HomeScreen(vm)
                    }
                }
                AnimatedVisibility(
                    vm.screen == Screen.MAIN,
                    Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it },
                ) { Dock(vm) }

                AnimatedVisibility(vm.loading, enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 3 }, exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 }) {
                    FindingSheet(vm.url) { vm.cancelParse() }
                }
                val verify = vm.verifyUrl
                AnimatedVisibility(vm.showVerify && verify != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                    if (verify != null) VerifySheet(verify, { vm.verified() }, { vm.showVerify = false })
                }
            }
        }
    }
}

/** Bottom navigation: a sharp glass bar with a glowing indicator that slides between tabs. */
@Composable
private fun Dock(vm: AppViewModel) {
    val p = LocalPalette.current
    val tabs: List<Triple<Tab, ImageVector, String>> = listOf(
        Triple(Tab.HOME, Ico.Home, t("nav_home")),
        Triple(Tab.DOWNLOADS, Ico.Download, t("nav_downloads")),
        Triple(Tab.SETTINGS, Ico.Sliders, t("nav_settings")),
    )
    val index = tabs.indexOfFirst { it.first == vm.tab }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).shadow(28.dp, PanelShape, spotColor = Color.Black)
                .panel().background(p.sheet.copy(alpha = 0.6f)),
        ) {
            val w = maxWidth / tabs.size
            val x by animateDpAsState(w * index, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "dock")
            Box(
                Modifier.offset(x = x).width(w).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Mana.copy(alpha = 0.16f), Color.Transparent))),
            ) {
                Box(Modifier.align(Alignment.TopCenter).width(36.dp).height(2.dp).background(ManaH))
            }
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, (tab, icon, label) ->
                    val tint by animateColorAsState(if (i == index) (if (p.dark) ManaGlow else ManaDeep) else p.muted, tween(180), label = "tint")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.tab = tab },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label.uppercase(), color = tint, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, fontFamily = display(), maxLines = 1)
                    }
                }
            }
        }
    }
}

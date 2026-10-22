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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
                Aurora()
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
                        (fadeIn(tween(380)) + slideInVertically(tween(420)) { it / 14 } + scaleIn(tween(420), initialScale = 0.97f))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.02f))
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
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) { Dock(vm) }

                AnimatedVisibility(vm.loading, enter = fadeIn(tween(250)) + slideInVertically { it / 3 }, exit = fadeOut(tween(200)) + slideOutVertically { it / 3 }) {
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

/** Floating glass capsule with a sliding highlight pill. */
@Composable
private fun Dock(vm: AppViewModel) {
    val p = LocalPalette.current
    val tabs: List<Pair<Tab, ImageVector>> = listOf(Tab.HOME to Icons.Filled.Home, Tab.DOWNLOADS to DownloadIcon, Tab.SETTINGS to Icons.Filled.Settings)
    val index = tabs.indexOfFirst { it.first == vm.tab }.coerceAtLeast(0)
    Box(Modifier.navigationBarsPadding().padding(bottom = 14.dp)) {
        BoxWithConstraints(
            Modifier.width(270.dp).height(68.dp).shadow(30.dp, CircleShape, spotColor = Color.Black)
                .glass(CircleShape).background(p.sheet.copy(alpha = 0.55f)).padding(6.dp),
        ) {
            val w = maxWidth / tabs.size
            val x by animateDpAsState(w * index, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "dock")
            Box(
                Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.20f else 0.95f), Color.White.copy(alpha = if (p.dark) 0.08f else 0.6f))))
                    .border(1.dp, Color.White.copy(alpha = if (p.dark) 0.25f else 1f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, (tab, icon) ->
                    val tint by animateColorAsState(if (i == index) p.text else p.muted, label = "tint")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.tab = tab },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

package app.mangavault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
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
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).dotted().systemBarsPadding()) {
                BackHandler(enabled = vm.screen != Screen.MAIN || vm.tab != Tab.HOME) {
                    if (vm.screen != Screen.MAIN) vm.back() else vm.tab = Tab.HOME
                }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (vm.screen) {
                            Screen.RESULT -> ResultScreen(vm, onDownload)
                            Screen.PROGRESS -> ProgressScreen(vm)
                            Screen.MAIN -> when (vm.tab) {
                                Tab.HOME -> HomeScreen(vm)
                                Tab.DOWNLOADS -> DownloadsScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                    if (vm.screen == Screen.MAIN) Dock(vm)
                }
            }
        }
    }
}

@Composable
private fun Dock(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp).clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(30.dp)).padding(6.dp),
    ) {
        DockItem("⌂", t("nav_home"), vm.tab == Tab.HOME, Modifier.weight(1f)) { vm.tab = Tab.HOME }
        DockItem("⬇", t("nav_downloads"), vm.tab == Tab.DOWNLOADS, Modifier.weight(1f)) { vm.tab = Tab.DOWNLOADS }
        DockItem("⚙", t("nav_settings"), vm.tab == Tab.SETTINGS, Modifier.weight(1f)) { vm.tab = Tab.SETTINGS }
    }
}

@Composable
private fun DockItem(icon: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(if (active) Yellow else Color.Transparent).clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 18.sp, color = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 11.sp, color = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

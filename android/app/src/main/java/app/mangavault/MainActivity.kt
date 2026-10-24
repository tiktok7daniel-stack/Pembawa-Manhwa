package app.mangavault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import app.mangavault.source.BrowserHost
import app.mangavault.ui.AppViewModel
import app.mangavault.ui.MangaVaultApp
import app.mangavault.ui.Screen
import app.mangavault.ui.Strings
import app.mangavault.data.Prefs

class MainActivity : ComponentActivity() {
    val vm: AppViewModel by viewModels()
    private var afterStoragePermission: (() -> Unit)? = null

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterStoragePermission?.invoke()
        afterStoragePermission = null
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** System file picker for the compression tool. */
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            vm.onPicked(uri)
        }
    }

    fun pickFile() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        try {
            filePicker.launch(arrayOf("application/pdf", "application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/octet-stream", "application/x-zip-compressed"))
        } catch (_: Exception) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // WebViews used by "Browser mode" must be attached to a window to render, so they live in this
        // (almost invisible) layer underneath the UI.
        val browserLayer = FrameLayout(this).apply { alpha = 0.01f }
        BrowserHost.container = browserLayer
        val ui = ComposeView(this).apply { setContent { MangaVaultApp(vm, ::beginDownload, ::pickFile) } }
        val root = FrameLayout(this)
        root.addView(browserLayer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(ui, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Links shared to the app from a browser land in the paste box. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Regex("https?://\\S+").find(text)?.let {
            vm.url = it.value
            vm.screen = Screen.MAIN
            vm.tab = app.mangavault.ui.Tab.HOME
        }
    }

    fun beginDownload() {
        if (vm.selected.isEmpty()) {
            Toast.makeText(this, Strings.get(Prefs.lang.value, "pick_one"), Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            afterStoragePermission = { vm.startDownload() }
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.startDownload()
        }
    }

    override fun onDestroy() {
        if (isFinishing) BrowserHost.container = null
        super.onDestroy()
    }
}

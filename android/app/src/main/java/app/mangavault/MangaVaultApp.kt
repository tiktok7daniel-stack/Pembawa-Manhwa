package app.mangavault

import android.app.Application
import app.mangavault.data.Prefs

class MangaVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}

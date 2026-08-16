package nl.mikesmits.p2000

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import nl.mikesmits.p2000.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
    }
}

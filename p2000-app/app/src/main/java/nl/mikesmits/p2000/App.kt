package nl.mikesmits.p2000

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import nl.mikesmits.p2000.data.P2000Data
import nl.mikesmits.p2000.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        P2000Data.init(this)
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
    }
}

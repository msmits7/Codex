package nl.mikesmits.p2000

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import nl.mikesmits.p2000.data.P2000Data
import nl.mikesmits.p2000.data.Prefs
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        P2000Data.init(this)
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
    }

    /**
     * Schrijft een eventuele crash naar files/last_crash.txt zodat die via
     * een bestandsbeheerder gedeeld kan worden, en geeft daarna door aan de
     * standaard handler.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val dir = getExternalFilesDir(null) ?: filesDir
                PrintWriter(File(dir, "last_crash.txt")).use { w ->
                    w.println("$stamp · thread ${thread.name}")
                    throwable.printStackTrace(w)
                }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

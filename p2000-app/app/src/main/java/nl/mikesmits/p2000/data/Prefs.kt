package nl.mikesmits.p2000.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Persists filters and theme choice across app restarts. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("p2000", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit { putInt("theme_mode", value) }

    /**
     * Aangevinkte types. Leeg = geen typefilter, dus alles tonen. Sleutel v2:
     * de oude opslag betekende juist "alles behalve de uitgevinkte types" en
     * mocht daarom niet worden overgenomen.
     */
    var filterTypes: Set<ServiceType>
        get() {
            val saved = prefs.getStringSet("filter_types_v2", null) ?: return emptySet()
            return saved.mapNotNull { name ->
                ServiceType.values().find { it.name == name }
            }.toSet()
        }
        set(value) = prefs.edit { putStringSet("filter_types_v2", value.map { it.name }.toSet()) }

    var locationQuery: String
        get() = prefs.getString("filter_location", "") ?: ""
        set(value) = prefs.edit { putString("filter_location", value) }

    var radiusKm: Int
        get() = prefs.getInt("filter_radius_km", 0)
        set(value) = prefs.edit { putInt("filter_radius_km", value) }

    /** Hoe ver terugkijken, in minuten. 1440 (24 uur) = volledige historie. */
    var windowMinutes: Int
        get() = prefs.getInt("filter_window_minutes", 1440)
        set(value) = prefs.edit { putInt("filter_window_minutes", value) }

    var backgroundEnabled: Boolean
        get() = prefs.getBoolean("background_enabled", false)
        set(value) = prefs.edit { putBoolean("background_enabled", value) }
}

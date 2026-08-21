package nl.mikesmits.p2000.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Persists filters and theme choice across app restarts. */
class Prefs(context: Context, private val prefix: String = "") {

    private val prefs = context.getSharedPreferences("p2000", Context.MODE_PRIVATE)

    /** Sleutels van het buurt-scherm blijven ongeprefixt, zodat bestaande
     *  instellingen behouden blijven; het Alles-scherm heeft zijn eigen set. */
    private fun key(naam: String) = prefix + naam

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
            val saved = prefs.getStringSet(key("filter_types_v2"), null) ?: return emptySet()
            return saved.mapNotNull { name ->
                ServiceType.values().find { it.name == name }
            }.toSet()
        }
        set(value) = prefs.edit { putStringSet(key("filter_types_v2"), value.map { it.name }.toSet()) }

    var locationQuery: String
        get() = prefs.getString(key("filter_location"), "") ?: ""
        set(value) = prefs.edit { putString(key("filter_location"), value) }

    var radiusKm: Int
        get() = prefs.getInt(key("filter_radius_km"), 0)
        set(value) = prefs.edit { putInt(key("filter_radius_km"), value) }

    /** Hoe ver terugkijken, in minuten. 1440 (24 uur) = volledige historie. */
    var windowMinutes: Int
        get() = prefs.getInt(key("filter_window_minutes"), 1440)
        set(value) = prefs.edit { putInt(key("filter_window_minutes"), value) }

    var backgroundEnabled: Boolean
        get() = prefs.getBoolean("background_enabled", false)
        set(value) = prefs.edit { putBoolean("background_enabled", value) }
}

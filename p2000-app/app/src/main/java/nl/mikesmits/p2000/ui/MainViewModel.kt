package nl.mikesmits.p2000.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.mikesmits.p2000.PollService
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.MeldingGroep
import nl.mikesmits.p2000.data.P2000Data
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType
import kotlin.math.abs

data class FilterState(
    val types: Set<ServiceType> = ServiceType.values().toSet(),
    val locationQuery: String = "",
    val radiusKm: Int = 0,              // 0 = radius filter off
    val windowMinutes: Int = 1440,      // hoe ver terugkijken; 1440 = volledige 24u-historie
    val myLocation: Location? = null
) {
    val radiusActive: Boolean get() = radiusKm > 0 && myLocation != null
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val GROUP_WINDOW_MS = 15 * 60 * 1000L
        private const val POLL_INTERVAL_MS = 15_000L
        private const val UI_REFRESH_MS = 5_000L
    }

    private val repository = P2000Data.also { it.init(application) }.repository
    private val prefs = Prefs(application)

    private val _all = MutableStateFlow<List<Melding>>(emptyList())
    private val _filter = MutableStateFlow(
        FilterState(
            types = prefs.filterTypes,
            locationQuery = prefs.locationQuery,
            radiusKm = prefs.radiusKm,
            windowMinutes = prefs.windowMinutes
        )
    )
    private val _status = MutableStateFlow("Laden…")

    val filter: StateFlow<FilterState> = _filter
    val status: StateFlow<String> = _status

    /** Gefilterde meldingen, gebundeld per incident. */
    val groups: StateFlow<List<MeldingGroep>> =
        combine(_all, _filter) { all, f -> groupMeldingen(applyFilter(all, f)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repository.ensureLoaded()
            _all.value = repository.current()
            var lastFetch = 0L
            while (true) {
                val now = System.currentTimeMillis()
                // De achtergrond-service ververst zelf; dan alleen de UI bijwerken.
                if (!PollService.running && now - lastFetch >= POLL_INTERVAL_MS) {
                    lastFetch = now
                    try {
                        repository.refresh()
                        repository.geocodeMissing(repository.current())
                    } catch (_: Exception) {
                        _status.value = "Geen verbinding – opnieuw proberen…"
                        delay(UI_REFRESH_MS)
                        continue
                    }
                }
                _all.value = repository.current()
                _status.value = "Live · ${_all.value.size} meldingen (24u)"
                delay(UI_REFRESH_MS)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            try {
                repository.refresh()
                repository.geocodeMissing(repository.current())
                _all.value = repository.current()
                _status.value = "Live · ${_all.value.size} meldingen (24u)"
            } catch (_: Exception) {
                _status.value = "Geen verbinding – opnieuw proberen…"
            }
        }
    }

    fun toggleType(type: ServiceType, enabled: Boolean) {
        val current = _filter.value.types.toMutableSet()
        if (enabled) current.add(type) else current.remove(type)
        _filter.value = _filter.value.copy(types = current)
        prefs.filterTypes = current
    }

    fun setLocationQuery(query: String) {
        _filter.value = _filter.value.copy(locationQuery = query.trim())
        prefs.locationQuery = query.trim()
    }

    fun setRadiusKm(km: Int) {
        _filter.value = _filter.value.copy(radiusKm = km)
        prefs.radiusKm = km
    }

    fun setWindowMinutes(minutes: Int) {
        _filter.value = _filter.value.copy(windowMinutes = minutes)
        prefs.windowMinutes = minutes
    }

    fun setMyLocation(location: Location?) {
        _filter.value = _filter.value.copy(myLocation = location)
    }

    private fun applyFilter(all: List<Melding>, f: FilterState): List<Melding> {
        val cutoff = System.currentTimeMillis() - f.windowMinutes * 60_000L
        return all.filter { m ->
            if (m.time.time < cutoff) return@filter false
            if (m.type !in f.types) return@filter false
            if (f.locationQuery.isNotEmpty()) {
                val q = f.locationQuery.lowercase()
                val haystack = listOfNotNull(m.city, m.street, m.region, m.province, m.postcode, m.rawTitle)
                    .joinToString(" ").lowercase()
                if (!haystack.contains(q)) return@filter false
            }
            if (f.radiusActive) {
                val lat = m.lat ?: return@filter false
                val lon = m.lon ?: return@filter false
                val results = FloatArray(1)
                Location.distanceBetween(
                    f.myLocation!!.latitude, f.myLocation.longitude, lat, lon, results
                )
                if (results[0] > f.radiusKm * 1000f) return@filter false
            }
            true
        }
    }

    /**
     * Bundel meldingen van hetzelfde incident: zelfde locatiesleutel en dicht
     * bij elkaar in de tijd. De lijst komt binnen op volgorde nieuwste eerst.
     */
    private fun groupMeldingen(list: List<Melding>): List<MeldingGroep> {
        val result = mutableListOf<MutableList<Melding>>()
        val byKey = HashMap<String, MutableList<MutableList<Melding>>>()
        for (m in list) {
            val key = m.groupKey
            if (key != null) {
                val target = byKey[key]?.firstOrNull { g ->
                    abs(g.last().time.time - m.time.time) <= GROUP_WINDOW_MS
                }
                if (target != null) {
                    target.add(m)
                    continue
                }
            }
            val group = mutableListOf(m)
            result.add(group)
            if (key != null) byKey.getOrPut(key) { mutableListOf() }.add(group)
        }
        return result.map { MeldingGroep(it) }
    }
}

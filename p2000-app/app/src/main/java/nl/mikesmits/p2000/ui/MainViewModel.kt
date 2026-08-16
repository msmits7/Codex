package nl.mikesmits.p2000.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.MeldingRepository
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType

data class FilterState(
    val types: Set<ServiceType> = ServiceType.values().toSet(),
    val locationQuery: String = "",
    val radiusKm: Int = 0,              // 0 = radius filter off
    val myLocation: Location? = null
) {
    val radiusActive: Boolean get() = radiusKm > 0 && myLocation != null
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeldingRepository()
    private val prefs = Prefs(application)

    private val _all = MutableStateFlow<List<Melding>>(emptyList())
    private val _filter = MutableStateFlow(
        FilterState(
            types = prefs.filterTypes,
            locationQuery = prefs.locationQuery,
            radiusKm = prefs.radiusKm
        )
    )
    private val _status = MutableStateFlow("Laden…")

    val filter: StateFlow<FilterState> = _filter
    val status: StateFlow<String> = _status

    val filtered: StateFlow<List<Melding>> =
        combine(_all, _filter) { all, f -> applyFilter(all, f) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    _all.value = repository.refresh()
                    _status.value = "Live · ${_all.value.size} meldingen"
                    // Geocode in the background; push updates as coordinates come in.
                    if (repository.geocodeMissing(_all.value)) {
                        _all.value = repository.current()
                    }
                } catch (e: Exception) {
                    _status.value = "Geen verbinding – opnieuw proberen…"
                }
                delay(30_000)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            try {
                _all.value = repository.refresh()
                _status.value = "Live · ${_all.value.size} meldingen"
                if (repository.geocodeMissing(_all.value)) {
                    _all.value = repository.current()
                }
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

    fun setMyLocation(location: Location?) {
        _filter.value = _filter.value.copy(myLocation = location)
    }

    private fun applyFilter(all: List<Melding>, f: FilterState): List<Melding> {
        return all.filter { m ->
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
}

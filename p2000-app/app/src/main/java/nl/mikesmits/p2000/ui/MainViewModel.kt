package nl.mikesmits.p2000.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.mikesmits.p2000.BuildConfig
import nl.mikesmits.p2000.PollService
import nl.mikesmits.p2000.data.Diagnostics
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.MeldingGroep
import nl.mikesmits.p2000.data.P2000Data
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class FilterState(
    /** Aangevinkte types; leeg betekent geen typefilter (alles tonen). */
    val types: Set<ServiceType> = emptySet(),
    val locationQuery: String = "",
    val radiusKm: Int = 0,              // 0 = radius filter off
    val windowMinutes: Int = 1440,      // hoe ver terugkijken; 1440 = volledige 24u-historie
    val myLocation: Location? = null,
    /** Vrij zoeken op het tabblad "Alles"; staat los van het locatiefilter. */
    val zoekAlles: String = ""
) {
    val radiusActive: Boolean get() = radiusKm > 0 && myLocation != null

    /**
     * Een type is zichtbaar als er niets is aangevinkt (geen filter) of als dit
     * type juist wél is aangevinkt. Aanvinken betekent dus "alleen dit tonen".
     */
    fun matchesType(type: ServiceType): Boolean = types.isEmpty() || type in types

    /**
     * Is de locatie nauwkeurig genoeg om op afstand te filteren? Een melding die
     * alleen op de regio te plaatsen is (of helemaal niet), hoort thuis op het
     * tabblad "Alles" en niet in de lijst met meldingen in de buurt.
     */
    fun heeftDuidelijkeLocatie(melding: Melding): Boolean =
        melding.exacteLocatie || (melding.extent != null && !melding.grofGebied)

    /**
     * Valt de melding binnen de ingestelde straal? Bij een plaats zonder exact
     * adres telt de afstand tot de rand van die gemeente, zodat een melding
     * meedoet zodra de gemeente ook maar deels binnen de straal ligt.
     */
    fun withinRadius(melding: Melding): Boolean {
        if (!radiusActive) return true
        val here = myLocation ?: return true
        if (!heeftDuidelijkeLocatie(melding)) return false
        val meters = melding.distanceMetersFrom(here.latitude, here.longitude) ?: return false
        return meters <= radiusKm * 1000.0
    }

    /** Het locatie-tekstfilter uit het filterpaneel. */
    fun matchesLocatie(melding: Melding): Boolean {
        if (locationQuery.isEmpty()) return true
        val hooiberg = listOfNotNull(
            melding.city, melding.street, melding.region, melding.province,
            melding.postcode, melding.rawTitle
        ).joinToString(" ").lowercase()
        return hooiberg.contains(locationQuery.lowercase())
    }

    /** Vrije zoekterm toepassen op alles wat een melding aan tekst heeft. */
    fun matchesZoek(melding: Melding): Boolean {
        if (zoekAlles.isBlank()) return true
        val hooiberg = listOfNotNull(
            melding.rawTitle, melding.description, melding.city, melding.street,
            melding.postcode, melding.region, melding.province, melding.aard,
            melding.prio, melding.type.label, melding.bron, melding.dossier
        ).joinToString(" ").lowercase()
        return zoekAlles.lowercase().split(" ").filter { it.isNotBlank() }
            .all { hooiberg.contains(it) }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val GROUP_WINDOW_MS = 15 * 60 * 1000L
        private const val POLL_INTERVAL_MS = 15_000L
        private const val UI_REFRESH_MS = 5_000L
    }

    /** De twee schermen met een eigen filterset. */
    enum class Scherm { BUURT, ALLES }

    private val repository = P2000Data.also { it.init(application) }.repository
    private val prefs = Prefs(application)
    private val allesPrefs = Prefs(application, prefix = "alles_")

    private val _all = MutableStateFlow<List<Melding>>(emptyList())

    private val _buurtFilter = MutableStateFlow(
        FilterState(
            types = prefs.filterTypes,
            locationQuery = prefs.locationQuery,
            radiusKm = prefs.radiusKm,
            windowMinutes = prefs.windowMinutes
        )
    )

    /** Het Alles-scherm heeft eigen filters; standaard zonder afstandsfilter. */
    private val _allesFilter = MutableStateFlow(
        FilterState(
            types = allesPrefs.filterTypes,
            locationQuery = allesPrefs.locationQuery,
            radiusKm = allesPrefs.radiusKm,
            windowMinutes = allesPrefs.windowMinutes
        )
    )

    private val _scherm = MutableStateFlow(Scherm.BUURT)
    private val _status = MutableStateFlow("Laden…")

    val buurtFilter: StateFlow<FilterState> = _buurtFilter
    val allesFilter: StateFlow<FilterState> = _allesFilter
    val scherm: StateFlow<Scherm> = _scherm
    val status: StateFlow<String> = _status

    /** De filterset van het scherm waar de gebruiker nu op zit. */
    val filter: StateFlow<FilterState> =
        combine(_scherm, _buurtFilter, _allesFilter) { scherm, buurt, alles ->
            if (scherm == Scherm.ALLES) alles else buurt
        }.stateIn(viewModelScope, SharingStarted.Eagerly, _buurtFilter.value)

    fun setScherm(nieuw: Scherm) {
        _scherm.value = nieuw
    }

    private fun huidigeFilter() =
        if (_scherm.value == Scherm.ALLES) _allesFilter else _buurtFilter

    private fun huidigePrefs() =
        if (_scherm.value == Scherm.ALLES) allesPrefs else prefs

    /**
     * Gefilterde meldingen, gebundeld per incident. Filteren en groeperen van
     * duizenden meldingen gebeurt via flowOn buiten de main thread.
     */
    val groups: StateFlow<List<MeldingGroep>> =
        combine(_all, _buurtFilter) { all, f -> groupMeldingen(applyFilter(all, f)) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Tabblad "Alles": het hele land, inclusief meldingen zonder duidelijke
     * locatie en de politieberichten. Heeft een eigen filterset - standaard
     * zonder afstandsfilter - plus de vrije zoekterm van dat tabblad.
     */
    val allesGroups: StateFlow<List<MeldingGroep>> =
        combine(_all, _allesFilter) { all, f ->
            val cutoff = System.currentTimeMillis() - f.windowMinutes * 60_000L
            groupMeldingen(
                all.filter { m ->
                    m.time.time >= cutoff && f.matchesType(m.type) && f.matchesZoek(m) &&
                        f.withinRadius(m) && f.matchesLocatie(m)
                }
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repository.ensureLoaded()
            publishSnapshot()
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
                publishSnapshot()
                delay(UI_REFRESH_MS)
            }
        }
    }

    /** Sorteren/snapshotten van de (grote) historie buiten de main thread. */
    private suspend fun publishSnapshot() {
        val snapshot = withContext(Dispatchers.Default) { repository.current() }
        if (snapshot != _all.value) {
            _all.value = snapshot
        }
        _status.value = "Live · ${snapshot.size} meldingen"
    }

    fun refreshNow() {
        viewModelScope.launch {
            try {
                repository.refresh()
                repository.geocodeMissing(repository.current())
                publishSnapshot()
            } catch (_: Exception) {
                _status.value = "Geen verbinding – opnieuw proberen…"
            }
        }
    }

    fun toggleType(type: ServiceType, enabled: Boolean) {
        val stroom = huidigeFilter()
        val current = stroom.value.types.toMutableSet()
        if (enabled) current.add(type) else current.remove(type)
        stroom.value = stroom.value.copy(types = current)
        huidigePrefs().filterTypes = current
    }

    /** Alle typefilters uitzetten (= alles tonen). */
    fun clearTypes() {
        val stroom = huidigeFilter()
        stroom.value = stroom.value.copy(types = emptySet())
        huidigePrefs().filterTypes = emptySet()
    }

    /** Zoekterm hoort altijd bij het Alles-scherm. */
    fun setZoekAlles(term: String) {
        _allesFilter.value = _allesFilter.value.copy(zoekAlles = term.trim())
    }

    fun setLocationQuery(query: String) {
        val stroom = huidigeFilter()
        stroom.value = stroom.value.copy(locationQuery = query.trim())
        huidigePrefs().locationQuery = query.trim()
    }

    fun setRadiusKm(km: Int) {
        val stroom = huidigeFilter()
        stroom.value = stroom.value.copy(radiusKm = km)
        huidigePrefs().radiusKm = km
    }

    fun setWindowMinutes(minutes: Int) {
        val stroom = huidigeFilter()
        stroom.value = stroom.value.copy(windowMinutes = minutes)
        huidigePrefs().windowMinutes = minutes
    }

    /** De eigen locatie geldt voor beide schermen. */
    fun setMyLocation(location: Location?) {
        _buurtFilter.value = _buurtFilter.value.copy(myLocation = location)
        _allesFilter.value = _allesFilter.value.copy(myLocation = location)
    }

    /**
     * Leesbaar overzicht van wat de app binnenkrijgt en waarom meldingen wel of
     * niet door het filter komen - bedoeld om te kopiëren bij een probleem.
     */
    fun diagnoseRapport(): String = buildString {
        val f = _buurtFilter.value
        val alle = _all.value
        val klok = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        appendLine("P2000 Live diagnose")
        appendLine("versie ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("tijd ${klok.format(Date())}")
        appendLine()

        appendLine("FILTERS (scherm: ${_scherm.value})")
        appendLine("  types: " + if (f.types.isEmpty()) "alles" else f.types.joinToString { it.label })
        appendLine("  zoektekst: " + f.locationQuery.ifEmpty { "-" })
        appendLine("  straal: " + if (f.radiusKm == 0) "uit" else "${f.radiusKm} km")
        appendLine("  historie: ${f.windowMinutes} minuten")
        appendLine("  mijn locatie: " + (f.myLocation?.let {
            String.format(Locale.US, "%.4f, %.4f (±%.0f m)", it.latitude, it.longitude, it.accuracy)
        } ?: "onbekend"))
        appendLine("  achtergrondservice: " + if (PollService.running) "aan" else "uit")
        appendLine("  --- Alles-scherm ---")
        val a = _allesFilter.value
        appendLine("  types: " + if (a.types.isEmpty()) "alles" else a.types.joinToString { it.label })
        appendLine("  straal: " + if (a.radiusKm == 0) "uit" else "${a.radiusKm} km")
        appendLine("  historie: ${a.windowMinutes} minuten")
        appendLine("  zoekterm: " + a.zoekAlles.ifEmpty { "-" })
        appendLine()

        appendLine("MELDINGEN")
        appendLine("  in geheugen: ${alle.size}")
        appendLine("  in de buurt: ${groups.value.sumOf { it.meldingen.size }} in ${groups.value.size} incidenten")
        appendLine("  op tabblad Alles: ${allesGroups.value.sumOf { it.meldingen.size }}")
        appendLine("  per bron: " + alle.groupingBy { it.bron }.eachCount()
            .entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "-" })
        appendLine("  per type: " + alle.groupingBy { it.type.label }.eachCount()
            .entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "-" })
        appendLine("  exacte locatie: ${alle.count { it.exacteLocatie }}")
        appendLine("  op plaats: ${alle.count { !it.exacteLocatie && it.extent != null && !it.grofGebied }}")
        appendLine("  alleen op regio: ${alle.count { it.grofGebied }}")
        appendLine("  locatie onbekend: ${alle.count { !it.exacteLocatie && it.extent == null && it.lat == null }}")
        appendLine()

        appendLine("LAATSTE 20 MELDINGEN (afstand vanaf mijn locatie)")
        val hier = f.myLocation
        for (m in alle.take(20)) {
            val afstand = hier?.let { m.distanceMetersFrom(it.latitude, it.longitude) }
            val afstandTekst = when {
                hier == null -> "geen locatie"
                afstand == null -> "ONBEKEND"
                else -> String.format(Locale.US, "%.1f km", afstand / 1000)
            }
            val soort = when {
                m.exacteLocatie -> "adres"
                m.grofGebied -> "regio"
                m.extent != null -> "plaats"
                else -> "geen"
            }
            val zichtbaar = if (f.withinRadius(m) && f.matchesType(m.type)) "BUURT" else "alles"
            appendLine(
                "  $zichtbaar ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(m.time)} " +
                    "${m.type.label} ${m.prio ?: "-"} | ${m.city ?: "?"} / ${m.region ?: "?"} " +
                    "| $afstandTekst ($soort) | ${m.bron}"
            )
            appendLine("        ${m.rawTitle.take(90)}")
        }
        appendLine()

        appendLine("LOGBOEK")
        Diagnostics.recent().forEach { appendLine("  $it") }
    }

    private fun applyFilter(all: List<Melding>, f: FilterState): List<Melding> {
        val cutoff = System.currentTimeMillis() - f.windowMinutes * 60_000L
        return all.filter { m ->
            if (m.time.time < cutoff) return@filter false
            if (!f.matchesType(m.type)) return@filter false
            if (!f.matchesLocatie(m)) return@filter false
            if (!f.withinRadius(m)) return@filter false
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

package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the live P2000 feed and builds a rolling 24-hour history, merged on
 * GUID and persisted to disk so restarts keep the history.
 */
class MeldingRepository(private val store: HistoryStore? = null) {

    companion object {
        private const val FEED_URL = "https://alarmeringen.nl/feeds/all.rss"

        /** Publieke politie-feeds: getuigenoproepen, opsporing, vermisten en nieuws. */
        private val POLITIE_FEEDS = listOf(
            "https://rss.politie.nl/rss/algemeen/ob/alle-gezochtberichten.xml" to "Getuigenoproep / opsporing",
            "https://rss.politie.nl/rss/algemeen/vp/alle-vermiste-personen.xml" to "Vermist persoon",
            "https://rss.politie.nl/rss/algemeen/nb/alle-nieuwsberichten.xml" to "Politienieuws"
        )
        private const val POLITIE_INTERVAL_MS = 5 * 60 * 1000L

        /**
         * Tweede P2000-bron. De feed van alarmeringen.nl bevat vrijwel alleen
         * A1-spoedmeldingen; deze monitorpagina bevat alle prioriteiten,
         * inclusief A2 en besteld vervoer (B1/B2).
         */
        private const val P2000_ONLINE_URL = "https://www.p2000-online.net/p2000.py"
        private const val P2000_ONLINE_INTERVAL_MS = 30_000L
        /** Historie tot een week terug, zodat de langere tijdfilters werken. */
        private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
        private const val MAX_ITEMS = 60000
        private const val MAX_POLITIEBERICHTEN = 500
        private const val SAVE_INTERVAL_MS = 3 * 60 * 1000L
    }

    private val byGuid = LinkedHashMap<String, Melding>()
    private var loaded = false
    private var lastSave = 0L
    private var lastPolitieFetch = 0L
    private var lastOnlineFetch = 0L
    /** Ritnummer -> tijdstip, om dezelfde melding uit twee bronnen te herkennen. */
    private val ritIndex = HashMap<String, Long>()

    /** Laad de opgeslagen historie (eenmalig, vóór het eerste gebruik). */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(byGuid) {
            if (!loaded) {
                loaded = true
                store?.load()?.forEach { byGuid[it.guid] = it }
            }
        }
    }

    /** Fetch the feed and return the full, merged, newest-first list. */
    suspend fun refresh(): List<Melding> = withContext(Dispatchers.IO) {
        val conn = URL(FEED_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
        val fresh = conn.inputStream.use { FeedParser.parse(it) }
        Diagnostics.log("alarmeringen.nl: ${fresh.size} meldingen")
        val politie = fetchPolitieFeeds()
        // Eerst de rijk geparseerde feed samenvoegen, daarna de bredere bron;
        // dubbele meldingen worden op ritnummer herkend en overgeslagen.
        synchronized(byGuid) {
            merge(fresh)
            merge(politie)
        }
        val online = fetchP2000Online()
        if (online.isNotEmpty()) {
            val zonderPlaats = online.count { it.city == null }
            Diagnostics.log(
                "p2000-online.net: ${online.size} meldingen, ${zonderPlaats} zonder herleidbare plaats"
            )
        }
        val result = synchronized(byGuid) {
            merge(online)
            pruneAndSort()
        }
        maybePersist(result)
        result
    }

    private fun merge(fresh: List<Melding>) {
        for (m in fresh) {
            // Ritnummers zijn per regio uniek, niet landelijk: regio meenemen
            val regioSleutel = m.region ?: m.province ?: "nl"
            val rit = m.ritNummer?.let { regioSleutel + "|" + it }
            if (rit != null) {
                val eerder = ritIndex[rit]
                // Zelfde rit binnen een uur = dezelfde inzet uit de andere bron
                if (eerder != null && kotlin.math.abs(eerder - m.time.time) < 60 * 60 * 1000L &&
                    !byGuid.containsKey(m.guid)
                ) continue
                ritIndex[rit] = m.time.time
            }
            val existing = byGuid[m.guid]
            if (existing != null) {
                // bewaar wat eerder al is opgezocht
                m.lat = existing.lat
                m.lon = existing.lon
                m.gemeenteCode = existing.gemeenteCode
                m.gemeenteNaam = existing.gemeenteNaam
                m.extent = existing.extent
                m.exacteLocatie = existing.exacteLocatie
                m.grofGebied = existing.grofGebied
            }
            byGuid[m.guid] = m
        }
    }

    /** Politieberichten veranderen traag; hooguit eens per 5 minuten ophalen. */
    private fun fetchPolitieFeeds(): List<Melding> {
        val now = System.currentTimeMillis()
        if (now - lastPolitieFetch < POLITIE_INTERVAL_MS && lastPolitieFetch != 0L) return emptyList()
        lastPolitieFetch = now
        val all = mutableListOf<Melding>()
        for ((url, aard) in POLITIE_FEEDS) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
                conn.inputStream.use { all += PolitieFeedParser.parse(it, aard) }
            }.onFailure { Diagnostics.log("politie-feed mislukt: ${it.javaClass.simpleName}") }
        }
        if (all.isNotEmpty()) Diagnostics.log("politie.nl: ${all.size} berichten")
        return all
    }

    /**
     * Haalt de monitorpagina op en zet die om naar meldingen. De plaats wordt
     * uit de pagerafkorting afgeleid; lukt dat niet, dan blijft de plaats leeg
     * zodat er geen speld op de verkeerde plek belandt.
     */
    private suspend fun fetchP2000Online(): List<Melding> {
        val now = System.currentTimeMillis()
        if (lastOnlineFetch != 0L && now - lastOnlineFetch < P2000_ONLINE_INTERVAL_MS) return emptyList()
        lastOnlineFetch = now
        val html = runCatching {
            val conn = URL(P2000_ONLINE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
            conn.inputStream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        }.getOrNull()
        if (html == null) {
            Diagnostics.log("p2000-online.net niet bereikbaar")
            return emptyList()
        }

        return P2000OnlineParser.parse(html).map { ruw ->
            val verwachteProvincie = Veiligheidsregios.provincie(ruw.regio)
            val kandidaten = PlaatsCodes.kandidaten(ruw.tekst)
            // Eerst een plaats in de eigen provincie; die is het waarschijnlijkst.
            // Levert dat niets op, dan alsnog buiten de provincie kijken - een
            // ambulance uit Rotterdam die naar Amsterdam rijdt is gewoon een
            // melding in Amsterdam.
            val treffer: Pair<String, String>? =
                kandidaten.firstNotNullOfOrNull { token ->
                    PlaatsCodes.zoek(token, verwachteProvincie, eisProvincie = true) {
                        PdokGeocoder.woonplaatsInfo(it)
                    }?.let { naam -> token to naam }
                } ?: kandidaten.firstNotNullOfOrNull { token ->
                    PlaatsCodes.zoek(token, verwachteProvincie, eisProvincie = false) {
                        PdokGeocoder.woonplaatsInfo(it)
                    }?.let { naam -> token to naam }
                }
            val plaats = treffer?.second
            val straat = PlaatsCodes.straatUit(ruw.tekst, treffer?.first)
            val type = P2000OnlineParser.typeVoor(ruw.discipline, ruw.tekst)
            Melding(
                guid = "p2o|${ruw.time.time}|${ruw.tekst.hashCode()}",
                rawTitle = ruw.tekst,
                description = beschrijving(type, straat, plaats, ruw.regio),
                link = P2000_ONLINE_URL,
                time = ruw.time,
                type = type,
                prio = ruw.prio,
                province = null,
                region = ruw.regio,
                city = plaats,
                street = straat,
                postcode = null,
                aard = AardExtractor.aard(ruw.tekst, ""),
                eenheden = AardExtractor.eenheden(ruw.tekst),
                dossier = AardExtractor.dossier(ruw.tekst),
                directeInzet = AardExtractor.directeInzet(ruw.tekst),
                bron = "p2000-online.net"
            )
        }
    }

    private fun beschrijving(type: ServiceType, straat: String?, plaats: String?, regio: String): String {
        val waar = when {
            straat != null && plaats != null -> "$straat in $plaats"
            plaats != null -> plaats
            straat != null -> "$straat ($regio)"
            else -> regio
        }
        return "${type.label} naar $waar"
    }

    /**
     * Zoek posities op voor meldingen die er nog geen hebben. Meldingen die
     * alleen een plaats noemen (zoals politieberichten) krijgen er ook de
     * omhullende van dat gebied bij, zodat het straalfilter op de rand van de
     * gemeente kan rekenen; die worden dus ook nog eens langsgelopen als ze al
     * wel een positie hebben.
     */
    suspend fun geocodeMissing(items: List<Melding>, limit: Int = 120): Boolean = coroutineScope {
        var changed = false

        // Stap 1: plaats-omhullende. Eén opzoeking per plaatsnaam, en daarmee
        // weet het straalfilter meteen of een melding in de buurt ligt - ook
        // als het exacte adres nog niet is opgezocht.
        val zonderGebied = items.filter { it.extent == null && it.city != null }.take(limit)
        for (blok in zonderGebied.groupBy { it.city!! }.entries.chunked(6)) {
            val boxen = blok.map { (plaats, _) ->
                async { plaats to PdokGeocoder.plaatsExtent(plaats) }
            }.awaitAll().toMap()
            for ((plaats, meldingen) in blok) {
                val box = boxen[plaats] ?: continue
                for (m in meldingen) {
                    if (m.extent != box || m.grofGebied) {
                        m.extent = box
                        m.grofGebied = false
                        changed = true
                    }
                }
            }
        }

        // Stap 1b: geen plaats te herleiden? Dan de gemeente van de
        // hoofdplaats van de veiligheidsregio als grove aanduiding.
        val zonderPlaats = items.filter { it.extent == null && it.lat == null }.take(limit)
        val perRegio = zonderPlaats.groupBy { Veiligheidsregios.hoofdplaats(it.region) }
            .filterKeys { it != null }
        for ((hoofdplaats, meldingen) in perRegio) {
            val box = PdokGeocoder.plaatsExtent(hoofdplaats!!) ?: continue
            for (m in meldingen) {
                if (m.extent == null) {
                    m.extent = box
                    m.grofGebied = true
                    changed = true
                }
            }
        }

        // Stap 2: exacte positie per melding, nieuwste eerst.
        val zonderPositie = items.filter { it.lat == null && it.geoQuery != null }.take(limit)
        for (blok in zonderPositie.chunked(8)) {
            val uitkomsten = blok.map { m ->
                async { m to PdokGeocoder.geocode(m.geoQuery!!) }
            }.awaitAll()
            for ((m, geo) in uitkomsten) {
                if (geo == null) continue
                m.lat = geo.lat
                m.lon = geo.lon
                m.gemeenteCode = geo.gemeenteCode
                m.gemeenteNaam = geo.gemeenteNaam
                m.exacteLocatie = geo.exact
                if (geo.extent != null) {
                    m.extent = geo.extent
                    m.grofGebied = false
                }
                changed = true
            }
        }

        if (changed) maybePersist(current())
        Diagnostics.log(
            "locaties opgezocht: ${zonderGebied.size} via plaats, " +
                "${zonderPlaats.size} via provincie, ${zonderPositie.size} exact"
        )
        changed
    }

    fun current(): List<Melding> = synchronized(byGuid) { pruneAndSort() }

    private fun maybePersist(items: List<Melding>) {
        val now = System.currentTimeMillis()
        if (store != null && now - lastSave > SAVE_INTERVAL_MS) {
            lastSave = now
            store.save(items)
        }
    }

    private fun pruneAndSort(): List<Melding> {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        // Politieberichten (opsporing/vermist) gaan vaak over oudere zaken en
        // blijven daarom staan, met een eigen bovengrens.
        var politieOver = MAX_POLITIEBERICHTEN
        val sorted = byGuid.values
            .sortedByDescending { it.time }
            .filter { m ->
                if (m.type == ServiceType.POLITIEBERICHT) politieOver-- > 0
                else m.time.time >= cutoff
            }
            .take(MAX_ITEMS)
        if (sorted.size != byGuid.size) {
            byGuid.clear()
            sorted.forEach { byGuid[it.guid] = it }
        }
        return sorted
    }
}

package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
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
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_ITEMS = 20000
        private const val SAVE_INTERVAL_MS = 60_000L
    }

    private val byGuid = LinkedHashMap<String, Melding>()
    private var loaded = false
    private var lastSave = 0L

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
        val result = synchronized(byGuid) {
            for (m in fresh) {
                val existing = byGuid[m.guid]
                if (existing != null) {
                    // keep coordinates that were already geocoded
                    m.lat = existing.lat
                    m.lon = existing.lon
                }
                byGuid[m.guid] = m
            }
            pruneAndSort()
        }
        maybePersist(result)
        result
    }

    /** Geocode meldingen that don't yet have coordinates. Returns true if anything changed. */
    suspend fun geocodeMissing(items: List<Melding>, limit: Int = 25): Boolean {
        var changed = false
        for (m in items.filter { it.lat == null && it.geoQuery != null }.take(limit)) {
            val coords = PdokGeocoder.geocode(m.geoQuery!!)
            if (coords != null) {
                m.lat = coords.first
                m.lon = coords.second
                changed = true
            }
        }
        if (changed) maybePersist(current())
        return changed
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
        val sorted = byGuid.values
            .filter { it.time.time >= cutoff }
            .sortedByDescending { it.time }
            .take(MAX_ITEMS)
        if (sorted.size != byGuid.size) {
            byGuid.clear()
            sorted.forEach { byGuid[it.guid] = it }
        }
        return sorted
    }
}

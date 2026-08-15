package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the live P2000 feed and keeps a rolling window of recent meldingen,
 * merged on GUID so repeated polls only add new items.
 */
class MeldingRepository {

    companion object {
        private const val FEED_URL = "https://alarmeringen.nl/feeds/all.rss"
        private const val MAX_ITEMS = 300
    }

    private val byGuid = LinkedHashMap<String, Melding>()

    /** Fetch the feed and return the full, merged, newest-first list. */
    suspend fun refresh(): List<Melding> = withContext(Dispatchers.IO) {
        val conn = URL(FEED_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
        val fresh = conn.inputStream.use { FeedParser.parse(it) }
        synchronized(byGuid) {
            for (m in fresh) {
                val existing = byGuid[m.guid]
                if (existing != null) {
                    // keep coordinates that were already geocoded
                    m.lat = existing.lat
                    m.lon = existing.lon
                }
                byGuid[m.guid] = m
            }
            trimAndSort()
        }
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
        return changed
    }

    fun current(): List<Melding> = synchronized(byGuid) { trimAndSort() }

    private fun trimAndSort(): List<Melding> {
        val sorted = byGuid.values.sortedByDescending { it.time }
        if (sorted.size > MAX_ITEMS) {
            val keep = sorted.take(MAX_ITEMS)
            byGuid.clear()
            keep.forEach { byGuid[it.guid] = it }
            return keep
        }
        return sorted
    }
}

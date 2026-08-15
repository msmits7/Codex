package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/**
 * Geocoder backed by the free Dutch PDOK Locatieserver (no API key required).
 * Results are cached in memory per query string.
 */
object PdokGeocoder {

    private const val BASE = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free"
    private val cache = Collections.synchronizedMap(HashMap<String, Pair<Double, Double>?>())

    suspend fun geocode(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        cache[query] ?: run {
            val result = fetch(query)
            cache[query] = result
            result
        }
    }

    private fun fetch(query: String): Pair<Double, Double>? {
        return try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL("$BASE?q=$q&rows=1&fl=centroide_ll")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val docs = json.getJSONObject("response").getJSONArray("docs")
                if (docs.length() == 0) return null
                // centroide_ll format: "POINT(lon lat)"
                val point = docs.getJSONObject(0).getString("centroide_ll")
                val coords = point.substringAfter("(").substringBefore(")").split(" ")
                Pair(coords[1].toDouble(), coords[0].toDouble())
            }
        } catch (_: Exception) {
            null
        }
    }
}

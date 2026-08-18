package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/** Uitkomst van een geocodering: positie plus de gemeente waarin die valt. */
data class GeoResult(
    val lat: Double,
    val lon: Double,
    val gemeenteCode: String?,
    val gemeenteNaam: String?
)

/**
 * Geocoder backed by the free Dutch PDOK Locatieserver (no API key required).
 * Results are cached in memory per query string.
 */
object PdokGeocoder {

    private const val BASE = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free"
    private val cache = Collections.synchronizedMap(HashMap<String, GeoResult?>())

    suspend fun geocode(query: String): GeoResult? = withContext(Dispatchers.IO) {
        if (cache.containsKey(query)) return@withContext cache[query]
        val result = fetch(query)
        cache[query] = result
        result
    }

    private fun fetch(query: String): GeoResult? {
        return try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL("$BASE?q=$q&rows=1&fl=centroide_ll,gemeentecode,gemeentenaam")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val docs = json.getJSONObject("response").getJSONArray("docs")
                if (docs.length() == 0) return null
                val doc = docs.getJSONObject(0)
                // centroide_ll format: "POINT(lon lat)"
                val coords = doc.getString("centroide_ll")
                    .substringAfter("(").substringBefore(")").split(" ")
                GeoResult(
                    lat = coords[1].toDouble(),
                    lon = coords[0].toDouble(),
                    gemeenteCode = doc.optString("gemeentecode").takeIf { it.isNotEmpty() },
                    gemeenteNaam = doc.optString("gemeentenaam").takeIf { it.isNotEmpty() }
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

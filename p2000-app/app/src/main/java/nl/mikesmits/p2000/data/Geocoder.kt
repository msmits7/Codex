package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/**
 * Uitkomst van een geocodering: positie, de gemeente waarin die valt en - als
 * het resultaat geen exact adres is maar een plaats of gemeente - de omhullende
 * van dat gebied, zodat er op afstand tot de rand gefilterd kan worden.
 */
data class GeoResult(
    val lat: Double,
    val lon: Double,
    val gemeenteCode: String?,
    val gemeenteNaam: String?,
    val extent: Bbox? = null,
    /** True bij een adres, postcode of straat; false bij plaats/gemeente. */
    val exact: Boolean = false
)

/**
 * Geocoder backed by the free Dutch PDOK Locatieserver (no API key required).
 * Results are cached in memory per query string.
 */
object PdokGeocoder {

    private const val SEARCH = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free"
    private const val LOOKUP = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/lookup"

    /** Resultaattypen die een gebied beschrijven in plaats van één punt. */
    private val gebiedTypes = setOf("woonplaats", "gemeente", "provincie", "wijk", "buurt")

    private val cache = Collections.synchronizedMap(HashMap<String, GeoResult?>())
    private val extentCache = Collections.synchronizedMap(HashMap<String, Bbox?>())

    suspend fun geocode(query: String): GeoResult? = withContext(Dispatchers.IO) {
        if (cache.containsKey(query)) return@withContext cache[query]
        val result = fetch(query)
        cache[query] = result
        result
    }

    private val plaatsExtentCache = Collections.synchronizedMap(HashMap<String, Bbox?>())

    /**
     * Omhullende van een plaats, los van een straat. Hiermee weet de app al
     * waar een melding ongeveer ligt vóórdat het exacte adres is opgezocht,
     * zodat het straalfilter meteen kan beslissen.
     */
    suspend fun plaatsExtent(plaats: String): Bbox? = withContext(Dispatchers.IO) {
        if (plaatsExtentCache.containsKey(plaats)) return@withContext plaatsExtentCache[plaats]
        val box = try {
            val q = URLEncoder.encode(plaats, "UTF-8")
            val url = URL("$SEARCH?q=$q&rows=1&fq=type:(woonplaats OR gemeente)&fl=id")
            val id = readJson(url)?.getJSONObject("response")?.getJSONArray("docs")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("id")
            if (id.isNullOrEmpty()) null else extentFor(id)
        } catch (_: Exception) {
            null
        }
        plaatsExtentCache[plaats] = box
        box
    }

    private val provincieExtentCache = Collections.synchronizedMap(HashMap<String, Bbox?>())

    /**
     * Omhullende van een provincie. Grove terugval voor meldingen waarvan de
     * plaatsnaam niet te herleiden is; genoeg om te zien of ze überhaupt in de
     * buurt kunnen liggen.
     */
    suspend fun provincieExtent(provincie: String): Bbox? = withContext(Dispatchers.IO) {
        if (provincieExtentCache.containsKey(provincie)) return@withContext provincieExtentCache[provincie]
        val box = try {
            val q = URLEncoder.encode(provincie, "UTF-8")
            val url = URL("$SEARCH?q=$q&rows=1&fq=type:provincie&fl=id")
            val id = readJson(url)?.getJSONObject("response")?.getJSONArray("docs")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("id")
            if (id.isNullOrEmpty()) null else extentFor(id)
        } catch (_: Exception) {
            null
        }
        provincieExtentCache[provincie] = box
        box
    }

    private val woonplaatsCache = Collections.synchronizedMap(HashMap<String, Pair<String, String?>?>())

    /**
     * Bestaat deze naam als woonplaats? Geeft de officiële schrijfwijze en de
     * provincie terug, zodat de aanroeper kan controleren of de plaats past bij
     * de regio van de melding.
     */
    suspend fun woonplaatsInfo(naam: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        if (woonplaatsCache.containsKey(naam)) return@withContext woonplaatsCache[naam]
        val info = try {
            val q = URLEncoder.encode(naam, "UTF-8")
            val url = URL("$SEARCH?q=$q&rows=1&fq=type:woonplaats&fl=weergavenaam,provincienaam")
            val doc = readJson(url)
                ?.getJSONObject("response")?.getJSONArray("docs")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)
            val plaats = doc?.optString("weergavenaam")?.substringBefore(",")?.takeIf { it.isNotEmpty() }
            if (plaats == null) null
            else plaats to doc.optString("provincienaam").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        woonplaatsCache[naam] = info
        info
    }

    private fun fetch(query: String): GeoResult? {
        return try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL("$SEARCH?q=$q&rows=1&fl=id,type,centroide_ll,gemeentecode,gemeentenaam")
            val doc = readJson(url)
                ?.getJSONObject("response")?.getJSONArray("docs")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)
                ?: return null

            // centroide_ll format: "POINT(lon lat)"
            val coords = doc.getString("centroide_ll")
                .substringAfter("(").substringBefore(")").split(" ")
            val type = doc.optString("type")
            val id = doc.optString("id")

            GeoResult(
                lat = coords[1].toDouble(),
                lon = coords[0].toDouble(),
                gemeenteCode = doc.optString("gemeentecode").takeIf { it.isNotEmpty() },
                gemeenteNaam = doc.optString("gemeentenaam").takeIf { it.isNotEmpty() },
                extent = if (type in gebiedTypes && id.isNotEmpty()) extentFor(id) else null,
                exact = type !in gebiedTypes
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Omhullende van een plaats/gemeente, uit de polygoon van de Locatieserver. */
    private fun extentFor(id: String): Bbox? {
        if (extentCache.containsKey(id)) return extentCache[id]
        val box = try {
            val url = URL("$LOOKUP?id=${URLEncoder.encode(id, "UTF-8")}&fl=geometrie_ll")
            val doc = readJson(url)
                ?.getJSONObject("response")?.getJSONArray("docs")
                ?.takeIf { it.length() > 0 }?.getJSONObject(0)
            doc?.optString("geometrie_ll")?.takeIf { it.isNotEmpty() }?.let(GeoUtils::bboxFromWkt)
        } catch (_: Exception) {
            null
        }
        extentCache[id] = box
        return box
    }

    private fun readJson(url: URL): JSONObject? {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
        return conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }
}

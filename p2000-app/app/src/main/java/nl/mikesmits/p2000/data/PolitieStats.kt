package nl.mikesmits.p2000.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** Geregistreerde criminaliteit in een gemeente over de laatst bekende maand. */
data class GemeenteStats(
    val gemeenteNaam: String,
    val periodeLabel: String,
    val totaalMisdrijven: Int,
    val soortLabel: String?,
    val soortAantal: Int?
)

/**
 * Haalt cijfers op bij data.politie.nl (tabel 47013NED, "Geregistreerde
 * misdrijven en aangiften; soort misdrijf, gemeente"), geserveerd via de
 * open OData-service van CBS. Dit zijn maandcijfers per gemeente - geen
 * gegevens over individuele meldingen; die publiceert de politie niet.
 */
object PolitieStats {

    private const val BASE = "https://dataderden.cbs.nl/ODataApi/odata/47013NED/TypedDataSet"
    private const val TOTAAL = "0.0.0"

    private val cache = Collections.synchronizedMap(HashMap<String, GemeenteStats?>())

    /** Soort misdrijf dat het beste past bij de aard van de melding. */
    private val aardNaarSoort: List<Pair<Regex, Pair<String, String>>> = listOf(
        Regex("""brand|ontploffing|explosie""") to ("1.6.1" to "Brand/ontploffing"),
        Regex("""verkeersongeval|ongeval|aanrijding|beknelling|letsel""") to ("1.3.1" to "Ongevallen (weg)"),
        Regex("""overval""") to ("1.4.7" to "Overval"),
        Regex("""straatroof""") to ("1.4.6" to "Straatroof"),
        Regex("""mishandeling""") to ("1.4.5" to "Mishandeling"),
        Regex("""bedreiging""") to ("1.4.4" to "Bedreiging"),
        Regex("""schietincident|steekincident|geweld""") to ("1.4.3" to "Openlijk geweld (persoon)"),
        Regex("""moord|doodslag""") to ("1.4.2" to "Moord, doodslag"),
        Regex("""inbraak|diefstal""") to ("1.1.1" to "Diefstal/inbraak woning")
    )

    suspend fun forGemeente(
        gemeenteCode: String,
        gemeenteNaam: String,
        aard: String?
    ): GemeenteStats? = withContext(Dispatchers.IO) {
        val soort = aard?.lowercase()?.let { a ->
            aardNaarSoort.firstOrNull { it.first.containsMatchIn(a) }?.second
        }
        val key = "$gemeenteCode|${soort?.first ?: "-"}"
        if (cache.containsKey(key)) return@withContext cache[key]

        val result = runCatching {
            val regio = "GM$gemeenteCode"
            val totaal = laatsteWaarde(regio, TOTAAL) ?: return@runCatching null
            val soortWaarde = soort?.let { laatsteWaarde(regio, it.first) }
            GemeenteStats(
                gemeenteNaam = gemeenteNaam,
                periodeLabel = periodeLabel(totaal.first),
                totaalMisdrijven = totaal.second,
                soortLabel = soort?.second,
                soortAantal = soortWaarde?.second
            )
        }.getOrNull()
        cache[key] = result
        result
    }

    /** Laatste periode met een cijfer, als (periode, aantal). */
    private fun laatsteWaarde(regio: String, soortMisdrijf: String): Pair<String, Int>? {
        // De sleutels in de tabel zijn met spaties opgevuld tot vaste lengte.
        val soortKey = soortMisdrijf.padEnd(6, ' ').replace(" ", "%20")
        val url = URL(
            "$BASE?\$filter=RegioS%20eq%20'$regio'%20and%20SoortMisdrijf%20eq%20'$soortKey'" +
                "&\$select=Perioden,GeregistreerdeMisdrijven_1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "P2000Live/1.0 (Android)")
        conn.inputStream.bufferedReader().use { reader ->
            val rows = JSONObject(reader.readText()).getJSONArray("value")
            for (i in rows.length() - 1 downTo 0) {
                val row = rows.getJSONObject(i)
                if (row.isNull("GeregistreerdeMisdrijven_1")) continue
                return row.getString("Perioden") to row.getInt("GeregistreerdeMisdrijven_1")
            }
        }
        return null
    }

    /** "2026MM07" → "juli 2026" */
    private fun periodeLabel(periode: String): String {
        val maanden = listOf(
            "januari", "februari", "maart", "april", "mei", "juni",
            "juli", "augustus", "september", "oktober", "november", "december"
        )
        val jaar = periode.take(4)
        val maand = periode.substringAfter("MM", "").toIntOrNull()
        return if (maand != null && maand in 1..12) "${maanden[maand - 1]} $jaar" else jaar
    }
}

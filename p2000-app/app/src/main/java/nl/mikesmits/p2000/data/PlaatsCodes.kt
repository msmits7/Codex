package nl.mikesmits.p2000.data

import java.util.Collections

/**
 * P2000-pagerteksten korten plaatsnamen af ("SGRAVH" voor Den Haag). Zonder
 * die vertaling zet een geocoder de melding in de verkeerde plaats - PDOK
 * vindt "De Savornin Lohmanplein SGRAVH" bijvoorbeeld in Groningen. Daarom:
 * eerst deze tabel, dan de vraag of het token zelf een echte woonplaats is, en
 * anders geen plaats (en dus geen speld op de kaart) in plaats van een gok.
 */
object PlaatsCodes {

    /** Alleen codes die zijn terug te zien in de echte feeds. */
    private val vast = mapOf(
        "SGRAVH" to "Den Haag",
        "ROTTDM" to "Rotterdam",
        "ZOETMR" to "Zoetermeer",
        "DORDRT" to "Dordrecht",
        "RIDDKK" to "Ridderkerk",
        "SPIJKN" to "Spijkenisse",
        "VOORB" to "Voorburg",
        "NOOTDP" to "Nootdorp",
        "PIJNAK" to "Pijnacker",
        "RIJSZH" to "Rijswijk",
        "VLAARD" to "Vlaardingen"
    )

    /** Tokens die nooit een plaats zijn. */
    private val stopwoorden = setOf(
        "AMBU", "BRW", "POL", "MMT", "HELI", "KNRM", "TESTOPROEP", "TEST",
        "PRIO", "RIT", "BON", "VWS", "DIA", "GRIP", "MKA", "OMS", "MOB"
    )

    private val woonplaatsCache = Collections.synchronizedMap(HashMap<String, String?>())

    /**
     * Zoek de plaatsnaam bij een token uit de pagertekst.
     * @param isWoonplaats controleert bij de geocoder of het token zelf een
     *        bestaande woonplaats is (bijvoorbeeld "DELFT").
     */
    suspend fun resolve(token: String, isWoonplaats: suspend (String) -> String?): String? {
        val key = token.uppercase().trim('-', ':', ',', '.')
        if (key.length < 3 || key in stopwoorden) return null
        vast[key]?.let { return it }
        if (woonplaatsCache.containsKey(key)) return woonplaatsCache[key]
        val gevonden = isWoonplaats(key)
        woonplaatsCache[key] = gevonden
        return gevonden
    }

    /** Kandidaat-plaatscodes uit een pagertekst, meest waarschijnlijke eerst. */
    fun kandidaten(text: String): List<String> {
        // Hoofdlettertokens, achteraan beginnen: de plaats staat achter het adres.
        return Regex("""\b([A-Z][A-Z'\-]{2,11})\b""").findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in stopwoorden && !it.all { c -> c.isDigit() } }
            .toList()
            .reversed()
    }

    /** Straatdeel: alles vóór de plaatscode, ontdaan van prio en codes. */
    fun straatUit(text: String, plaatsToken: String?): String? {
        var deel = if (plaatsToken != null) text.substringBefore(plaatsToken) else text
        deel = deel
            .replace(Regex("""^\s*(a0|a1|a2|b1|b2|p ?\d|prio ?\d)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(ambu|brw|mmt\d?|dia:?\s*ja)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d{4,6}\b"""), "")
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""[:;]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ',')
        return deel.takeIf { it.length >= 4 }
    }
}

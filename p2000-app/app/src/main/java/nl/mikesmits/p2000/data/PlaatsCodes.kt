package nl.mikesmits.p2000.data

/**
 * P2000-pagerteksten korten plaatsnamen af ("SGRAVH" voor Den Haag). Zonder
 * die vertaling zet een geocoder de melding in de verkeerde plaats - PDOK
 * vindt "De Savornin Lohmanplein SGRAVH" bijvoorbeeld in Groningen. Daarom:
 * eerst deze tabel, dan de vraag of het token zelf een echte woonplaats is, en
 * anders geen plaats (en dus geen speld op de kaart) in plaats van een gok.
 */
object PlaatsCodes {

    private const val MAX_KANDIDATEN = 4

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

    /**
     * Zoek de plaatsnaam bij een token uit de pagertekst.
     * @param isWoonplaats controleert bij de geocoder of het token zelf een
     *        bestaande woonplaats is (bijvoorbeeld "DELFT").
     */
    suspend fun resolve(
        token: String,
        verwachteProvincie: String? = null,
        isWoonplaats: suspend (String) -> Pair<String, String?>?
    ): String? {
        val key = token.uppercase().trim('-', ':', ',', '.')
        if (key.length < 3 || key in stopwoorden) return null
        vast[key]?.let { return it }
        val info = isWoonplaats(key) ?: return null
        // Een straatnaam kan toevallig ook een dorp zijn. Als de gevonden plaats
        // in een andere provincie ligt dan de regio van de melding, is het
        // vrijwel zeker de verkeerde en laten we hem liever staan.
        if (verwachteProvincie != null && info.second != null && info.second != verwachteProvincie) {
            return null
        }
        return info.first
    }

    /**
     * Kandidaat-plaatsnamen uit een pagertekst, meest waarschijnlijke eerst.
     *
     * De landelijke monitorpagina schrijft plaatsen gewoon uit ("A2 Eindhoven
     * Rit: 98623"), de regiopagina's gebruiken hoofdletterafkortingen
     * ("Duinweg SGRAVH"). Beide vormen tellen dus mee. De plaats staat vrijwel
     * altijd achteraan, dus we lopen van achter naar voren en kijken maar naar
     * een handvol tokens - dat scheelt een berg opzoekwerk.
     */
    fun kandidaten(text: String): List<String> {
        val tokens = Regex("""\b([A-Z][A-Za-z'\-]{2,14})\b""").findAll(text)
            .map { it.groupValues[1] }
            .filter { it.uppercase() !in stopwoorden }
            .toList()
        return tokens.reversed().take(MAX_KANDIDATEN)
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

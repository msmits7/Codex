package nl.mikesmits.p2000.data

/**
 * Koppelt de 25 veiligheidsregio's aan hun provincie. Een melding noemt altijd
 * een regio, ook als de plaatsnaam niet te herleiden is; via de provincie weet
 * het straalfilter dan tenminste grofweg waar die ligt, in plaats van de
 * melding maar te tonen omdat de locatie onbekend is.
 */
object Veiligheidsregios {

    private val naarProvincie = mapOf(
        "groningen" to "Groningen",
        "fryslan" to "Fryslân",
        "friesland" to "Fryslân",
        "drenthe" to "Drenthe",
        "ijsselland" to "Overijssel",
        "twente" to "Overijssel",
        "noordenoostgelderland" to "Gelderland",
        "gelderlandmidden" to "Gelderland",
        "gelderlandzuid" to "Gelderland",
        "utrecht" to "Utrecht",
        "flevoland" to "Flevoland",
        "noordhollandnoord" to "Noord-Holland",
        "zaanstreekwaterland" to "Noord-Holland",
        "kennemerland" to "Noord-Holland",
        "amsterdamamstelland" to "Noord-Holland",
        "gooienvechtstreek" to "Noord-Holland",
        "haaglanden" to "Zuid-Holland",
        "hollandsmidden" to "Zuid-Holland",
        "rotterdamrijnmond" to "Zuid-Holland",
        "zuidhollandzuid" to "Zuid-Holland",
        "zeeland" to "Zeeland",
        "middenenwestbrabant" to "Noord-Brabant",
        "middenwestbrabant" to "Noord-Brabant",
        "brabantnoord" to "Noord-Brabant",
        "brabantzuidoost" to "Noord-Brabant",
        "limburgnoord" to "Limburg",
        "limburgzuid" to "Limburg"
    )

    /**
     * Grootste plaats per veiligheidsregio. De provinciegrens bleek als
     * terugval veel te ruim: vanaf Den Haag ligt de rand van Noord-Holland op
     * 19,7 km, waardoor meldingen uit Alkmaar en Hilversum binnen een straal
     * van 25 km vielen. De gemeente van de hoofdplaats zit daar veel dichterbij
     * de werkelijkheid.
     */
    private val naarHoofdplaats = mapOf(
        "groningen" to "Groningen",
        "fryslan" to "Leeuwarden",
        "friesland" to "Leeuwarden",
        "drenthe" to "Assen",
        "ijsselland" to "Zwolle",
        "twente" to "Enschede",
        "noordenoostgelderland" to "Apeldoorn",
        "gelderlandmidden" to "Arnhem",
        "gelderlandzuid" to "Nijmegen",
        "utrecht" to "Utrecht",
        "flevoland" to "Lelystad",
        "noordhollandnoord" to "Alkmaar",
        "zaanstreekwaterland" to "Zaanstad",
        "kennemerland" to "Haarlem",
        "amsterdamamstelland" to "Amsterdam",
        "gooienvechtstreek" to "Hilversum",
        "haaglanden" to "Den Haag",
        "hollandsmidden" to "Leiden",
        "rotterdamrijnmond" to "Rotterdam",
        "zuidhollandzuid" to "Dordrecht",
        "zeeland" to "Middelburg",
        "middenenwestbrabant" to "Tilburg",
        "middenwestbrabant" to "Tilburg",
        "brabantnoord" to "'s-Hertogenbosch",
        "brabantzuidoost" to "Eindhoven",
        "limburgnoord" to "Venlo",
        "limburgzuid" to "Maastricht"
    )

    /** Hoofdplaats van een veiligheidsregio; null als de naam niet past. */
    fun hoofdplaats(naam: String?): String? {
        if (naam.isNullOrBlank()) return null
        return naarHoofdplaats[naam.lowercase().filter { it.isLetter() }]
    }

    /** Provincie bij een regio- of provincienaam; null als er niets past. */
    fun provincie(naam: String?): String? {
        if (naam.isNullOrBlank()) return null
        val sleutel = naam.lowercase().filter { it.isLetter() }
        naarProvincie[sleutel]?.let { return it }
        // De naam kan zelf al een provincie zijn (zo komt alarmeringen.nl binnen)
        return naarProvincie.values.firstOrNull { it.lowercase().filter { c -> c.isLetter() } == sleutel }
    }
}

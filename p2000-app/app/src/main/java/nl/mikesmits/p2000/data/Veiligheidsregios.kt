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

    /** Provincie bij een regio- of provincienaam; null als er niets past. */
    fun provincie(naam: String?): String? {
        if (naam.isNullOrBlank()) return null
        val sleutel = naam.lowercase().filter { it.isLetter() }
        naarProvincie[sleutel]?.let { return it }
        // De naam kan zelf al een provincie zijn (zo komt alarmeringen.nl binnen)
        return naarProvincie.values.firstOrNull { it.lowercase().filter { c -> c.isLetter() } == sleutel }
    }
}

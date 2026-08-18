package nl.mikesmits.p2000.data

/**
 * Leidt extra informatie af uit de ruwe P2000-pagertekst: de aard van de
 * melding, opgeroepen eenheden en rit-/bonnummers. De pagertekst is de enige
 * bron — de feed levert geen gestructureerde velden.
 */
object AardExtractor {

    // Volgorde is belangrijk: specifieke patronen eerst, generieke laatst.
    private val aardPatronen: List<Pair<Regex, String>> = listOf(
        Regex("""grip\s?([1-5])""") to "GRIP-opschaling",
        Regex("""\boms\b|automatische (brand)?melding|brandmelding""") to "Automatische brandmelding (OMS)",
        Regex("""br(and)? woning|woningbrand""") to "Woningbrand",
        Regex("""br(and)? gebouw|gebouwbrand""") to "Gebouwbrand",
        Regex("""br(and)? industrie""") to "Industriebrand",
        Regex("""br(and)? (auto|voertuig|wegvervoer)|autobrand|voertuigbrand""") to "Voertuigbrand",
        Regex("""br(and)? container|containerbrand""") to "Containerbrand",
        Regex("""br(and)? schoorsteen|schoorsteenbrand""") to "Schoorsteenbrand",
        Regex("""br(and)? (buiten|afval|berm|bos|heide|duin)|buitenbrand|bermbrand""") to "Buitenbrand",
        Regex("""br(and)? (schip|vaartuig)|scheepsbrand""") to "Scheepsbrand",
        Regex("""brandgerucht|nacontrole br""") to "Brandgerucht / nacontrole",
        Regex("""nacontrole""") to "Nacontrole",
        Regex("""reanimatie|resuscitatie|\bcpr\b""") to "Reanimatie",
        Regex("""schietincident|schietpartij""") to "Schietincident",
        Regex("""steekincident|steekpartij""") to "Steekincident",
        Regex("""\boverval\b""") to "Overval",
        Regex("""explosie(f|ven)?\b""") to "Explosie / explosief",
        Regex("""vermissing|vermiste? persoon""") to "Vermissing",
        Regex("""beknelling""") to "Beknelling",
        Regex("""(hv|ongeval)[/ ]wegvervoer|\bvko\b|aanrijding|verkeersongeval""") to "Verkeersongeval",
        Regex("""letsel""") to "Ongeval met letsel",
        Regex("""persoon te water""") to "Persoon te water",
        Regex("""voertuig te water""") to "Voertuig te water",
        Regex("""dier te water|dier in nood|dier in problemen""") to "Dier in nood",
        Regex("""gaslek(kage)?|gaslucht""") to "Gaslek / gaslucht",
        Regex("""co[- ]?(melding|melder)|koolmonoxide""") to "CO-melding",
        Regex("""stank(overlast)?|vreemde lucht|hinderlijke lucht""") to "Stankoverlast",
        Regex("""liftopsluiting""") to "Liftopsluiting",
        Regex("""buitensluiting""") to "Buitensluiting",
        Regex("""wateroverlast""") to "Wateroverlast",
        Regex("""stormschade|omgewaaide boom""") to "Stormschade",
        Regex("""tilassistentie""") to "Tilassistentie",
        Regex("""ass(\.|istentie)? ambu(lance)?""") to "Assistentie ambulance",
        Regex("""ass(\.|istentie)? politie""") to "Assistentie politie",
        Regex("""\bmmt\b|traumaheli|lifeliner""") to "MMT / traumaheli-inzet",
        Regex("""reddingsbrigade|knrm|kustwacht""") to "Waterhulpverlening",
        Regex("""\bhv\b|hulpverlening""") to "Hulpverlening (algemeen)",
        // Woordgrenzen: "brandweer" mag hier niet op matchen
        Regex("""\bbr\b|\bbrand\b""") to "Brand (onbepaald)"
    )

    private val gripRegex = Regex("""grip\s?([1-5])""")
    private val eenheidRegex = Regex("""\b(ambu|mmt|heli|ts|tas|hw|wo|hv|av|dv|red)[- ]?(\d{2}[- ]?\d{3,4})\b""")
    private val ritRegex = Regex("""\brit[:\s]?(\d{4,8})""")
    private val bonRegex = Regex("""\bbon[:\s]?(\d{4,8})""")
    private val diaRegex = Regex("""dia[:\s]?ja""")

    /** Aard van de melding, of null als er niets herkenbaars in de tekst staat. */
    fun aard(rawTitle: String, description: String): String? {
        val text = "${rawTitle.lowercase()} ${description.lowercase()}"
        gripRegex.find(text)?.let { return "GRIP ${it.groupValues[1]} – grootschalige opschaling" }
        for ((regex, label) in aardPatronen) {
            if (regex.containsMatchIn(text)) return label
        }
        return null
    }

    /** Opgeroepen eenheden (roepnummers) zoals "ambu 17-170". */
    fun eenheden(rawTitle: String): List<String> {
        return eenheidRegex.findAll(rawTitle.lowercase())
            .map { "${it.groupValues[1].uppercase()} ${it.groupValues[2]}" }
            .distinct()
            .toList()
    }

    /** Rit- of bonnummer van de meldkamer, indien aanwezig. */
    fun dossier(rawTitle: String): String? {
        val t = rawTitle.lowercase()
        ritRegex.find(t)?.let { return "Ritnummer ${it.groupValues[1]}" }
        bonRegex.find(t)?.let { return "Bonnummer ${it.groupValues[1]}" }
        return null
    }

    /** Directe inzet ambulance (DIA) gemeld? */
    fun directeInzet(rawTitle: String): Boolean = diaRegex.containsMatchIn(rawTitle.lowercase())

    /** Uitleg bij de prioriteitscode. */
    fun prioUitleg(prio: String?): String? = when (prio?.uppercase()) {
        "A0" -> "A0 – reanimatie, maximale spoed"
        "A1" -> "A1 – levensbedreigend, hoogste spoed (sirene en zwaailicht)"
        "A2" -> "A2 – spoed, geen directe levensbedreiging"
        "B", "B1", "B2" -> "B – gepland / besteld vervoer"
        "P1", "PRIO1" -> "Prio 1 – directe uitruk, hoogste prioriteit"
        "P2", "PRIO2" -> "Prio 2 – uitruk met lagere prioriteit"
        "P3", "PRIO3" -> "Prio 3 – geen spoed"
        else -> null
    }
}

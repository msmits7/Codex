package nl.mikesmits.p2000.data

import java.util.Date

enum class ServiceType(val label: String) {
    AMBULANCE("Ambulance"),
    BRANDWEER("Brandweer"),
    POLITIE("Politie"),
    TRAUMA("Traumaheli"),
    WATER("KNRM / Water"),
    POLITIEBERICHT("Politiebericht"),
    OVERIG("Overig");
}

data class Melding(
    val guid: String,
    val rawTitle: String,
    val description: String,
    val link: String,
    val time: Date,
    val type: ServiceType,
    val prio: String?,
    val province: String?,
    val region: String?,
    val city: String?,
    val street: String?,
    val postcode: String?,
    val aard: String? = null,
    val eenheden: List<String> = emptyList(),
    val dossier: String? = null,
    val directeInzet: Boolean = false,
    /** Waar deze melding vandaan komt; zichtbaar in het detailscherm. */
    val bron: String = "alarmeringen.nl",
    var lat: Double? = null,
    var lon: Double? = null,
    /** Gemeente waarin de melding valt; ingevuld tijdens het geocoderen en
     *  gebruikt om cijfers bij data.politie.nl op te halen. */
    var gemeenteCode: String? = null,
    var gemeenteNaam: String? = null,
    /** True zodra lat/lon van een adres of straat komen (en niet van een plaats). */
    var exacteLocatie: Boolean = false,
    /** True als de omhullende van de regio komt en niet van de plaats zelf. */
    var grofGebied: Boolean = false,
    /** Omhullende van de plaats/gemeente als er geen exact adres bekend is;
     *  daarmee telt een gebied mee zodra het deels binnen de straal ligt. */
    var extent: Bbox? = null
) {

    /**
     * Afstand in meters tot een punt. Bij een gebied (plaats of gemeente
     * zonder exact adres) is dat de afstand tot de rand van dat gebied, dus 0
     * als het punt er middenin ligt.
     */
    fun distanceMetersFrom(lat: Double, lon: Double): Double? {
        // Exact adres bekend: dat is het nauwkeurigst.
        if (exacteLocatie) {
            val mLat = this.lat
            val mLon = this.lon
            if (mLat != null && mLon != null) return GeoUtils.distance(lat, lon, mLat, mLon)
        }
        // Anders de omhullende van de plaats/gemeente; die is er vaak al
        // voordat het exacte adres is opgezocht.
        extent?.let { return GeoUtils.distanceToBox(lat, lon, it) }
        val mLat = this.lat ?: return null
        val mLon = this.lon ?: return null
        return GeoUtils.distance(lat, lon, mLat, mLon)
    }
    /** Rit-/bonnummer uit de meldingstekst, om dubbele bronnen te herkennen. */
    val ritNummer: String?
        get() = ritRegex.find(rawTitle)?.groupValues?.get(1)

    /** Best available textual key for geocoding, or null if nothing usable. */
    val geoQuery: String?
        get() = when {
            postcode != null && city != null -> "$postcode $city"
            postcode != null -> postcode
            street != null && city != null -> "$street, $city"
            city != null -> city
            else -> null
        }

    val locationLabel: String
        get() = listOfNotNull(street, city).joinToString(", ").ifEmpty { region ?: province ?: "Onbekend" }

    /**
     * Sleutel om meldingen van verschillende diensten voor hetzelfde incident
     * te bundelen. Null = niet te bundelen (te weinig locatie-informatie).
     */
    val groupKey: String?
        get() = when {
            // Politieberichten gaan over losse zaken; die nooit samenvoegen
            type == ServiceType.POLITIEBERICHT -> null
            street != null && city != null -> "s|${norm(street)}|${norm(city)}"
            postcode != null -> "p|$postcode"
            city != null && aard != null -> "a|${norm(city)}|${norm(aard)}"
            else -> null
        }

    /** Losse leestekens en spaties wegstrepen zodat diensten die dezelfde
     *  straat net anders schrijven ("H. de Lintweg" / "H de Lintweg") toch
     *  op hetzelfde incident uitkomen. */
    private fun norm(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}

private val ritRegex = Regex("\\b(?:rit|bon)[:\\s]+(\\d{4,8})", RegexOption.IGNORE_CASE)

/** Eén incident: alle meldingen (mogelijk van meerdere diensten) gebundeld. */
data class MeldingGroep(val meldingen: List<Melding>) {
    val primary: Melding get() = meldingen.first()
    val types: List<ServiceType> get() = meldingen.map { it.type }.distinct()
    val aard: String? get() = meldingen.firstNotNullOfOrNull { it.aard }
    val prio: String? get() = meldingen.firstNotNullOfOrNull { it.prio }
    val eenheden: List<String> get() = meldingen.flatMap { it.eenheden }.distinct()
    val dossier: String? get() = meldingen.firstNotNullOfOrNull { it.dossier }
    val directeInzet: Boolean get() = meldingen.any { it.directeInzet }
    val lat: Double? get() = meldingen.firstNotNullOfOrNull { it.lat }
    val lon: Double? get() = meldingen.firstNotNullOfOrNull { it.lon }
}

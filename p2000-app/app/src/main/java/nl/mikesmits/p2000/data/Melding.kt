package nl.mikesmits.p2000.data

import java.util.Date

enum class ServiceType(val label: String) {
    AMBULANCE("Ambulance"),
    BRANDWEER("Brandweer"),
    POLITIE("Politie"),
    TRAUMA("Traumaheli"),
    WATER("KNRM / Water"),
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
    var lat: Double? = null,
    var lon: Double? = null
) {
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
            street != null && city != null -> "s|${street.lowercase()}|${city.lowercase()}"
            postcode != null -> "p|$postcode"
            city != null && aard != null -> "a|${city.lowercase()}|${aard.lowercase()}"
            else -> null
        }
}

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

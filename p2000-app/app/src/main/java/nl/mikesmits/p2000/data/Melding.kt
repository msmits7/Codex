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
}

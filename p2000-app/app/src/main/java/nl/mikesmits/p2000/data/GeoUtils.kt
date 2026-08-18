package nl.mikesmits.p2000.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.PI

/** Rechthoekige omhullende van een plaats of gemeente (WGS84). */
data class Bbox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

object GeoUtils {

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * Afstand in meters van een punt tot een rechthoek: 0 als het punt erbinnen
     * ligt, anders de kortste afstand tot de rand. Zo telt een gemeente mee
     * zodra die ook maar deels binnen de straal valt.
     */
    fun distanceToBox(lat: Double, lon: Double, box: Bbox): Double {
        val dLat = max(max(box.minLat - lat, lat - box.maxLat), 0.0)
        val dLon = max(max(box.minLon - lon, lon - box.maxLon), 0.0)
        val metersPerDegreeLon = METERS_PER_DEGREE_LAT * cos(lat * PI / 180.0)
        return hypot(dLat * METERS_PER_DEGREE_LAT, dLon * abs(metersPerDegreeLon))
    }

    /** Afstand in meters tussen twee punten (equirectangular; ruim nauwkeurig genoeg voor NL). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * METERS_PER_DEGREE_LAT
        val dLon = (lon2 - lon1) * METERS_PER_DEGREE_LAT * cos((lat1 + lat2) / 2 * PI / 180.0)
        return hypot(dLat, dLon)
    }

    /** Bounding box uit een WKT-(MULTI)POLYGON; null als er geen punten in staan. */
    fun bboxFromWkt(wkt: String): Bbox? {
        val pairs = Regex("""(-?\d+\.?\d*)\s+(-?\d+\.?\d*)""").findAll(wkt)
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        var count = 0
        for (p in pairs) {
            val lon = p.groupValues[1].toDoubleOrNull() ?: continue
            val lat = p.groupValues[2].toDoubleOrNull() ?: continue
            minLat = minOf(minLat, lat); maxLat = max(maxLat, lat)
            minLon = minOf(minLon, lon); maxLon = max(maxLon, lon)
            count++
        }
        return if (count == 0) null else Bbox(minLat, maxLat, minLon, maxLon)
    }
}

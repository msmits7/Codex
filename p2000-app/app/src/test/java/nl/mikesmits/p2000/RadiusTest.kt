package nl.mikesmits.p2000

import nl.mikesmits.p2000.data.Bbox
import nl.mikesmits.p2000.data.GeoUtils
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Het straalfilter moet ook werken voor meldingen zonder exact adres, zoals
 * politieberichten die alleen een gemeente noemen.
 */
class RadiusTest {

    /** Echte omhullende van gemeente Dronten (PDOK Locatieserver). */
    private val dronten = Bbox(minLat = 52.3641, maxLat = 52.6636, minLon = 5.5113, maxLon = 5.8645)

    private fun politiebericht(extent: Bbox?) = Melding(
        guid = "x", rawTitle = "Dronten - Getuigen gezocht", description = "Getuigen gezocht",
        link = "", time = Date(), type = ServiceType.POLITIEBERICHT, prio = null,
        province = null, region = null, city = "Dronten", street = null, postcode = null,
        // middelpunt van de gemeente, zoals de geocoder dat teruggeeft
        lat = 52.51000993, lon = 5.69535334, extent = extent
    )

    @Test
    fun binnenDeGemeenteIsAfstandNul() {
        val meters = GeoUtils.distanceToBox(52.50, 5.70, dronten)
        assertEquals(0.0, meters, 0.001)
    }

    @Test
    fun gemeenteTeltMeeZodraDeRandBinnenDeStraalLigt() {
        // Punt ruim onder Dronten: middelpunt ligt ~22 km weg, de rand ~5,6 km
        val lat = 52.3141
        val lon = 5.70

        val zonderExtent = politiebericht(extent = null).distanceMetersFrom(lat, lon)!!
        val metExtent = politiebericht(extent = dronten).distanceMetersFrom(lat, lon)!!

        assertTrue("middelpunt ligt ver weg", zonderExtent > 20_000)
        assertTrue("rand van de gemeente is dichtbij", metExtent < 7_000)

        // Met een straal van 10 km hoort dit bericht er nu wél bij
        assertTrue(metExtent <= 10_000)
        assertTrue(zonderExtent > 10_000)
    }

    @Test
    fun buitenDeStraalBlijftBuitenDeStraal() {
        // Maastricht ligt ver van Dronten; ook de rand valt buiten 10 km
        val meters = politiebericht(extent = dronten).distanceMetersFrom(50.85, 5.69)!!
        assertTrue(meters > 100_000)
    }

    @Test
    fun bboxWordtUitWktGelezen() {
        val wkt = "MULTIPOLYGON(((5.62421 52.3651,5.64487 52.37086,5.65255 52.36543,5.62421 52.3651)))"
        val box = GeoUtils.bboxFromWkt(wkt)
        assertNotNull(box)
        assertEquals(52.3651, box!!.minLat, 0.00001)
        assertEquals(52.37086, box.maxLat, 0.00001)
        assertEquals(5.62421, box.minLon, 0.00001)
        assertEquals(5.65255, box.maxLon, 0.00001)
    }
}

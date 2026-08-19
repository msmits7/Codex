package nl.mikesmits.p2000

import kotlinx.coroutines.runBlocking
import nl.mikesmits.p2000.data.P2000OnlineParser
import nl.mikesmits.p2000.data.PlaatsCodes
import nl.mikesmits.p2000.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De feed van alarmeringen.nl bevat vrijwel alleen A1-meldingen. Deze tweede
 * bron moet juist ook besteld vervoer (B1/B2) en A2 opleveren.
 */
class P2000OnlineTest {

    private val html = """
        <table>
        <tr><td>18-08-2026 15:07:00</td><td>Ambulance</td><td>Haaglanden</td>
            <td>B1 De Savornin Lohmanplein SGRAVH : (medium care) 15232</td></tr>
        <tr><td>18-08-2026 15:06:00</td><td>Ambulance</td><td>Zeeland</td>
            <td>A2 Vlissingen rit: 146627</td></tr>
        <tr><td>18-08-2026 15:05:00</td><td>Brandweer</td><td>Brabant Noord</td>
            <td>TESTOPROEP MOB</td></tr>
        <tr><td>18-08-2026 15:04:00</td><td>Politie</td><td>Haaglanden</td>
            <td>Prio 1 Waldorpstraat SGRAVH Ongeval wegvervoer letsel</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun besteldVervoerWordtGelezen() {
        val rijen = P2000OnlineParser.parse(html)

        // De testoproep valt af, de rest blijft over
        assertEquals(3, rijen.size)

        val b = rijen[0]
        assertEquals("B1", b.prio)
        assertEquals("Haaglanden", b.regio)
        assertEquals(ServiceType.AMBULANCE, P2000OnlineParser.typeVoor(b.discipline, b.tekst))
        assertTrue(b.tekst.contains("De Savornin Lohmanplein"))
    }

    @Test
    fun prioriteitenEnDisciplinesKloppen() {
        val rijen = P2000OnlineParser.parse(html)
        assertEquals(listOf("B1", "A2", "PRIO1"), rijen.map { it.prio })
        assertEquals(
            listOf(ServiceType.AMBULANCE, ServiceType.AMBULANCE, ServiceType.POLITIE),
            rijen.map { P2000OnlineParser.typeVoor(it.discipline, it.tekst) }
        )
        assertEquals("146627", rijen[1].rit)
    }

    @Test
    fun plaatsafkortingWordtVertaald() = runBlocking {
        // SGRAVH is geen woonplaats bij de geocoder, maar staat in de tabel
        val naam = PlaatsCodes.resolve("SGRAVH") { null }
        assertEquals("Den Haag", naam)
    }

    @Test
    fun onbekendeCodeGeeftGeenGokLocatie() = runBlocking {
        // Niet in de tabel en geen echte woonplaats: liever geen plaats dan een
        // verkeerde speld op de kaart
        assertNull(PlaatsCodes.resolve("XQZWRT") { null })
    }

    @Test
    fun volledigeplaatsnaamWordtHerkendViaDeGeocoder() = runBlocking {
        val naam = PlaatsCodes.resolve("DELFT") { if (it == "DELFT") "Delft" to "Zuid-Holland" else null }
        assertEquals("Delft", naam)
    }

    @Test
    fun straatWordtUitDePagertekstGehaald() {
        val straat = PlaatsCodes.straatUit(
            "B1 De Savornin Lohmanplein SGRAVH : (medium care) 15232", "SGRAVH"
        )
        assertNotNull(straat)
        assertEquals("De Savornin Lohmanplein", straat)
    }
}

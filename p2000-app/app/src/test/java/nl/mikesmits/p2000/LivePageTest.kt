package nl.mikesmits.p2000

import kotlinx.coroutines.runBlocking
import nl.mikesmits.p2000.data.P2000OnlineParser
import nl.mikesmits.p2000.data.PlaatsCodes
import nl.mikesmits.p2000.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draait de echte parser over een opgeslagen kopie van de live monitorpagina,
 * zodat we zien wat de app werkelijk uit de bron haalt - inclusief de
 * doorgestuurde melding over besteld vervoer naar De Savornin Lohmanplein.
 */
class LivePageTest {

    private val html: String =
        javaClass.getResourceAsStream("/p2000online_live.html")!!
            .readBytes().toString(Charsets.ISO_8859_1)

    @Test
    fun deDoorgestuurdeBestelVervoerMeldingKomtEruit() = runBlocking {
        val rijen = P2000OnlineParser.parse(html)
        assertTrue("er moeten rijen geparseerd worden", rijen.size > 10)

        val savornin = rijen.firstOrNull { it.tekst.contains("Savornin", ignoreCase = true) }
        assertNotNull("de doorgestuurde melding moet gevonden worden", savornin)

        assertEquals("B2", savornin!!.prio)
        assertEquals(ServiceType.AMBULANCE, P2000OnlineParser.typeVoor(savornin.discipline, savornin.tekst))
        assertEquals("Haaglanden", savornin.regio)

        // Plaats en straat moeten eruit komen zodat hij ook op de kaart landt
        val treffer = PlaatsCodes.kandidaten(savornin.tekst)
            .firstNotNullOfOrNull { t -> PlaatsCodes.resolve(t) { null }?.let { t to it } }
        assertEquals("Den Haag", treffer?.second)
        assertEquals(
            "De Savornin Lohmanplein",
            PlaatsCodes.straatUit(savornin.tekst, treffer?.first)
                ?.substringAfter("Albert Heijn ")
        )
    }

    @Test
    fun deBronBevatMeerDanAlleenSpoedmeldingen() {
        val rijen = P2000OnlineParser.parse(html)
        val prios = rijen.mapNotNull { it.prio }.toSet()
        assertTrue("B-meldingen horen erin te zitten", prios.any { it.startsWith("B") })
        assertTrue("A2 hoort erin te zitten", prios.contains("A2"))
    }
}

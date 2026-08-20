package nl.mikesmits.p2000

import android.location.Location
import nl.mikesmits.p2000.data.Bbox
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.ui.FilterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Vanuit Den Haag met 20 km straal horen IJmuiden en Culemborg er niet bij te
 * staan. Zodra de plaats bekend is moet dat oordeel meteen te vellen zijn,
 * ook als het exacte adres nog niet is opgezocht. Omhullenden komen van de
 * PDOK Locatieserver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AfstandFilterTest {

    private val ijmuiden = Bbox(52.4348, 52.4737, 4.5175, 4.6332)
    private val culemborg = Bbox(51.9196, 51.9789, 5.1424, 5.2704)
    private val zoetermeer = Bbox(52.0313, 52.0933, 4.4152, 4.5577)
    private val delft = Bbox(51.9663, 52.0326, 4.3202, 4.4079)

    private val denHaag = Location("test").apply {
        latitude = 52.0705
        longitude = 4.3007
    }

    private fun melding(plaats: String, box: Bbox?, exact: Boolean = false) = Melding(
        guid = plaats, rawTitle = "a1 $plaats", description = "", link = "",
        time = Date(), type = ServiceType.AMBULANCE, prio = "A1",
        province = null, region = null, city = plaats, street = "Teststraat",
        postcode = null
    ).also { it.extent = box; it.exacteLocatie = exact }

    private val filter = FilterState(radiusKm = 20, myLocation = denHaag)

    @Test
    fun verWegGelegenPlaatsenVallenAf() {
        assertFalse("IJmuiden hoort niet in een straal van 20 km rond Den Haag",
            filter.withinRadius(melding("IJmuiden", ijmuiden)))
        assertFalse("Culemborg hoort niet in een straal van 20 km rond Den Haag",
            filter.withinRadius(melding("Culemborg", culemborg)))
    }

    @Test
    fun plaatsenInDeBuurtBlijvenStaan() {
        assertTrue(filter.withinRadius(melding("Zoetermeer", zoetermeer)))
        assertTrue(filter.withinRadius(melding("Delft", delft)))
    }

    @Test
    fun hetOordeelKanValZodraDePlaatsBekendIs() {
        // Geen exacte positie, alleen de plaats: dat is genoeg om te filteren
        val zonderAdres = melding("IJmuiden", ijmuiden, exact = false)
        assertFalse(filter.withinRadius(zonderAdres))
    }

    @Test
    fun eenOnduidelijkeLocatieHoortNietInDeBuurtlijst() {
        // Zonder bruikbare locatie valt er niets over de afstand te zeggen; die
        // meldingen staan voortaan op het tabblad "Alles" in plaats van tussen
        // de meldingen in de buurt.
        val onbekend = melding("Onbekend", null)
        assertFalse(filter.withinRadius(onbekend))
        assertFalse(filter.heeftDuidelijkeLocatie(onbekend))
    }

    @Test
    fun alleenOpRegioGeplaatstTeltOokAlsOnduidelijk() {
        val opRegio = melding("Ergens", zoetermeer).also { it.grofGebied = true }
        assertFalse(filter.heeftDuidelijkeLocatie(opRegio))
        assertFalse(filter.withinRadius(opRegio))
    }

    @Test
    fun eenPlaatsInDeBuurtGeldtWelAlsDuidelijk() {
        val opPlaats = melding("Zoetermeer", zoetermeer)
        assertTrue(filter.heeftDuidelijkeLocatie(opPlaats))
        assertTrue(filter.withinRadius(opPlaats))
    }
}

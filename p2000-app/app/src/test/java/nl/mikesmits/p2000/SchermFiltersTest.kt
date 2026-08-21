package nl.mikesmits.p2000

import androidx.test.core.app.ApplicationProvider
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.ui.FilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Het Alles-scherm heeft een eigen filterset, los van "In de buurt" en de
 * kaart, en werkt standaard zonder afstandsfilter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchermFiltersTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val buurt = Prefs(context)
    private val alles = Prefs(context, prefix = "alles_")

    @Test
    fun beideSchermenBewarenHunFiltersApart() {
        buurt.radiusKm = 10
        alles.radiusKm = 0
        assertEquals(10, buurt.radiusKm)
        assertEquals(0, alles.radiusKm)

        buurt.windowMinutes = 180
        alles.windowMinutes = 10080
        assertEquals(180, buurt.windowMinutes)
        assertEquals(10080, alles.windowMinutes)

        buurt.filterTypes = setOf(ServiceType.BRANDWEER)
        alles.filterTypes = setOf(ServiceType.POLITIEBERICHT)
        assertEquals(setOf(ServiceType.BRANDWEER), buurt.filterTypes)
        assertEquals(setOf(ServiceType.POLITIEBERICHT), alles.filterTypes)
    }

    @Test
    fun standaardStaatErGeenAfstandsfilter() {
        assertEquals(0, Prefs(context, prefix = "vers_").radiusKm)
    }

    @Test
    fun straalNulBetekentGeenAfstandsfilter() {
        val zonderStraal = FilterState(radiusKm = 0)
        assertTrue(!zonderStraal.radiusActive)
        // Zonder afstandsfilter doet ook een melding zonder locatie gewoon mee
        assertTrue(zonderStraal.withinRadius(RadiusTestData.zonderLocatie()))
    }

    @Test
    fun themaEnAchtergrondServiceBlijvenAppbreed() {
        buurt.themeMode = 2
        assertEquals(2, alles.themeMode)
        buurt.backgroundEnabled = true
        assertTrue(alles.backgroundEnabled)
    }
}

/** Kleine hulp om een melding zonder locatie te maken. */
object RadiusTestData {
    fun zonderLocatie() = nl.mikesmits.p2000.data.Melding(
        guid = "x", rawTitle = "test", description = "", link = "",
        time = java.util.Date(), type = ServiceType.OVERIG, prio = null,
        province = null, region = null, city = null, street = null, postcode = null
    )
}

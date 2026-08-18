package nl.mikesmits.p2000

import android.location.Location
import nl.mikesmits.p2000.data.Bbox
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.data.Veiligheidsregios
import nl.mikesmits.p2000.ui.FilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Meldingen waarvan de plaatsnaam niet te herleiden is, moeten via de
 * veiligheidsregio alsnog grofweg te plaatsen zijn - anders blijven ze in beeld
 * terwijl ze aan de andere kant van het land liggen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegioTest {

    /** Werkelijke omhullende van provincie Overijssel (PDOK Locatieserver). */
    private val overijssel = Bbox(52.1181, 52.8542, 5.7779, 7.0728)

    @Test
    fun regiosVanDeBronWordenAanEenProvincieGekoppeld() {
        // Precies zoals p2000-online.net ze schrijft
        assertEquals("Overijssel", Veiligheidsregios.provincie("Twente"))
        assertEquals("Overijssel", Veiligheidsregios.provincie("IJsselland"))
        assertEquals("Zuid-Holland", Veiligheidsregios.provincie("Haaglanden"))
        assertEquals("Zuid-Holland", Veiligheidsregios.provincie("Rotterdam-Rijnmond"))
        assertEquals("Zuid-Holland", Veiligheidsregios.provincie("Zuid-Holland Zuid"))
        assertEquals("Noord-Holland", Veiligheidsregios.provincie("Amsterdam-Amstelland"))
        assertEquals("Noord-Holland", Veiligheidsregios.provincie("Kennemerland"))
        assertEquals("Noord-Brabant", Veiligheidsregios.provincie("Midden- en West Brabant"))
        assertEquals("Noord-Brabant", Veiligheidsregios.provincie("Brabant Noord"))
        assertEquals("Limburg", Veiligheidsregios.provincie("Limburg-Zuid"))
        assertEquals("Gelderland", Veiligheidsregios.provincie("Noord- en Oost Gelderland"))
        assertEquals("Groningen", Veiligheidsregios.provincie("Groningen"))
        assertEquals("Zeeland", Veiligheidsregios.provincie("Zeeland"))
        assertEquals("Utrecht", Veiligheidsregios.provincie("Utrecht"))
    }

    @Test
    fun eenProvincienaamWordtOokHerkend() {
        // alarmeringen.nl levert de provincie, niet de veiligheidsregio
        assertNotNull(Veiligheidsregios.provincie("Zuid-Holland"))
        assertEquals("Overijssel", Veiligheidsregios.provincie("Overijssel"))
    }

    @Test
    fun twenteValtBuitenTwintigKilometerVanafDenHaag() {
        val melding = Melding(
            guid = "t", rawTitle = "A2 Onbekende Straat XYZ 12345", description = "",
            link = "", time = Date(), type = ServiceType.AMBULANCE, prio = "A2",
            province = null, region = "Twente", city = null, street = null, postcode = null
        ).also { it.extent = overijssel }

        val denHaag = Location("test").apply { latitude = 52.0705; longitude = 4.3007 }
        val filter = FilterState(radiusKm = 20, myLocation = denHaag)

        assertFalse("een melding uit Twente hoort niet in beeld", filter.withinRadius(melding))
    }
}

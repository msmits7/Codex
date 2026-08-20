package nl.mikesmits.p2000

import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.ui.FilterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** Het tabblad "Alles" heeft een vrij zoekveld over alle velden van een melding. */
class ZoekAllesTest {

    private val melding = Melding(
        guid = "1",
        rawTitle = "B2 AMBU 17206 Kleiweg 3045PM Rotterdam ROTTDM bon 129219",
        description = "Ambulance naar Kleiweg in Rotterdam",
        link = "", time = Date(), type = ServiceType.AMBULANCE, prio = "B2",
        province = "Zuid-Holland", region = "Rotterdam-Rijnmond", city = "Rotterdam",
        street = "Kleiweg", postcode = "3045PM", aard = null,
        bron = "p2000-online.net"
    )

    private fun zoek(term: String) = FilterState(zoekAlles = term).matchesZoek(melding)

    @Test
    fun leegZoekveldToontAlles() {
        assertTrue(zoek(""))
    }

    @Test
    fun zoekenOpPlaatsStraatEnPostcode() {
        assertTrue(zoek("rotterdam"))
        assertTrue(zoek("kleiweg"))
        assertTrue(zoek("3045PM"))
    }

    @Test
    fun zoekenOpDienstPrioRegioEnBron() {
        assertTrue(zoek("ambulance"))
        assertTrue(zoek("b2"))
        assertTrue(zoek("rijnmond"))
        assertTrue(zoek("p2000-online"))
    }

    @Test
    fun zoekenOpRuweTekstEnBonnummer() {
        assertTrue(zoek("17206"))
        assertTrue(zoek("129219"))
    }

    @Test
    fun meerdereWoordenMoetenAllemaalVoorkomen() {
        assertTrue(zoek("rotterdam kleiweg"))
        assertFalse(zoek("rotterdam amsterdam"))
    }

    @Test
    fun watErNietInStaatKomtNietTerug() {
        assertFalse(zoek("brandweer"))
        assertFalse(zoek("groningen"))
    }
}

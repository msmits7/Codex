package nl.mikesmits.p2000

import kotlinx.coroutines.runBlocking
import nl.mikesmits.p2000.data.PlaatsCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De landelijke monitorpagina schrijft plaatsen gewoon uit ("A2 Eindhoven Rit:
 * 98623"); alleen op hoofdletterafkortingen zoeken liet vrijwel elke melding
 * zonder plaats achter, waardoor het straalfilter er niets mee kon.
 */
class PlaatsHerkenningTest {

    @Test
    fun plaatsnamenInGewoneSchrijfwijzeWordenGevonden() {
        val teksten = listOf(
            "A2 Eindhoven Rit: 98623" to "Eindhoven",
            "A1 Lelystad 135458" to "Lelystad",
            "B2 Naarden 135461" to "Naarden",
            "A2 Ambu 08130 VWS Waardenburg Rit 255771" to "Waardenburg",
            "Ongeval Letsel Koedijkerstraat Alkmaar" to "Alkmaar",
            "A2 13123 Oostelijke Handelskade 1019 Amsterdam 79809" to "Amsterdam"
        )
        for ((tekst, plaats) in teksten) {
            assertTrue(
                "'$plaats' hoort een kandidaat te zijn in: $tekst",
                PlaatsCodes.kandidaten(tekst).contains(plaats)
            )
        }
    }

    @Test
    fun hoofdletterafkortingenBlijvenWerken() = runBlocking {
        val kandidaten = PlaatsCodes.kandidaten("A1 Duinweg SGRAVH : 15110")
        assertTrue(kandidaten.contains("SGRAVH"))
        assertEquals("Den Haag", PlaatsCodes.resolve("SGRAVH") { null })
    }

    @Test
    fun eenPlaatsInEenAndereProvincieWordtVerworpen() = runBlocking {
        // Straatnaam die toevallig ook een dorp is, maar ver van de regio:
        // dan liever geen plaats dan een melding op 100 km afstand.
        val gevonden = PlaatsCodes.resolve("Bergen", "Zuid-Holland") {
            "Bergen" to "Limburg"
        }
        assertNull(gevonden)
    }

    @Test
    fun eenPlaatsInDeJuisteProvincieWordtGeaccepteerd() = runBlocking {
        val gevonden = PlaatsCodes.resolve("Alkmaar", "Noord-Holland") {
            "Alkmaar" to "Noord-Holland"
        }
        assertEquals("Alkmaar", gevonden)
    }

    @Test
    fun alleenDeLaatsteTokensWordenBekeken() {
        // De plaats staat achteraan; dat scheelt opzoekwerk en voorkomt dat een
        // ziekenhuisnaam vooraan als plaats wordt gelezen.
        val kandidaten = PlaatsCodes.kandidaten(
            "A1 Reinier de Graaf Gasthuis Afdeling Spoedeisende Hulp Reinier de Graafweg Delft"
        )
        assertTrue(kandidaten.size <= 4)
        assertEquals("Delft", kandidaten.first())
    }
}

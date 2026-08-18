package nl.mikesmits.p2000

import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.ui.FilterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aanvinken van een categorie betekent "alleen dit tonen", niet "dit verbergen". */
class FilterSelectionTest {

    @Test
    fun nietsAangevinktToontAlles() {
        val f = FilterState()
        assertTrue(ServiceType.values().all { f.matchesType(it) })
    }

    @Test
    fun eenTypeAangevinktToontAlleenDatType() {
        val f = FilterState(types = setOf(ServiceType.BRANDWEER))
        assertTrue(f.matchesType(ServiceType.BRANDWEER))
        assertFalse(f.matchesType(ServiceType.AMBULANCE))
        assertFalse(f.matchesType(ServiceType.POLITIE))
    }

    @Test
    fun meerdereTypesTegelijkBlijftMogelijk() {
        val f = FilterState(types = setOf(ServiceType.AMBULANCE, ServiceType.BRANDWEER))
        assertTrue(f.matchesType(ServiceType.AMBULANCE))
        assertTrue(f.matchesType(ServiceType.BRANDWEER))
        assertFalse(f.matchesType(ServiceType.TRAUMA))
    }

    @Test
    fun politieberichtIsNietLangerAfhankelijkVanOudeOpslag() {
        // Zonder selectie hoort ook het nieuwe type gewoon zichtbaar te zijn
        assertTrue(FilterState().matchesType(ServiceType.POLITIEBERICHT))
    }
}

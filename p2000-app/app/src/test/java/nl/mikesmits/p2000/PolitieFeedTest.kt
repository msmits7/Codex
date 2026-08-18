package nl.mikesmits.p2000

import nl.mikesmits.p2000.data.PolitieFeedParser
import nl.mikesmits.p2000.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolitieFeedTest {

    private val feed = """<?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0"><channel>
          <item>
            <title>Dronten - Motorrijder overleden bij verkeersongeval N307 Dronten</title>
            <description>Zondag vond een verkeersongeval plaats op de N307.</description>
            <link>https://www.politie.nl/gezocht/getuigenoproep/2026/augustus/03-motorrijder.html</link>
            <guid>pol-1</guid>
            <pubDate>Mon, 17 Aug 2026 10:04:00 GMT</pubDate>
          </item>
          <item>
            <title>Deze zaak is niet meer actueel.</title>
            <description>-</description>
            <link>https://www.politie.nl/vermist/x.html</link>
            <guid>pol-2</guid>
            <pubDate>Fri, 14 Aug 2026 12:00:06 GMT</pubDate>
          </item>
        </channel></rss>"""

    @Test
    fun parseertPlaatsEnOnderwerpUitDeTitel() {
        val items = PolitieFeedParser.parse(feed.byteInputStream(), "Getuigenoproep / opsporing")

        // Het afgesloten bericht wordt overgeslagen
        assertEquals(1, items.size)
        val m = items[0]
        assertEquals(ServiceType.POLITIEBERICHT, m.type)
        assertEquals("Dronten", m.city)
        assertEquals("Motorrijder overleden bij verkeersongeval N307 Dronten", m.description)
        assertEquals("Getuigenoproep / opsporing", m.aard)
        // Plaatsnaam is genoeg om te geocoderen en op de kaart te tonen
        assertTrue(m.geoQuery == "Dronten")
    }

    @Test
    fun politieberichtenWordenNietGebundeld() {
        val items = PolitieFeedParser.parse(feed.byteInputStream(), "Vermist persoon")
        assertNull(items[0].groupKey)
    }
}

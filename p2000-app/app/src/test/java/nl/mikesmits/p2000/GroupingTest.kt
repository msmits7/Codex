package nl.mikesmits.p2000

import nl.mikesmits.p2000.data.FeedParser
import nl.mikesmits.p2000.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Controleert dat meldingen van verschillende diensten op hetzelfde adres
 *  dezelfde groepssleutel krijgen en dus gebundeld worden getoond. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupingTest {

    private fun feed(vararg items: Pair<String, String>): String {
        val body = items.joinToString("") { (title, desc) ->
            """<item><title>$title</title><description>$desc</description>
               <link>http://alarmeringen.nl/zuid-holland/haaglanden/den-haag/1/x.html</link>
               <guid isPermaLink="false">${title.hashCode()}</guid>
               <pubDate>Tue, 18 Aug 2026 15:00:42 +0000</pubDate></item>"""
        }
        return """<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel>$body</channel></rss>"""
    }

    @Test
    fun ambulanceEnBrandweerOpZelfdeAdresDelenGroupKey() {
        val meldingen = FeedParser.parse(
            feed(
                "a1 oude haagweg sgravh : 15112" to "Ambulance met spoed naar Oude Haagweg in Den Haag",
                "p 1 br woning oude haagweg sgravh 15113" to "Brandweer naar Oude Haagweg in Den Haag"
            ).byteInputStream()
        )

        assertEquals(2, meldingen.size)
        assertEquals(ServiceType.AMBULANCE, meldingen[0].type)
        assertEquals(ServiceType.BRANDWEER, meldingen[1].type)
        assertNotNull(meldingen[0].groupKey)
        assertEquals(meldingen[0].groupKey, meldingen[1].groupKey)
    }

    @Test
    fun leestekensInStraatnaamBrekenDeBundelingNiet() {
        val meldingen = FeedParser.parse(
            feed(
                "a1 ambu 17170 h. de lintweg spijkenisse" to "Ambulance met spoed naar H. de Lintweg in Spijkenisse",
                "p 1 brw h de lintweg spijkenisse" to "Brandweer naar H de Lintweg in Spijkenisse"
            ).byteInputStream()
        )

        assertEquals(meldingen[0].groupKey, meldingen[1].groupKey)
    }
}

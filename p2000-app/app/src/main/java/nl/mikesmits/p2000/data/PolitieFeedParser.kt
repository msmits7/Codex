package nl.mikesmits.p2000.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser voor de publieke RSS-feeds van rss.politie.nl: getuigenoproepen,
 * opsporingsberichten, vermiste personen en politienieuws. Dit zijn dezelfde
 * soort berichten die via Burgernet worden uitgezonden; Burgernet zelf heeft
 * geen publieke API (hun endpoint vereist authenticatie vanuit de eigen app).
 *
 * Titelformaat: "Dronten - Motorrijder overleden bij verkeersongeval N307".
 */
object PolitieFeedParser {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    /** Berichten die alleen melden dat een zaak is afgesloten. */
    private val vervallenRegex = Regex("""niet meer actueel|zaak is opgelost""", RegexOption.IGNORE_CASE)

    fun parse(stream: InputStream, aard: String): List<Melding> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        val items = mutableListOf<Melding>()
        var title = ""; var desc = ""; var link = ""; var guid = ""; var pubDate = ""
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> { inItem = true; title = ""; desc = ""; link = ""; guid = ""; pubDate = "" }
                    "title" -> if (inItem) title = parser.nextText()
                    "description" -> if (inItem) desc = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "guid" -> if (inItem) guid = parser.nextText()
                    "pubDate" -> if (inItem) pubDate = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    toMelding(title, desc, link, guid, pubDate, aard)?.let { items.add(it) }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun toMelding(
        title: String,
        desc: String,
        link: String,
        guid: String,
        pubDate: String,
        aard: String
    ): Melding? {
        if (title.isBlank() || vervallenRegex.containsMatchIn(title)) return null
        val time = try { dateFormat.parse(pubDate) ?: Date() } catch (_: Exception) { Date() }

        // "Plaats - Onderwerp" → plaats en onderwerp los
        val dashIndex = title.indexOf(" - ")
        val city = if (dashIndex > 0) title.substring(0, dashIndex).trim() else null
        val subject = if (dashIndex > 0) title.substring(dashIndex + 3).trim() else title.trim()

        return Melding(
            guid = guid.ifEmpty { link },
            rawTitle = title.trim(),
            description = subject,
            link = link.trim(),
            time = time,
            type = ServiceType.POLITIEBERICHT,
            prio = null,
            province = null,
            region = null,
            city = city,
            street = null,
            postcode = null,
            aard = aard,
            eenheden = emptyList(),
            dossier = null,
            directeInzet = false
        )
    }
}

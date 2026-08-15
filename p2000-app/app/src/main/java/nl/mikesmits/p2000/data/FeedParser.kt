package nl.mikesmits.p2000.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parses the alarmeringen.nl RSS feed (all P2000 pager messages nationwide).
 *
 * Item example:
 *  title: "a1 ambu 17170 h. de lintweg 3201ek spijkenisse spijkn bon 126521"
 *  description: "Ambulance met spoed naar H. de Lintweg in Spijkenisse"
 *  link: "http://alarmeringen.nl/zuid-holland/rotterdam-rijnmond/spijkenisse/55700833/p2000-....html?utm..."
 */
object FeedParser {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    private val postcodeRegex = Regex("""\b(\d{4}\s?[a-zA-Z]{2})\b""")
    private val prioRegex = Regex("""^\s*(a1|a2|b1|b2|p1|p2|p 1|p 2|prio\s?\d)""", RegexOption.IGNORE_CASE)

    fun parse(stream: InputStream): List<Melding> {
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
                    items.add(toMelding(title, desc, link, guid, pubDate))
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun toMelding(title: String, desc: String, link: String, guid: String, pubDate: String): Melding {
        val time = try { dateFormat.parse(pubDate) ?: Date() } catch (_: Exception) { Date() }
        val (province, region, city) = parseLinkPath(link)
        val postcode = postcodeRegex.find(title)?.groupValues?.get(1)?.uppercase()?.replace(" ", "")
        val (street, descCity) = parseDescription(desc)
        return Melding(
            guid = guid.ifEmpty { link },
            rawTitle = title.trim(),
            description = desc.trim(),
            link = link.substringBefore("?utm"),
            time = time,
            type = classify(title, desc),
            prio = prioRegex.find(title)?.groupValues?.get(1)?.uppercase()?.replace(" ", ""),
            province = province,
            region = region,
            city = descCity ?: city,
            street = street,
            postcode = postcode
        )
    }

    /** Link path: /provincie[/regio]/plaats/<id>/slug.html */
    private fun parseLinkPath(link: String): Triple<String?, String?, String?> {
        return try {
            val path = link.substringAfter("//").substringAfter("/").substringBefore("?")
            val segments = path.split("/").filter { it.isNotEmpty() }
            val idIndex = segments.indexOfFirst { it.all { c -> c.isDigit() } }
            if (idIndex < 1) return Triple(null, null, null)
            val locSegments = segments.subList(0, idIndex)
            val pretty = { s: String -> s.split("-").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } } }
            when (locSegments.size) {
                1 -> Triple(pretty(locSegments[0]), null, null)
                2 -> Triple(pretty(locSegments[0]), null, pretty(locSegments[1]))
                else -> Triple(pretty(locSegments[0]), pretty(locSegments[1]), pretty(locSegments.last()))
            }
        } catch (_: Exception) {
            Triple(null, null, null)
        }
    }

    /** Description pattern: "... naar <straat> in <plaats>" or "... naar <plaats>". */
    private fun parseDescription(desc: String): Pair<String?, String?> {
        val afterNaar = desc.substringAfter(" naar ", "").trim()
        if (afterNaar.isEmpty()) return Pair(null, null)
        val inIndex = afterNaar.lastIndexOf(" in ")
        return if (inIndex > 0) {
            Pair(afterNaar.substring(0, inIndex).trim(), afterNaar.substring(inIndex + 4).trim())
        } else {
            Pair(null, afterNaar)
        }
    }

    private fun classify(title: String, desc: String): ServiceType {
        val d = desc.lowercase()
        val t = " ${title.lowercase()} "
        return when {
            d.startsWith("traumaheli") || d.contains("lifeliner") || t.contains(" heli ") -> ServiceType.TRAUMA
            d.startsWith("ambulance") || t.contains(" ambu ") || t.contains(" mka ") -> ServiceType.AMBULANCE
            d.startsWith("brandweer") || t.contains(" brw ") || d.contains("brandweer") -> ServiceType.BRANDWEER
            d.startsWith("politie") || t.contains(" pol ") || d.contains("politie") -> ServiceType.POLITIE
            d.contains("knrm") || d.contains("reddingsbrigade") || d.contains("kustwacht") ||
                t.contains("knrm") || t.contains("kustwacht") -> ServiceType.WATER
            else -> ServiceType.OVERIG
        }
    }
}

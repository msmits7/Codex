package nl.mikesmits.p2000.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser voor de openbare monitorpagina van p2000-online.net. Anders dan de
 * feed van alarmeringen.nl - die vrijwel alleen A1-spoedmeldingen bevat -
 * staan hier alle prioriteiten in, inclusief A2 en besteld vervoer (B1/B2),
 * en staan discipline en veiligheidsregio in aparte kolommen.
 *
 * Rij: <td>18-08-2026 21:11:44</td><td>Ambulance</td><td>Zeeland</td><td>A1 Goes rit: 146633</td>
 */
object P2000OnlineParser {

    private val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cellRegex = Regex("""<td[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tagRegex = Regex("""<[^>]+>""")
    private val timeRegex = Regex("""^\d{2}-\d{2}-\d{4} \d{2}:\d{2}:\d{2}$""")
    private val prioRegex = Regex("""^\s*(a0|a1|a2|b1|b2|p ?[1-3]|prio ?[1-3])\b""", RegexOption.IGNORE_CASE)
    private val ritRegex = Regex("""\b(?:rit|bon)[:\s]+(\d{4,8})""", RegexOption.IGNORE_CASE)

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

    fun parse(html: String): List<RuweMelding> {
        val result = mutableListOf<RuweMelding>()
        for (row in rowRegex.findAll(html)) {
            val cells = cellRegex.findAll(row.groupValues[1])
                .map { schoon(it.groupValues[1]) }
                .filter { it.isNotEmpty() && it != ">>" }
                .toList()
            if (cells.size < 4 || !timeRegex.matches(cells[0])) continue

            val time = runCatching { dateFormat.parse(cells[0]) }.getOrNull() ?: continue
            val tekst = cells[3]
            if (tekst.contains("TESTOPROEP", ignoreCase = true)) continue

            result.add(
                RuweMelding(
                    time = time,
                    discipline = cells[1],
                    regio = cells[2],
                    tekst = tekst,
                    prio = prioRegex.find(tekst)?.groupValues?.get(1)
                        ?.uppercase()?.replace(" ", ""),
                    rit = ritRegex.find(tekst)?.groupValues?.get(1)
                )
            )
        }
        return result
    }

    private fun schoon(raw: String): String = tagRegex.replace(raw, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun typeVoor(discipline: String, tekst: String): ServiceType {
        val d = discipline.lowercase()
        val t = tekst.lowercase()
        return when {
            d.startsWith("lifeliner") || d.contains("heli") ||
                t.contains("mmt") || t.contains("lifeliner") -> ServiceType.TRAUMA
            d.contains("knrm") || d.contains("kustwacht") ||
                d.contains("reddingsbrigade") -> ServiceType.WATER
            d.startsWith("ambulance") -> ServiceType.AMBULANCE
            d.startsWith("brandweer") -> ServiceType.BRANDWEER
            d.startsWith("politie") -> ServiceType.POLITIE
            else -> ServiceType.OVERIG
        }
    }
}

/** Ruwe regel van de monitorpagina, vóór het opzoeken van de plaats. */
data class RuweMelding(
    val time: Date,
    val discipline: String,
    val regio: String,
    val tekst: String,
    val prio: String?,
    val rit: String?
)

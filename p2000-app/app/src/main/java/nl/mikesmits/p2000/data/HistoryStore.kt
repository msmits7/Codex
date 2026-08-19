package nl.mikesmits.p2000.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/** Bewaart de meldinghistorie op schijf zodat die een herstart overleeft. */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.jsonl")
    private val tmpFile = File(context.filesDir, "history.jsonl.tmp")

    fun load(): List<Melding> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { line ->
                runCatching { fromJson(JSONObject(line)) }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<Melding>) {
        try {
            tmpFile.bufferedWriter().use { w ->
                for (m in items) {
                    w.write(toJson(m).toString())
                    w.newLine()
                }
            }
            tmpFile.renameTo(file)
        } catch (_: Exception) {
            // opslag is best-effort; volgende poging overschrijft
        }
    }

    private fun toJson(m: Melding): JSONObject = JSONObject().apply {
        put("guid", m.guid)
        put("rawTitle", m.rawTitle)
        put("description", m.description)
        put("link", m.link)
        put("time", m.time.time)
        put("type", m.type.name)
        put("prio", m.prio ?: JSONObject.NULL)
        put("province", m.province ?: JSONObject.NULL)
        put("region", m.region ?: JSONObject.NULL)
        put("city", m.city ?: JSONObject.NULL)
        put("street", m.street ?: JSONObject.NULL)
        put("postcode", m.postcode ?: JSONObject.NULL)
        put("aard", m.aard ?: JSONObject.NULL)
        put("eenheden", JSONArray(m.eenheden))
        put("dossier", m.dossier ?: JSONObject.NULL)
        put("dia", m.directeInzet)
        put("bron", m.bron)
        put("lat", m.lat ?: JSONObject.NULL)
        put("lon", m.lon ?: JSONObject.NULL)
        put("gemeenteCode", m.gemeenteCode ?: JSONObject.NULL)
        put("gemeenteNaam", m.gemeenteNaam ?: JSONObject.NULL)
        put("exact", m.exacteLocatie)
        put("grof", m.grofGebied)
        put("extent", m.extent?.let { JSONArray(listOf(it.minLat, it.maxLat, it.minLon, it.maxLon)) }
            ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject): Melding = Melding(
        guid = o.getString("guid"),
        rawTitle = o.getString("rawTitle"),
        description = o.getString("description"),
        link = o.getString("link"),
        time = Date(o.getLong("time")),
        type = runCatching { ServiceType.valueOf(o.getString("type")) }.getOrDefault(ServiceType.OVERIG),
        prio = o.optString("prio").takeIf { it.isNotEmpty() && !o.isNull("prio") },
        province = o.optString("province").takeIf { it.isNotEmpty() && !o.isNull("province") },
        region = o.optString("region").takeIf { it.isNotEmpty() && !o.isNull("region") },
        city = o.optString("city").takeIf { it.isNotEmpty() && !o.isNull("city") },
        street = o.optString("street").takeIf { it.isNotEmpty() && !o.isNull("street") },
        postcode = o.optString("postcode").takeIf { it.isNotEmpty() && !o.isNull("postcode") },
        aard = o.optString("aard").takeIf { it.isNotEmpty() && !o.isNull("aard") },
        eenheden = o.optJSONArray("eenheden")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        dossier = o.optString("dossier").takeIf { it.isNotEmpty() && !o.isNull("dossier") },
        directeInzet = o.optBoolean("dia", false),
        bron = o.optString("bron").ifEmpty { "alarmeringen.nl" },
        lat = if (o.isNull("lat")) null else o.getDouble("lat"),
        lon = if (o.isNull("lon")) null else o.getDouble("lon"),
        gemeenteCode = o.optString("gemeenteCode").takeIf { it.isNotEmpty() && !o.isNull("gemeenteCode") },
        gemeenteNaam = o.optString("gemeenteNaam").takeIf { it.isNotEmpty() && !o.isNull("gemeenteNaam") },
        exacteLocatie = o.optBoolean("exact", false),
        grofGebied = o.optBoolean("grof", false),
        extent = o.optJSONArray("extent")?.takeIf { it.length() == 4 }?.let { a ->
            Bbox(a.getDouble(0), a.getDouble(1), a.getDouble(2), a.getDouble(3))
        }
    )
}

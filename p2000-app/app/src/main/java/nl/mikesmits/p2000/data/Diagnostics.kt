package nl.mikesmits.p2000.data

import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Kleine ringbuffer met wat de app aan het doen is, zodat er bij een klacht
 * iets concreets te delen valt in plaats van een vermoeden.
 */
object Diagnostics {

    private const val MAX_REGELS = 200
    private val klok = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val regels = Collections.synchronizedList(ArrayList<String>())

    fun log(bericht: String) {
        synchronized(regels) {
            regels.add("${klok.format(Date())}  $bericht")
            while (regels.size > MAX_REGELS) regels.removeAt(0)
        }
    }

    fun recent(aantal: Int = 60): List<String> = synchronized(regels) {
        regels.takeLast(aantal).toList()
    }

    fun wissen() = synchronized(regels) { regels.clear() }
}

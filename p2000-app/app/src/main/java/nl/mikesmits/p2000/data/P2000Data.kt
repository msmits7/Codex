package nl.mikesmits.p2000.data

import android.content.Context

/**
 * Eén gedeelde repository voor de hele app, zodat de achtergrond-service en
 * de UI dezelfde (persistente) meldinghistorie zien.
 */
object P2000Data {
    lateinit var repository: MeldingRepository
        private set

    fun init(context: Context) {
        if (::repository.isInitialized) return
        repository = MeldingRepository(HistoryStore(context.applicationContext))
    }
}

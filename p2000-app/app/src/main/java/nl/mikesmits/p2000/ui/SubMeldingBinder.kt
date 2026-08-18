package nl.mikesmits.p2000.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.databinding.ItemSubMeldingBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Vult een container met één ingesprongen regel per opgeroepen dienst, zodat
 * bij een gebundeld incident zichtbaar is dat bijvoorbeeld zowel ambulance
 * als brandweer is gealarmeerd.
 */
object SubMeldingBinder {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * @param showRaw true toont de originele pagertekst als detailregel
     *                (detailscherm), false de omschrijving (lijstkaart).
     */
    fun bind(container: LinearLayout, meldingen: List<Melding>, showRaw: Boolean) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        for (m in meldingen) {
            val row = ItemSubMeldingBinding.inflate(inflater, container, false)
            row.subIcon.setImageResource(MeldingAdapter.iconFor(m.type))
            row.subIcon.backgroundTintList =
                ContextCompat.getColorStateList(container.context, MeldingAdapter.colorFor(m.type))
            row.subTitle.text = listOfNotNull(m.type.label, m.prio, timeFormat.format(m.time))
                .joinToString(" · ")
            val detail = if (showRaw) m.rawTitle else m.description.ifEmpty { m.rawTitle }
            row.subDetail.text = detail
            if (!showRaw) {
                row.subDetail.maxLines = 2
                row.subDetail.ellipsize = android.text.TextUtils.TruncateAt.END
            } else {
                row.subDetail.typeface = android.graphics.Typeface.MONOSPACE
            }
            container.addView(row.root)
        }
    }
}

package nl.mikesmits.p2000.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.mikesmits.p2000.R
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.databinding.ItemMeldingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MeldingAdapter(
    private val onClick: (Melding) -> Unit = {}
) : ListAdapter<Melding, MeldingAdapter.Holder>(Diff) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    object Diff : DiffUtil.ItemCallback<Melding>() {
        override fun areItemsTheSame(oldItem: Melding, newItem: Melding) = oldItem.guid == newItem.guid
        override fun areContentsTheSame(oldItem: Melding, newItem: Melding) = oldItem == newItem
    }

    class Holder(val binding: ItemMeldingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMeldingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.textType.text = m.type.label + (m.prio?.let { " · $it" } ?: "")
        holder.binding.textDescription.text = m.description.ifEmpty { m.rawTitle }
        holder.binding.textLocation.text = m.locationLabel
        holder.binding.textTime.text = timeFormat.format(m.time)
        holder.binding.typeIndicator.setBackgroundColor(ContextCompat.getColor(ctx, colorFor(m.type)))
        holder.binding.root.setOnClickListener { onClick(m) }
    }

    companion object {
        fun colorFor(type: ServiceType): Int = when (type) {
            ServiceType.AMBULANCE -> R.color.type_ambulance
            ServiceType.BRANDWEER -> R.color.type_brandweer
            ServiceType.POLITIE -> R.color.type_politie
            ServiceType.TRAUMA -> R.color.type_trauma
            ServiceType.WATER -> R.color.type_water
            ServiceType.OVERIG -> R.color.type_overig
        }
    }
}

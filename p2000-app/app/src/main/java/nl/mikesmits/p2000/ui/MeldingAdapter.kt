package nl.mikesmits.p2000.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.mikesmits.p2000.R
import nl.mikesmits.p2000.data.MeldingGroep
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.databinding.ItemMeldingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MeldingAdapter(
    private val onClick: (MeldingGroep) -> Unit = {}
) : ListAdapter<MeldingGroep, MeldingAdapter.Holder>(Diff) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    object Diff : DiffUtil.ItemCallback<MeldingGroep>() {
        override fun areItemsTheSame(oldItem: MeldingGroep, newItem: MeldingGroep) =
            oldItem.primary.guid == newItem.primary.guid
        override fun areContentsTheSame(oldItem: MeldingGroep, newItem: MeldingGroep) =
            oldItem == newItem
    }

    class Holder(val binding: ItemMeldingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMeldingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val g = getItem(position)
        val m = g.primary
        val ctx = holder.binding.root.context
        val typesLabel = g.types.joinToString(" + ") { it.label }
        holder.binding.textType.text =
            listOfNotNull(typesLabel, g.prio, g.aard).joinToString(" · ")
        holder.binding.textDescription.text = m.description.ifEmpty { m.rawTitle }
        val extra = if (g.meldingen.size > 1) {
            " · " + ctx.getString(R.string.group_count, g.meldingen.size)
        } else ""
        holder.binding.textLocation.text = m.locationLabel + extra
        holder.binding.textTime.text = timeFormat.format(m.time)
        holder.binding.iconType.setImageResource(iconFor(m.type))
        holder.binding.iconType.backgroundTintList =
            ContextCompat.getColorStateList(ctx, colorFor(m.type))
        holder.binding.root.setOnClickListener { onClick(g) }
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

        fun iconFor(type: ServiceType): Int = when (type) {
            ServiceType.AMBULANCE -> R.drawable.ic_type_ambulance
            ServiceType.BRANDWEER -> R.drawable.ic_type_brandweer
            ServiceType.POLITIE -> R.drawable.ic_type_politie
            ServiceType.TRAUMA -> R.drawable.ic_type_trauma
            ServiceType.WATER -> R.drawable.ic_type_water
            ServiceType.OVERIG -> R.drawable.ic_type_overig
        }
    }
}

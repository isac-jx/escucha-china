package com.isacjx.escuchachina

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.isacjx.escuchachina.databinding.ItemSegmentBinding

/** Muestra cada frase: chino arriba, traducción (ES o EN) abajo, y el minuto. */
class SegmentAdapter(private var target: String) :
    RecyclerView.Adapter<SegmentAdapter.VH>() {

    private val items = ArrayList<SegmentRecord>()

    class VH(val b: ItemSegmentBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<SegmentRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setTarget(t: String) {
        target = t
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSegmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.b.chinese.text = s.chinese
        val translation = if (target == Translator.TARGET_EN) s.en else s.es
        holder.b.translation.text = translation.ifBlank { "…" }
        val sec = s.startSec.toInt()
        holder.b.time.text = String.format("%02d:%02d", sec / 60, sec % 60)
    }

    override fun getItemCount() = items.size
}

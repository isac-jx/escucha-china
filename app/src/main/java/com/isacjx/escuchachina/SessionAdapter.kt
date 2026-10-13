package com.isacjx.escuchachina

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.isacjx.escuchachina.databinding.ItemSessionBinding

class SessionAdapter(
    private val onClick: (Session) -> Unit,
    private val onDelete: (Session) -> Unit,
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private val items = ArrayList<Session>()

    class VH(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Session>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.b.title.text = s.title()
        val min = s.durationSec / 60
        val sec = s.durationSec % 60
        holder.b.subtitle.text = holder.itemView.context.getString(
            R.string.session_subtitle, s.segments.size, min, sec
        )
        holder.b.root.setOnClickListener { onClick(s) }
        holder.b.btnDelete.setOnClickListener { onDelete(s) }
    }

    override fun getItemCount() = items.size
}

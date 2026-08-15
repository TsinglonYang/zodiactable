package com.example.zodiactableapp.ui.dashboard

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.zodiactableapp.R

class ZodiacTableAdapter(
    private var rows: List<ZodiacTableRow>
) : RecyclerView.Adapter<ZodiacTableAdapter.VH>() {

    fun submit(newRows: List<ZodiacTableRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_zodiac_table_row, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.tvLabel.text = row.label

        val isSubtotal = row.label == "小计"
        holder.tvLabel.setTypeface(null, if (isSubtotal) Typeface.BOLD else Typeface.NORMAL)

        for (i in 0 until 12) {
            val tv = holder.cells[i]
            val v = row.values.getOrNull(i)
            tv.text = v?.toString() ?: ""
            tv.setTypeface(null, if (isSubtotal) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)

        val cells: List<TextView> = listOf(
            itemView.findViewById(R.id.c1),
            itemView.findViewById(R.id.c2),
            itemView.findViewById(R.id.c3),
            itemView.findViewById(R.id.c4),
            itemView.findViewById(R.id.c5),
            itemView.findViewById(R.id.c6),
            itemView.findViewById(R.id.c7),
            itemView.findViewById(R.id.c8),
            itemView.findViewById(R.id.c9),
            itemView.findViewById(R.id.c10),
            itemView.findViewById(R.id.c11),
            itemView.findViewById(R.id.c12)
        )
    }
}
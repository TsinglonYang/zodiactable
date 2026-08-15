package com.example.zodiactableapp.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.zodiactableapp.R

data class DashboardRow(
    val index: Int,
    val groupId: Int,
    val zodiacText: String,
    val level: Int,
    val amount: Int
)

class DashboardTableAdapter(
    private val rows: List<DashboardRow>
) : RecyclerView.Adapter<DashboardTableAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIndex: TextView = v.findViewById(R.id.tvIndex)
        val tvGroupId: TextView = v.findViewById(R.id.tvGroupId)
        val tvZodiac: TextView = v.findViewById(R.id.tvZodiac)
        val tvLevel: TextView = v.findViewById(R.id.tvLevel)
        val tvAmount: TextView = v.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        h.tvIndex.text = r.index.toString()
        h.tvGroupId.text = r.groupId.toString()
        h.tvZodiac.text = r.zodiacText
        h.tvLevel.text = r.level.toString()
        h.tvAmount.text = r.amount.toString()
    }

    override fun getItemCount() = rows.size
}

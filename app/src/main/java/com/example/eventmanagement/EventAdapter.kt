package com.example.eventmanagement

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private val items: List<Event>,
    private val action: (Event, String) -> Unit
) : RecyclerView.Adapter<EventAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvId: TextView = v.findViewById(R.id.tvEventId)
        val tvTitle: TextView = v.findViewById(R.id.tvEventTitle)
        val tvDate: TextView = v.findViewById(R.id.tvEventDate)
        val tvLocation: TextView = v.findViewById(R.id.tvEventLocation)
        val tvStatus: TextView = v.findViewById(R.id.tvEventStatus)
        val btnEdit: ImageButton = v.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val e = items[pos]

        h.tvId.text = "ID: ${e.id}"
        h.tvTitle.text = e.title
        h.tvDate.text = "${e.date} | ${e.time}"
        h.tvLocation.text = e.location
        h.tvStatus.text = e.status

        h.itemView.setOnClickListener { action(e, "view") }
        h.btnEdit.setOnClickListener { action(e, "edit") }
        h.btnDelete.setOnClickListener { action(e, "delete") }
    }
}

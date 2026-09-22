package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation

class AdministradorAdapter(
    private val administradores: List<Admin>,
    private val onClick: (Admin) -> Unit
) : RecyclerView.Adapter<AdministradorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoAdministrador)
        val tvNome: TextView = view.findViewById(R.id.tvNomeAdministrador)
        val tvPapel: TextView = view.findViewById(R.id.tvPapelAdministrador)
        val tvEmail: TextView = view.findViewById(R.id.tvEmailAdministrador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_administrador, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val admin = administradores[position]
        val context = holder.itemView.context

        holder.tvNome.text = admin.nomeCompleto.ifEmpty { admin.email ?: "" }
        holder.tvPapel.text = context.getString(
            if (admin.ehColaborador) R.string.admin_badge_colaborador else R.string.admin_badge_admin
        )
        holder.tvEmail.text = admin.email ?: ""

        if (!admin.fotoUrl.isNullOrEmpty()) {
            holder.ivFoto.load(admin.fotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person_default)
        }

        holder.itemView.setOnClickListener { onClick(admin) }
    }

    override fun getItemCount() = administradores.size
}

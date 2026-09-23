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

// Lista de administradores pra escolher com quem começar uma conversa nova
// (ver EscolherAdminChatCaronasActivity) — nunca inclui o próprio admin
// logado (filtrado antes de chegar aqui).
class SelecionarAdminAdapter(
    private val admins: List<Admin>,
    private val onClick: (Admin) -> Unit
) : RecyclerView.Adapter<SelecionarAdminAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoSelecionarAdmin)
        val tvNome: TextView = view.findViewById(R.id.tvNomeSelecionarAdmin)
        val tvEmail: TextView = view.findViewById(R.id.tvEmailSelecionarAdmin)
        val cliqueRoot: View = view.findViewById(R.id.rowSelecionarAdminClicavel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_selecionar_admin_caronas, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val admin = admins[position]
        holder.tvNome.text = admin.nomeCompleto.ifEmpty { admin.email ?: "" }
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

        holder.cliqueRoot.setOnClickListener { onClick(admin) }
    }

    override fun getItemCount() = admins.size
}

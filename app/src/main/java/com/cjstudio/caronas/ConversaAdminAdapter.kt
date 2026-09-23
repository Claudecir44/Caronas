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
import java.text.SimpleDateFormat
import java.util.Locale

// Diferente de ConversaCaronaAdapter, aqui a prévia da última mensagem
// aparece na lista sim — é uma ferramenta interna entre administradores, não
// tem o mesmo motivo de privacidade do chat com o usuário (ver comentário em
// ConversaCaronaAdapter). Mesmo espírito da lista de conversas do Match.
class ConversaAdminAdapter(
    private val conversas: List<ConversaAdmin>,
    private val meuId: String,
    private val onClick: (ConversaAdmin) -> Unit,
    private val onLongClick: (ConversaAdmin) -> Unit
) : RecyclerView.Adapter<ConversaAdminAdapter.ViewHolder>() {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoConversaAdmin)
        val tvNome: TextView = view.findViewById(R.id.tvNomeConversaAdmin)
        val tvUltimaMensagem: TextView = view.findViewById(R.id.tvUltimaMensagemConversaAdmin)
        val tvHora: TextView = view.findViewById(R.id.tvHoraConversaAdmin)
        val tvBadge: TextView = view.findViewById(R.id.tvBadgeConversaAdmin)
        val cliqueRoot: View = view.findViewById(R.id.rowConversaAdminClicavel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversa_admin_caronas, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversa = conversas[position]

        holder.tvNome.text = conversa.nomeOutroAdmin(meuId)?.trim().takeUnless { it.isNullOrEmpty() } ?: "?"
        holder.tvUltimaMensagem.text = conversa.ultimaMensagem.orEmpty()
        holder.tvHora.text = conversa.ultimoTimestamp?.let { formatoHora.format(it) } ?: ""

        val naoLidas = conversa.naoLidasParaMim(meuId)
        if (naoLidas > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = naoLidas.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        val fotoOutro = conversa.fotoOutroAdmin(meuId)
        if (!fotoOutro.isNullOrEmpty()) {
            holder.ivFoto.load(fotoOutro) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person_default)
        }

        holder.cliqueRoot.setOnClickListener { onClick(conversa) }
        holder.cliqueRoot.setOnLongClickListener { onLongClick(conversa); true }
    }

    override fun getItemCount() = conversas.size
}

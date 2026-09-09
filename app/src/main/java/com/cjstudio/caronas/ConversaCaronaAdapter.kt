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

class ConversaCaronaAdapter(
    private val conversas: List<ConversaCarona>,
    private val meuId: String,
    private val onClick: (ConversaCarona) -> Unit
) : RecyclerView.Adapter<ConversaCaronaAdapter.ViewHolder>() {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoConversa)
        val tvNome: TextView = view.findViewById(R.id.tvNomeConversa)
        val tvRota: TextView = view.findViewById(R.id.tvRotaConversa)
        val tvUltimaMensagem: TextView = view.findViewById(R.id.tvUltimaMensagemConversa)
        val tvHora: TextView = view.findViewById(R.id.tvHoraConversa)
        val tvBadge: TextView = view.findViewById(R.id.tvBadgeConversa)
        val cliqueRoot: View = view.findViewById(R.id.rowConversaClicavel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversa_carona, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversa = conversas[position]
        val context = holder.itemView.context

        holder.tvNome.text = conversa.nomeOutroUsuario(meuId) ?: context.getString(R.string.procurar_motorista_desconhecido)
        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            conversa.cidadeOrigem ?: "",
            conversa.cidadeDestino ?: ""
        )
        holder.tvUltimaMensagem.text = conversa.ultimaMensagem ?: ""
        holder.tvHora.text = conversa.ultimoTimestamp?.let { formatoHora.format(it) } ?: ""

        val naoLidas = conversa.naoLidasParaMim(meuId)
        if (naoLidas > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = naoLidas.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        val fotoOutro = conversa.fotoOutroUsuario(meuId)
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
    }

    override fun getItemCount() = conversas.size
}

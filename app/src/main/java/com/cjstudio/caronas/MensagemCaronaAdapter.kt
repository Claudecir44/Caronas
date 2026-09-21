package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Só leitura: mensagem não é apagada por ninguém (nem "só pra mim", nem
// "pra todos") — o histórico fica igual pros dois (ver firestore.rules).
class MensagemCaronaAdapter(
    private val mensagens: List<MensagemCarona>,
    private val meuId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    companion object {
        private const val TIPO_ENVIADA = 0
        private const val TIPO_RECEBIDA = 1
    }

    class ViewHolderEnviada(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudoEnviada)
        val tvHora: TextView = view.findViewById(R.id.tvHoraEnviada)
        val tvStatusLeitura: TextView = view.findViewById(R.id.tvStatusLeituraEnviada)
    }

    class ViewHolderRecebida(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudoRecebida)
        val tvHora: TextView = view.findViewById(R.id.tvHoraRecebida)
    }

    override fun getItemViewType(position: Int): Int {
        return if (mensagens[position].remetenteId == meuId) TIPO_ENVIADA else TIPO_RECEBIDA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_ENVIADA) {
            ViewHolderEnviada(inflater.inflate(R.layout.item_mensagem_enviada, parent, false))
        } else {
            ViewHolderRecebida(inflater.inflate(R.layout.item_mensagem_recebida, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensagem = mensagens[position]
        val context = holder.itemView.context

        val texto = mensagem.conteudo.orEmpty()
        val hora = mensagem.timestamp?.let { formatoHora.format(it) } ?: ""

        when (holder) {
            is ViewHolderEnviada -> {
                holder.tvConteudo.text = texto
                holder.tvHora.text = hora

                // Status de leitura (estilo Match): "Lida"/"Não lida" embaixo
                // das mensagens que EU enviei.
                holder.tvStatusLeitura.visibility = View.VISIBLE
                if (mensagem.lida) {
                    holder.tvStatusLeitura.text = context.getString(R.string.chat_carona_status_lida)
                    holder.tvStatusLeitura.setTextColor(0xFF00C853.toInt())
                } else {
                    holder.tvStatusLeitura.text = context.getString(R.string.chat_carona_status_nao_lida)
                    holder.tvStatusLeitura.setTextColor(0xFFFF1744.toInt())
                }
            }
            is ViewHolderRecebida -> {
                holder.tvConteudo.text = texto
                holder.tvHora.text = hora
            }
        }
    }

    override fun getItemCount() = mensagens.size
}

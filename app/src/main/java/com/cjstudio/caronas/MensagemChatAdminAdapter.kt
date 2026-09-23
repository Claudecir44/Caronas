package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Reaproveita os MESMOS layouts de bolha do chat de carona
// (item_mensagem_enviada/recebida.xml) — só muda o tipo da lista e o
// long-click, que aqui abre o menu de apagar (ver ChatAdminCaronaActivity).
class MensagemChatAdminAdapter(
    private val mensagens: List<MensagemChatAdmin>,
    private val meuId: String,
    private val onLongClick: (MensagemChatAdmin) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    companion object {
        private const val TIPO_ENVIADA = 0
        private const val TIPO_RECEBIDA = 1
    }

    class ViewHolderEnviada(view: View) : RecyclerView.ViewHolder(view) {
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudoEnviada)
        val tvHora: TextView = view.findViewById(R.id.tvHoraEnviada)
        val tvStatusLeitura: TextView = view.findViewById(R.id.tvStatusLeituraEnviada)
    }

    class ViewHolderRecebida(view: View) : RecyclerView.ViewHolder(view) {
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

        // Mensagem apagada (só pra mim ou pra todos) mostra o texto fixo em
        // vez do conteúdo real, sem long-click de apagar de novo.
        val apagadaParaMim = mensagem.deletadaParaMim(meuId)
        val texto = if (apagadaParaMim) context.getString(R.string.chat_admin_mensagem_apagada) else mensagem.conteudo.orEmpty()
        val hora = mensagem.timestamp?.let { formatoHora.format(it) } ?: ""

        when (holder) {
            is ViewHolderEnviada -> {
                holder.tvConteudo.text = texto
                holder.tvHora.text = hora
                if (apagadaParaMim) {
                    holder.tvStatusLeitura.visibility = View.GONE
                } else {
                    holder.tvStatusLeitura.visibility = View.VISIBLE
                    if (mensagem.lida) {
                        holder.tvStatusLeitura.text = context.getString(R.string.chat_carona_status_lida)
                        holder.tvStatusLeitura.setTextColor(0xFF00C853.toInt())
                    } else {
                        holder.tvStatusLeitura.text = context.getString(R.string.chat_carona_status_nao_lida)
                        holder.tvStatusLeitura.setTextColor(0xFFFF1744.toInt())
                    }
                }
                holder.itemView.setOnLongClickListener {
                    if (!apagadaParaMim) onLongClick(mensagem)
                    true
                }
            }
            is ViewHolderRecebida -> {
                holder.tvConteudo.text = texto
                holder.tvHora.text = hora
                holder.itemView.setOnLongClickListener {
                    if (!apagadaParaMim) onLongClick(mensagem)
                    true
                }
            }
        }
    }

    override fun getItemCount() = mensagens.size
}

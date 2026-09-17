package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Lista mensagens de VÁRIAS conversas misturadas (é a lista inteira de
// conversas do usuário buscado, não uma conversa só — ver
// AdministracaoCaronasActivity/AdminRepository.listarMensagensDoUsuario),
// por isso cada item mostra a rota e quem mandou pra quem, não só o texto.
class MensagemAdminAdapter(
    private val mensagens: List<MensagemAdminInfo>
) : RecyclerView.Adapter<MensagemAdminAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRota: TextView = view.findViewById(R.id.tvRotaMensagemAdmin)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraMensagemAdmin)
        val tvRemetenteDestinatario: TextView = view.findViewById(R.id.tvRemetenteDestinatarioMensagemAdmin)
        val tvConteudo: TextView = view.findViewById(R.id.tvConteudoMensagemAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mensagem_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = mensagens[position]
        val mensagem = info.mensagem
        val conversa = info.conversa
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            conversa.cidadeOrigem ?: "",
            conversa.cidadeDestino ?: ""
        )
        holder.tvDataHora.text = mensagem.timestamp?.let { formatoDataHora.format(it) } ?: ""

        val nomeRemetente = nomeParticipante(mensagem.remetenteId, conversa)
        val nomeDestinatario = nomeParticipante(mensagem.destinatarioId, conversa)
        holder.tvRemetenteDestinatario.text = context.getString(
            R.string.admin_mensagem_remetente_destinatario_formato, nomeRemetente, nomeDestinatario
        )

        // Respeita só o "apagada pra todos" (uma exclusão real, os dois
        // lados perderam o texto) — o admin, pra fins de suporte, ainda
        // enxerga o conteúdo mesmo que um dos dois tenha apagado só pra si
        // (ver MensagemCarona.deletadaParaRemetente/Destinatario, que são
        // ocultação pessoal, não uma exclusão de verdade).
        holder.tvConteudo.text = if (mensagem.deletadaParaTodos) {
            context.getString(R.string.chat_carona_mensagem_apagada)
        } else {
            mensagem.conteudo.orEmpty()
        }
    }

    private fun nomeParticipante(id: String?, conversa: ConversaCarona): String {
        if (id == null) return "?"
        return when (id) {
            conversa.motoristaId -> conversa.motoristaNome?.trim()?.substringBefore(" ") ?: "Motorista"
            conversa.passageiroId -> conversa.passageiroNome?.trim()?.substringBefore(" ") ?: "Passageiro"
            else -> "?"
        }
    }

    override fun getItemCount() = mensagens.size
}

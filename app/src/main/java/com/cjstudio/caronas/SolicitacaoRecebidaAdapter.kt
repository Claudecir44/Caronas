package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import java.text.SimpleDateFormat
import java.util.Locale

// Card fechado por padrão (só rota, status e data) — toque no cabeçalho
// abre e revela nome (primeiro nome) e foto do passageiro antes do
// motorista decidir confirmar. Só um fica aberto por vez (mesmo padrão de
// MinhaViagemAdapter).
class SolicitacaoRecebidaAdapter(
    private val solicitacoes: List<Solicitacao>,
    private val avaliadas: Set<String>,
    private val onConfirmarClick: (Solicitacao) -> Unit,
    private val onChatClick: (Solicitacao) -> Unit,
    private val onExcluirLongClick: (Solicitacao) -> Unit,
    private val onAvaliarClick: (Solicitacao) -> Unit,
    private val onPerfilClick: (String) -> Unit,
    // Cancela uma viagem que o motorista já tinha confirmado (ver
    // TelaCaronasActivity.confirmarCancelarComoMotorista) — diferente de
    // onExcluirLongClick, que apaga o registro inteiro sem avisar ninguém.
    private val onCancelarClick: (Solicitacao) -> Unit
) : RecyclerView.Adapter<SolicitacaoRecebidaAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    private var posicaoAberta = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.headerSolicitacao)
        val corpoExpandido: LinearLayout = view.findViewById(R.id.corpoExpandidoSolicitacao)
        val tvExpandIcon: TextView = view.findViewById(R.id.tvExpandIconSolicitacao)
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoPassageiro)
        val tvNome: TextView = view.findViewById(R.id.tvNomePassageiro)
        val tvRota: TextView = view.findViewById(R.id.tvRotaSolicitacao)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusSolicitacao)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraSolicitacao)
        val btnConfirmar: Button = view.findViewById(R.id.btnConfirmarSolicitacao)
        val btnCancelar: Button = view.findViewById(R.id.btnCancelarSolicitacao)
        val btnChat: Button = view.findViewById(R.id.btnChatSolicitacao)
        val btnAvaliar: Button = view.findViewById(R.id.btnAvaliarSolicitacao)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_solicitacao_recebida, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val solicitacao = solicitacoes[position]
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            solicitacao.cidadeOrigem ?: "",
            solicitacao.cidadeDestino ?: ""
        )
        holder.tvDataHora.text = solicitacao.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""

        // Só o primeiro nome — o motorista revela isso ao abrir o card,
        // antes de decidir confirmar, sem expor o nome completo aqui.
        holder.tvNome.text = solicitacao.passageiroNome?.trim()?.substringBefore(" ")
            ?: context.getString(R.string.procurar_motorista_desconhecido)

        if (!solicitacao.passageiroFotoUrl.isNullOrEmpty()) {
            holder.ivFoto.load(solicitacao.passageiroFotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person_default)
        }

        val passageiroId = solicitacao.passageiroId
        if (passageiroId != null) {
            val abrirPerfil = { onPerfilClick(passageiroId) }
            holder.ivFoto.setOnClickListener { abrirPerfil() }
            holder.tvNome.setOnClickListener { abrirPerfil() }
        }

        // "Avaliar"/"Cancelar" dependem de já ter ocorrido ou não (mesmo
        // critério de MinhaViagemAdapter, do lado do motorista, com a mesma
        // tolerância de 10min — ver StatusViagemUtil) — calculado antes do
        // "when" abaixo porque os dois dependem disso.
        val jaOcorreu = StatusViagemUtil.jaConcluida(solicitacao.dataHoraPartida)

        when (solicitacao.status) {
            "confirmada" -> {
                holder.tvStatus.text = context.getString(
                    if (jaOcorreu) R.string.minhas_viagens_status_concluida else R.string.solicitacoes_status_confirmada
                )
                holder.tvStatus.setBackgroundColor(0xFF2E7D32.toInt())
                holder.btnConfirmar.visibility = View.GONE
                // Só dá pra desistir de uma viagem confirmada ANTES dela
                // acontecer — depois disso vira histórico, mesmo espírito
                // de "podeCancelar" em MinhaViagemAdapter (lado do passageiro).
                holder.btnCancelar.visibility = if (!jaOcorreu) View.VISIBLE else View.GONE
            }
            "cancelada" -> {
                holder.tvStatus.text = context.getString(R.string.solicitacoes_status_cancelada)
                holder.tvStatus.setBackgroundColor(0xFFD32F2F.toInt())
                holder.btnConfirmar.visibility = View.GONE
                holder.btnCancelar.visibility = View.GONE
            }
            else -> {
                holder.tvStatus.text = context.getString(R.string.solicitacoes_status_solicitada)
                holder.tvStatus.setBackgroundColor(0xFF1E90FF.toInt())
                holder.btnConfirmar.visibility = View.VISIBLE
                holder.btnCancelar.visibility = View.GONE
            }
        }
        holder.btnCancelar.setOnClickListener { onCancelarClick(solicitacao) }

        val podeAvaliar = solicitacao.status == "confirmada" && jaOcorreu && solicitacao.id !in avaliadas
        holder.btnAvaliar.visibility = if (podeAvaliar) View.VISIBLE else View.GONE
        holder.btnAvaliar.setOnClickListener { onAvaliarClick(solicitacao) }

        val aberto = position == posicaoAberta
        holder.corpoExpandido.visibility = if (aberto) View.VISIBLE else View.GONE
        holder.tvExpandIcon.text = if (aberto) "▲" else "▼"

        holder.header.setOnClickListener {
            val anterior = posicaoAberta
            posicaoAberta = if (aberto) -1 else position
            if (anterior != -1) notifyItemChanged(anterior)
            if (posicaoAberta != -1) notifyItemChanged(posicaoAberta)
        }
        // Toque e segure no cabeçalho (sempre visível, aberto ou fechado) —
        // exclui a solicitação de vez, com confirmação (ver TelaCaronasActivity.confirmarExcluirSolicitacao).
        holder.header.setOnLongClickListener {
            onExcluirLongClick(solicitacao)
            true
        }

        holder.btnConfirmar.setOnClickListener { onConfirmarClick(solicitacao) }
        holder.btnChat.setOnClickListener { onChatClick(solicitacao) }
    }

    override fun getItemCount() = solicitacoes.size
}

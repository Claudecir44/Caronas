package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Lista de reclamações/sugestões/denúncias no painel admin ("Ver Sugestões
// e Reclamações"). Card fechado por padrão (mesmo padrão de
// SolicitacaoRecebidaAdapter) — só um aberto por vez. "mostrarBotoes"
// desliga Responder/Arquivar/Excluir na visão de Arquivados (lá só faz
// sentido consultar, ver AdministracaoCaronasActivity.mostrarManifestacoesArquivadas).
class ManifestacaoAdminAdapter(
    private val itens: List<Manifestacao>,
    private val mostrarBotoes: Boolean = true,
    private val onResponderClick: (Manifestacao) -> Unit = {},
    private val onArquivarClick: (Manifestacao) -> Unit = {},
    private val onExcluirClick: (Manifestacao) -> Unit = {}
) : RecyclerView.Adapter<ManifestacaoAdminAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    private var posicaoAberta = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.headerManifestacao)
        val corpoExpandido: LinearLayout = view.findViewById(R.id.corpoExpandidoManifestacao)
        val tvExpandIcon: TextView = view.findViewById(R.id.tvExpandIconManifestacao)
        val tvTipo: TextView = view.findViewById(R.id.tvTipoManifestacao)
        val tvNome: TextView = view.findViewById(R.id.tvNomeManifestacao)
        val tvData: TextView = view.findViewById(R.id.tvDataManifestacao)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusManifestacao)
        val tvContato: TextView = view.findViewById(R.id.tvContatoManifestacao)
        val tvMensagem: TextView = view.findViewById(R.id.tvMensagemManifestacao)
        val containerResposta: LinearLayout = view.findViewById(R.id.containerRespostaManifestacao)
        val tvResposta: TextView = view.findViewById(R.id.tvRespostaManifestacao)
        val btnResponder: Button = view.findViewById(R.id.btnResponderManifestacao)
        val btnArquivar: Button = view.findViewById(R.id.btnArquivarManifestacao)
        val btnExcluir: Button = view.findViewById(R.id.btnExcluirManifestacao)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manifestacao_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val context = holder.itemView.context

        holder.tvTipo.text = when (item.tipo) {
            Manifestacao.TIPO_RECLAMACAO -> context.getString(R.string.admin_manifestacoes_tipo_reclamacao)
            Manifestacao.TIPO_DENUNCIA -> context.getString(R.string.admin_manifestacoes_tipo_denuncia)
            else -> context.getString(R.string.admin_manifestacoes_tipo_sugestao)
        }
        holder.tvNome.text = item.nomeCompleto?.ifBlank { null } ?: "Anônimo"
        holder.tvData.text = item.criadoEm?.let { formatoDataHora.format(it) } ?: ""

        val respondida = item.status == Manifestacao.STATUS_RESPONDIDO
        if (respondida) {
            val dataResp = item.respondidoEm?.let { formatoDataHora.format(it) } ?: "-"
            holder.tvStatus.text = context.getString(R.string.admin_manifestacoes_status_respondido_formato, dataResp) +
                "\n" + context.getString(R.string.admin_manifestacoes_respondido_por_formato, item.respondidoPorCpf ?: "não disponível")
            holder.tvStatus.setTextColor(0xFF00897B.toInt())
        } else {
            holder.tvStatus.text = context.getString(R.string.admin_manifestacoes_status_pendente)
            holder.tvStatus.setTextColor(0xFF999999.toInt())
        }

        holder.tvContato.text = "${item.email ?: "-"} — ${item.telefone ?: "-"}"
        // Denúncia feita do chat/perfil: aponta quem foi denunciado e o
        // motivo antes da descrição (ver SegurancaUsuarioDialogUtil).
        holder.tvMensagem.text = if (item.denunciadoId != null) {
            context.getString(
                R.string.admin_manifestacoes_denunciado_formato,
                item.denunciadoNome ?: item.denunciadoId,
                item.motivo ?: "",
                item.mensagem ?: ""
            )
        } else {
            item.mensagem ?: ""
        }

        if (respondida && !item.resposta.isNullOrBlank()) {
            holder.containerResposta.visibility = View.VISIBLE
            holder.tvResposta.text = item.resposta
        } else {
            holder.containerResposta.visibility = View.GONE
        }

        val aberto = position == posicaoAberta
        holder.corpoExpandido.visibility = if (aberto) View.VISIBLE else View.GONE
        holder.tvExpandIcon.text = if (aberto) "▲" else "▼"
        holder.header.setOnClickListener {
            val anterior = posicaoAberta
            posicaoAberta = if (aberto) -1 else position
            if (anterior != -1) notifyItemChanged(anterior)
            if (posicaoAberta != -1) notifyItemChanged(posicaoAberta)
        }

        if (mostrarBotoes) {
            holder.btnResponder.visibility = View.VISIBLE
            holder.btnArquivar.visibility = View.VISIBLE
            holder.btnResponder.setOnClickListener { onResponderClick(item) }
            holder.btnArquivar.setOnClickListener { onArquivarClick(item) }
        } else {
            holder.btnResponder.visibility = View.GONE
            holder.btnArquivar.visibility = View.GONE
        }
        holder.btnExcluir.setOnClickListener { onExcluirClick(item) }
    }

    override fun getItemCount() = itens.size
}

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

// Oferta ainda ativa: card aberto (rota resumida, status, data, tempo, vagas,
// valor e Editar/Excluir sempre visíveis) — tocar no cabeçalho só abre o
// corpo com a rota COMPLETA (origem, cada parada e destino, com endereço
// quando houver), mesmo espírito de MinhaViagemAdapter. A cidade de chegada
// vai em verde-água (a de partida já é verde).
//
// Oferta já concluída: card RECOLHIDO — só cabeçalho + data/tempo, igual às
// Solicitações Recebidas — e tocar no cabeçalho expande tudo (vagas, valor,
// total recebido, rota completa e Excluir).
//
// O título da seção ("Viagens Ofertadas") agora é a aba de Minhas Ofertas
// (ver TelaCaronasActivity.selecionarAbaOfertas), não mais uma linha da lista.
class MinhaOfertaAdapter(
    private val ofertas: List<Carona>,
    // Vagas ainda livres da rota inteira de cada oferta (id -> vagas), já
    // descontando solicitações pendentes/confirmadas (ver
    // SolicitacaoRepository.vagasDisponiveis) — cai pra oferta.vagas (a
    // capacidade total, sem desconto) se a oferta não tiver id ou não
    // estiver no mapa.
    private val vagasDisponiveisPorOferta: Map<String, Int> = emptyMap(),
    // Soma de valorPago de todas as solicitações CONFIRMADAS de cada
    // oferta (id -> total), calculado uma vez em TelaCaronasActivity.
    // carregarOfertas a partir de uma única query (não uma por oferta).
    private val totalRecebidoPorOferta: Map<String, Double> = emptyMap(),
    private val onEditarClick: (Carona) -> Unit,
    private val onExcluirClick: (Carona) -> Unit
) : RecyclerView.Adapter<MinhaOfertaAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    private var posicaoAberta = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.headerMinhaOferta)
        val tvExpandIcon: TextView = view.findViewById(R.id.tvExpandIconOferta)
        val corpoExpandido: LinearLayout = view.findViewById(R.id.corpoExpandidoOferta)
        val tvRota: TextView = view.findViewById(R.id.tvRotaOferta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusOferta)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraOferta)
        val tvTempoViagem: TextView = view.findViewById(R.id.tvTempoViagemOferta)
        val tvVagas: TextView = view.findViewById(R.id.tvVagasOferta)
        val tvValor: TextView = view.findViewById(R.id.tvValorOferta)
        val tvTotalRecebido: TextView = view.findViewById(R.id.tvTotalRecebidoOferta)
        val corpoResumo: LinearLayout = view.findViewById(R.id.corpoResumoOferta)
        val linhaBotoes: LinearLayout = view.findViewById(R.id.linhaBotoesOferta)
        val btnEditar: Button = view.findViewById(R.id.btnEditarOferta)
        val btnExcluir: Button = view.findViewById(R.id.btnExcluirOferta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_minha_oferta, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val oferta = ofertas[position]
        val context = holder.itemView.context
        val concluida = StatusViagemUtil.jaConcluida(oferta.dataHoraPartida)

        // Chegada em verde-água só enquanto a viagem ainda está ativa; concluída
        // volta ao preto do layout.
        holder.tvRota.text = RotaTextoUtil.formatar(
            context,
            oferta.cidadeOrigem,
            oferta.cidadeDestino,
            corDestino = if (concluida) null else RotaTextoUtil.COR_DESTINO_VERDE_AGUA
        )
        holder.tvDataHora.text = oferta.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        TempoViagemUtil.preencher(holder.tvTempoViagem, oferta.distanciaKm)
        val vagas = oferta.id?.let { vagasDisponiveisPorOferta[it] } ?: oferta.vagas
        holder.tvVagas.text = context.getString(R.string.procurar_vagas_formato, vagas)
        holder.tvValor.text = context.getString(
            R.string.procurar_valor_formato,
            String.format(Locale("pt", "BR"), "%.2f", oferta.valorPorVaga ?: 0.0)
        )
        holder.tvStatus.text = context.getString(
            when {
                oferta.status == "cancelada" -> R.string.minhas_ofertas_status_cancelada
                StatusViagemUtil.jaConcluida(oferta.dataHoraPartida) -> R.string.minhas_ofertas_status_concluida
                else -> R.string.minhas_ofertas_status_ativa
            }
        )

        val totalRecebido = oferta.id?.let { totalRecebidoPorOferta[it] } ?: 0.0
        if (totalRecebido > 0.0) {
            holder.tvTotalRecebido.visibility = View.VISIBLE
            holder.tvTotalRecebido.text = context.getString(
                R.string.minhas_ofertas_total_recebido_formato,
                String.format(Locale("pt", "BR"), "%.2f", totalRecebido)
            )
        } else {
            holder.tvTotalRecebido.visibility = View.GONE
        }

        val aberto = position == posicaoAberta
        holder.tvExpandIcon.text = if (aberto) "▲" else "▼"
        holder.corpoExpandido.visibility = if (aberto) View.VISIBLE else View.GONE
        if (aberto) preencherDetalhesRota(holder.corpoExpandido, oferta)

        // Concluída: recolhida até tocar no cabeçalho. Definido em todo bind
        // (a view é reaproveitada pelo RecyclerView).
        val mostrarCorpo = !concluida || aberto
        holder.corpoResumo.visibility = if (mostrarCorpo) View.VISIBLE else View.GONE
        holder.linhaBotoes.visibility = if (mostrarCorpo) View.VISIBLE else View.GONE

        holder.header.setOnClickListener {
            val anterior = posicaoAberta
            posicaoAberta = if (aberto) -1 else position
            if (anterior != -1) notifyItemChanged(anterior)
            if (posicaoAberta != -1) notifyItemChanged(posicaoAberta)
        }

        // Viagem concluída não pode mais ser editada (o servidor também recusa, ver
        // firestore.rules: viagemJaConcluida) — só sobra o Excluir. Visibilidade
        // definida em todo bind, porque a view é reaproveitada pelo RecyclerView.
        holder.btnEditar.visibility = if (concluida) View.GONE else View.VISIBLE
        holder.btnEditar.setOnClickListener { onEditarClick(oferta) }
        holder.btnExcluir.setOnClickListener { onExcluirClick(oferta) }
    }

    // Uma linha por ponto da rota (origem, cada parada intermediária,
    // destino), com o endereço junto quando o motorista preencheu um —
    // toda a informação que existia na tela de Oferecer Carona antes de
    // publicar, não só o resumo origem→destino do cabeçalho.
    private fun preencherDetalhesRota(container: LinearLayout, oferta: Carona) {
        container.removeAllViews()
        val context = container.context
        val paradas = oferta.paradas
        if (paradas.isEmpty()) return

        paradas.forEachIndexed { indice, parada ->
            val formato = when (indice) {
                0 -> R.string.minhas_ofertas_detalhe_origem
                paradas.lastIndex -> R.string.minhas_ofertas_detalhe_destino
                else -> R.string.minhas_ofertas_detalhe_parada
            }
            val cidade = parada.cidade ?: ""
            val texto = if (!parada.endereco.isNullOrBlank()) {
                context.getString(formato, context.getString(R.string.minhas_ofertas_detalhe_com_endereco, cidade, parada.endereco))
            } else {
                context.getString(formato, cidade)
            }
            container.addView(TextView(context).apply {
                text = texto
                setTextColor(0xFF455A64.toInt())
                textSize = 12f
                setPadding(0, 4, 0, 4)
            })
        }
    }

    override fun getItemCount() = ofertas.size
}

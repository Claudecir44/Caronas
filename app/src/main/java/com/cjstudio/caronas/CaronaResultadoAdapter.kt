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

class CaronaResultadoAdapter(
    private val resultados: List<ResultadoBuscaCarona>,
    private val onSolicitarClick: (ResultadoBuscaCarona) -> Unit,
    private val onPerfilClick: (String) -> Unit
) : RecyclerView.Adapter<CaronaResultadoAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    // Ids das caronas já solicitadas nesta lista (nesta busca) — evita
    // solicitar de novo sem sair da tela. Marcado otimista assim que o
    // motorista toca "Solicitar vaga" (ver TelaCaronasActivity), pra
    // desabilitar o botão antes mesmo da resposta do servidor chegar e
    // impedir toque duplo durante a espera da rede.
    private val solicitadas = mutableSetOf<String>()

    fun marcarComoSolicitada(caronaId: String) {
        if (!solicitadas.add(caronaId)) return
        val posicao = resultados.indexOfFirst { it.carona.id == caronaId }
        if (posicao != -1) notifyItemChanged(posicao)
    }

    // Desfaz a marcação otimista se o pedido falhar no servidor.
    fun desmarcarComoSolicitada(caronaId: String) {
        if (!solicitadas.remove(caronaId)) return
        val posicao = resultados.indexOfFirst { it.carona.id == caronaId }
        if (posicao != -1) notifyItemChanged(posicao)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoMotoristaResultado)
        val tvRota: TextView = view.findViewById(R.id.tvRotaResultado)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraResultado)
        val tvNomeMotorista: TextView = view.findViewById(R.id.tvNomeMotoristaResultado)
        val tvCarroMotorista: TextView = view.findViewById(R.id.tvCarroMotoristaResultado)
        val tvVagas: TextView = view.findViewById(R.id.tvVagasResultado)
        val tvValor: TextView = view.findViewById(R.id.tvValorResultado)
        val btnSolicitar: android.widget.Button = view.findViewById(R.id.btnSolicitarResultado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_carona_resultado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resultado = resultados[position]
        val carona = resultado.carona
        val context = holder.itemView.context

        // Só o TRECHO buscado (embarque → desembarque) — nunca a rota
        // inteira do motorista. Uma parada em outro pedaço da viagem, pra
        // pegar outro passageiro, não é da conta de quem só vai andar
        // deste trecho pra cá.
        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            resultado.cidadeEmbarque,
            resultado.cidadeDesembarque
        )
        holder.tvDataHora.text = carona.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""

        // Só o primeiro nome do motorista aqui — nome completo só depois de
        // confirmada a solicitação (ver MinhaViagemAdapter).
        holder.tvNomeMotorista.text = carona.motoristaNome?.trim()?.substringBefore(" ")
            ?: context.getString(R.string.procurar_motorista_desconhecido)

        val veiculo = carona.veiculo
        holder.tvCarroMotorista.text = if (veiculo != null) {
            context.getString(
                R.string.minhas_viagens_carro_formato,
                veiculo.modelo ?: "",
                veiculo.marca ?: "",
                veiculo.cor ?: "",
                veiculo.placa ?: ""
            )
        } else {
            ""
        }

        holder.tvVagas.text = context.getString(R.string.procurar_vagas_formato, resultado.vagasDisponiveis)
        // Valor SÓ do trecho buscado — nunca o valor da rota inteira do
        // motorista (ver ResultadoBuscaCarona/TelaCaronasActivity.buscarCaronas).
        holder.tvValor.text = context.getString(
            R.string.procurar_valor_formato,
            String.format(Locale("pt", "BR"), "%.2f", resultado.valorTrecho)
        )

        if (!carona.motoristaFotoUrl.isNullOrEmpty()) {
            holder.ivFoto.load(carona.motoristaFotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person_default)
        }

        val motoristaId = carona.motoristaId
        if (motoristaId != null) {
            val abrirPerfil = { onPerfilClick(motoristaId) }
            holder.ivFoto.setOnClickListener { abrirPerfil() }
            holder.tvNomeMotorista.setOnClickListener { abrirPerfil() }
        }

        val jaSolicitada = carona.id != null && carona.id in solicitadas
        holder.btnSolicitar.isEnabled = !jaSolicitada
        holder.btnSolicitar.alpha = if (jaSolicitada) 0.6f else 1f
        holder.btnSolicitar.text = context.getString(
            if (jaSolicitada) R.string.procurar_vaga_ja_solicitada else R.string.procurar_botao_solicitar
        )
        holder.btnSolicitar.setOnClickListener {
            if (!jaSolicitada) onSolicitarClick(resultado)
        }
    }

    override fun getItemCount() = resultados.size
}

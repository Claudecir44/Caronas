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

// Cada card vem fechado (só rota, status e data) — toque no cabeçalho abre
// o corpo (valor pago, chat, cancelar). Só um fica aberto por vez: abrir
// outro fecha o anterior, mesmo espírito do card de perfil expansível do
// Match (cardPerfil/cardHeader/cardExpanded em TelaUsuarioActivity).
class MinhaViagemAdapter(
    private val viagens: List<Solicitacao>,
    private val onCancelarClick: (Solicitacao) -> Unit,
    private val onChatClick: (Solicitacao) -> Unit
) : RecyclerView.Adapter<MinhaViagemAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    private var posicaoAberta = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.headerMinhaViagem)
        val corpoExpandido: LinearLayout = view.findViewById(R.id.corpoExpandidoViagem)
        val tvExpandIcon: TextView = view.findViewById(R.id.tvExpandIconViagem)
        val tvRota: TextView = view.findViewById(R.id.tvRotaViagem)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusViagem)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraViagem)
        val layoutMotorista: LinearLayout = view.findViewById(R.id.layoutMotoristaViagem)
        val ivFotoMotorista: ImageView = view.findViewById(R.id.ivFotoMotoristaViagem)
        val tvNomeMotorista: TextView = view.findViewById(R.id.tvNomeMotoristaViagem)
        val tvCarroMotorista: TextView = view.findViewById(R.id.tvCarroMotoristaViagem)
        val tvValorPago: TextView = view.findViewById(R.id.tvValorPagoViagem)
        val btnChat: Button = view.findViewById(R.id.btnChatViagem)
        val btnCancelar: Button = view.findViewById(R.id.btnCancelarViagem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_minha_viagem, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viagem = viagens[position]
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            viagem.cidadeOrigem ?: "",
            viagem.cidadeDestino ?: ""
        )
        holder.tvDataHora.text = viagem.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        holder.tvValorPago.text = context.getString(
            R.string.minhas_viagens_valor_pago_formato,
            String.format(Locale("pt", "BR"), "%.2f", viagem.valorPago ?: 0.0)
        )

        // "concluída" é inferido aqui comparando a data da viagem com agora,
        // em vez de um status próprio guardado no Firestore (ver Solicitacao.kt).
        val jaOcorreu = (viagem.dataHoraPartida ?: 0L) < System.currentTimeMillis()
        val cancelada = viagem.status == "cancelada"

        val confirmada = viagem.status == "confirmada"

        when {
            cancelada -> {
                holder.tvStatus.text = context.getString(R.string.minhas_viagens_status_cancelada)
                holder.tvStatus.setBackgroundColor(0xFFD32F2F.toInt())
            }
            jaOcorreu -> {
                holder.tvStatus.text = context.getString(R.string.minhas_viagens_status_concluida)
                holder.tvStatus.setBackgroundColor(0xFF2E7D32.toInt())
            }
            confirmada -> {
                holder.tvStatus.text = context.getString(R.string.solicitacoes_status_confirmada)
                holder.tvStatus.setBackgroundColor(0xFF2E7D32.toInt())
            }
            else -> {
                holder.tvStatus.text = context.getString(R.string.minhas_viagens_status_solicitada)
                holder.tvStatus.setBackgroundColor(0xFF1E90FF.toInt())
            }
        }

        // Dados do motorista (foto, nome, carro) só depois de confirmada —
        // gravados no momento da confirmação (ver SolicitacaoRepository
        // .confirmarSolicitacao).
        if (confirmada) {
            holder.layoutMotorista.visibility = View.VISIBLE
            holder.tvNomeMotorista.text = viagem.motoristaNome
                ?: context.getString(R.string.procurar_motorista_desconhecido)

            val veiculo = viagem.veiculo
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

            if (!viagem.motoristaFotoUrl.isNullOrEmpty()) {
                holder.ivFotoMotorista.load(viagem.motoristaFotoUrl) {
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person_default)
                    error(R.drawable.ic_person_default)
                }
            } else {
                holder.ivFotoMotorista.setImageResource(R.drawable.ic_person_default)
            }
        } else {
            holder.layoutMotorista.visibility = View.GONE
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

        val podeCancelar = !cancelada && !jaOcorreu
        holder.btnCancelar.visibility = if (podeCancelar) View.VISIBLE else View.GONE
        holder.btnCancelar.setOnClickListener { onCancelarClick(viagem) }
        holder.btnChat.setOnClickListener { onChatClick(viagem) }
    }

    override fun getItemCount() = viagens.size
}

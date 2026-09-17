package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ViagemPassageiroAdminAdapter(
    private val solicitacoes: List<Solicitacao>
) : RecyclerView.Adapter<ViagemPassageiroAdminAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRota: TextView = view.findViewById(R.id.tvRotaViagemPassageiroAdmin)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusViagemPassageiroAdmin)
        val tvPassageiro: TextView = view.findViewById(R.id.tvPassageiroViagemPassageiroAdmin)
        val tvMotorista: TextView = view.findViewById(R.id.tvMotoristaViagemPassageiroAdmin)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraViagemPassageiroAdmin)
        val tvValor: TextView = view.findViewById(R.id.tvValorViagemPassageiroAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_passageiro_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val solicitacao = solicitacoes[position]
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato, solicitacao.cidadeOrigem ?: "", solicitacao.cidadeDestino ?: ""
        )
        holder.tvPassageiro.text = context.getString(R.string.admin_passageiro_formato, solicitacao.passageiroNome ?: "")
        holder.tvMotorista.text = context.getString(R.string.admin_motorista_formato, solicitacao.motoristaNome ?: "")
        holder.tvDataHora.text = solicitacao.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        holder.tvValor.text = context.getString(
            R.string.procurar_valor_formato, String.format(Locale("pt", "BR"), "%.2f", solicitacao.valorPago ?: 0.0)
        )
        holder.tvStatus.text = when (solicitacao.status) {
            "confirmada" -> context.getString(R.string.admin_status_confirmada)
            "cancelada" -> context.getString(R.string.minhas_ofertas_status_cancelada)
            else -> context.getString(R.string.admin_status_solicitada)
        }
    }

    override fun getItemCount() = solicitacoes.size
}

package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ViagemAdminAdapter(
    private val caronas: List<Carona>
) : RecyclerView.Adapter<ViagemAdminAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRota: TextView = view.findViewById(R.id.tvRotaViagemAdmin)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusViagemAdmin)
        val tvMotorista: TextView = view.findViewById(R.id.tvMotoristaViagemAdmin)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraViagemAdmin)
        val tvVagas: TextView = view.findViewById(R.id.tvVagasViagemAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val carona = caronas[position]
        val context = holder.itemView.context

        holder.tvRota.text = if (carona.paradas.size > 2) {
            carona.paradas.joinToString(" → ") { it.cidade ?: "" }
        } else {
            context.getString(R.string.procurar_rota_formato, carona.cidadeOrigem ?: "", carona.cidadeDestino ?: "")
        }
        holder.tvMotorista.text = context.getString(R.string.admin_motorista_formato, carona.motoristaNome ?: "")
        holder.tvDataHora.text = carona.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        holder.tvVagas.text = context.getString(R.string.procurar_vagas_formato, carona.vagas)
        holder.tvStatus.text = context.getString(
            when {
                carona.status == "cancelada" -> R.string.minhas_ofertas_status_cancelada
                StatusViagemUtil.jaConcluida(carona.dataHoraPartida) -> R.string.minhas_ofertas_status_concluida
                else -> R.string.minhas_ofertas_status_ativa
            }
        )
    }

    override fun getItemCount() = caronas.size
}

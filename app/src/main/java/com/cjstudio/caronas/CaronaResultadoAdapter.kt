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
    private val caronas: List<Carona>,
    private val onSolicitarClick: (Carona) -> Unit
) : RecyclerView.Adapter<CaronaResultadoAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoMotoristaResultado)
        val tvRota: TextView = view.findViewById(R.id.tvRotaResultado)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraResultado)
        val tvNomeMotorista: TextView = view.findViewById(R.id.tvNomeMotoristaResultado)
        val tvVagas: TextView = view.findViewById(R.id.tvVagasResultado)
        val tvValor: TextView = view.findViewById(R.id.tvValorResultado)
        val btnSolicitar: android.widget.Button = view.findViewById(R.id.btnSolicitarResultado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_carona_resultado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val carona = caronas[position]
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            carona.cidadeOrigem ?: "",
            carona.cidadeDestino ?: ""
        )
        holder.tvDataHora.text = carona.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        holder.tvNomeMotorista.text = carona.motoristaNome ?: context.getString(R.string.procurar_motorista_desconhecido)
        holder.tvVagas.text = context.getString(R.string.procurar_vagas_formato, carona.vagas)
        holder.tvValor.text = context.getString(
            R.string.procurar_valor_formato,
            String.format(Locale("pt", "BR"), "%.2f", carona.valorPorVaga ?: 0.0)
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

        holder.btnSolicitar.setOnClickListener { onSolicitarClick(carona) }
    }

    override fun getItemCount() = caronas.size
}

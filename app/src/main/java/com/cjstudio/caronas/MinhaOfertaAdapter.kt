package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MinhaOfertaAdapter(
    private val ofertas: List<Carona>,
    private val onEditarClick: (Carona) -> Unit,
    private val onExcluirClick: (Carona) -> Unit
) : RecyclerView.Adapter<MinhaOfertaAdapter.ViewHolder>() {

    private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRota: TextView = view.findViewById(R.id.tvRotaOferta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusOferta)
        val tvDataHora: TextView = view.findViewById(R.id.tvDataHoraOferta)
        val tvVagas: TextView = view.findViewById(R.id.tvVagasOferta)
        val tvValor: TextView = view.findViewById(R.id.tvValorOferta)
        val btnEditar: Button = view.findViewById(R.id.btnEditarOferta)
        val btnExcluir: Button = view.findViewById(R.id.btnExcluirOferta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_minha_oferta, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val oferta = ofertas[position]
        val context = holder.itemView.context

        holder.tvRota.text = context.getString(
            R.string.procurar_rota_formato,
            oferta.cidadeOrigem ?: "",
            oferta.cidadeDestino ?: ""
        )
        holder.tvDataHora.text = oferta.dataHoraPartida?.let { formatoDataHora.format(it) } ?: ""
        holder.tvVagas.text = context.getString(R.string.procurar_vagas_formato, oferta.vagas)
        holder.tvValor.text = context.getString(
            R.string.procurar_valor_formato,
            String.format(Locale("pt", "BR"), "%.2f", oferta.valorPorVaga ?: 0.0)
        )
        holder.tvStatus.text = context.getString(
            if (oferta.status == "cancelada") R.string.minhas_ofertas_status_cancelada else R.string.minhas_ofertas_status_ativa
        )

        holder.btnEditar.setOnClickListener { onEditarClick(oferta) }
        holder.btnExcluir.setOnClickListener { onExcluirClick(oferta) }
    }

    override fun getItemCount() = ofertas.size
}

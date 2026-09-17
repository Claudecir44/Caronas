package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Lista de pagamentos na seção "Financeiro" do dashboard (ver
// AdministracaoCaronasActivity.mostrarFinanceiro) — mesmo item_pagamento_
// motorista.xml usado quando essa tela ainda era uma Activity separada.
class PagamentoMotoristaAdminAdapter(
    private val pagamentos: List<PagamentoMotorista>
) : RecyclerView.Adapter<PagamentoMotoristaAdminAdapter.ViewHolder>() {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNome: TextView = view.findViewById(R.id.tvNome)
        val tvEmail: TextView = view.findViewById(R.id.tvEmail)
        val tvValor: TextView = view.findViewById(R.id.tvValor)
        val tvDataCompra: TextView = view.findViewById(R.id.tvDataCompra)
        val tvExpiraEm: TextView = view.findViewById(R.id.tvExpiraEm)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pagamento_motorista, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = pagamentos[position]
        val context = holder.itemView.context
        val agora = System.currentTimeMillis()
        val ativo = p.expiraEm != null && p.expiraEm!! > agora

        holder.tvNome.text = p.usuarioNome?.ifEmpty { null } ?: context.getString(R.string.financeiro_motorista_padrao)
        holder.tvEmail.text = p.usuarioEmail ?: ""
        holder.tvValor.text = context.getString(R.string.financeiro_valor_format, p.valor)
        holder.tvDataCompra.text = p.dataCompra?.let { sdf.format(Date(it)) } ?: "-"
        holder.tvExpiraEm.text = p.expiraEm?.let { sdf.format(Date(it)) } ?: "-"
        holder.tvStatus.setText(if (ativo) R.string.financeiro_status_ativo else R.string.financeiro_status_expirado)
        holder.tvStatus.setTextColor(if (ativo) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt())
    }

    override fun getItemCount() = pagamentos.size
}

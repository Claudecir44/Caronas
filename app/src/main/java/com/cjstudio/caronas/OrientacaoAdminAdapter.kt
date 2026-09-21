package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Lista de "Orientações" do painel Administração: cada seção (título, corpo) é
// um card que abre ao tocar no cabeçalho — vários podem ficar abertos ao mesmo
// tempo (é um guia de consulta, dá pra comparar duas seções). O conteúdo vem
// de OrientacoesAdminConteudo.
class OrientacaoAdminAdapter(
    private val secoes: List<Pair<String, String>>
) : RecyclerView.Adapter<OrientacaoAdminAdapter.ViewHolder>() {

    private val abertas = mutableSetOf<Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.headerOrientacaoAdmin)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloOrientacaoAdmin)
        val tvSeta: TextView = view.findViewById(R.id.tvSetaOrientacaoAdmin)
        val tvCorpo: TextView = view.findViewById(R.id.tvCorpoOrientacaoAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_orientacao_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (titulo, corpo) = secoes[position]
        val aberta = position in abertas
        holder.tvTitulo.text = titulo
        holder.tvCorpo.text = corpo
        holder.tvCorpo.visibility = if (aberta) View.VISIBLE else View.GONE
        holder.tvSeta.text = if (aberta) "▲" else "▼"
        holder.header.setOnClickListener {
            val posicao = holder.bindingAdapterPosition
            if (posicao == RecyclerView.NO_POSITION) return@setOnClickListener
            if (!abertas.add(posicao)) abertas.remove(posicao)
            notifyItemChanged(posicao)
        }
    }

    override fun getItemCount() = secoes.size
}

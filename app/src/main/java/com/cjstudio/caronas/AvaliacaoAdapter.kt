package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Lista de avaliações recebidas, na tela de perfil público (mais recentes
// primeiro — já vem nessa ordem de AvaliacaoRepository.buscarAvaliacoesRecebidas).
class AvaliacaoAdapter(
    private val avaliacoes: List<Avaliacao>
) : RecyclerView.Adapter<AvaliacaoAdapter.ViewHolder>() {

    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ratingBar: RatingBar = view.findViewById(R.id.ratingBarItemAvaliacao)
        val tvData: TextView = view.findViewById(R.id.tvDataItemAvaliacao)
        val tvComentario: TextView = view.findViewById(R.id.tvComentarioItemAvaliacao)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_avaliacao, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val avaliacao = avaliacoes[position]
        holder.ratingBar.rating = avaliacao.nota.toFloat()
        holder.tvData.text = avaliacao.criadoEm?.let { formatoData.format(it) } ?: ""
        val comentario = avaliacao.comentario
        if (comentario.isNullOrBlank()) {
            holder.tvComentario.visibility = View.GONE
        } else {
            holder.tvComentario.visibility = View.VISIBLE
            holder.tvComentario.text = comentario
        }
    }

    override fun getItemCount() = avaliacoes.size
}

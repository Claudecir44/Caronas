package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Lista só os COMENTÁRIOS das avaliações recebidas pelo usuário logado —
// sem nota, sem data, sem quem avaliou, exatamente como pedido: só o texto
// do comentário, um abaixo do outro, com um traço decorativo entre eles
// (ver TelaCaronasActivity.mostrarAvaliacoes). Diferente de AvaliacaoAdapter
// (usado na tela de Perfil Público), que mostra nota + data.
class ComentarioAvaliacaoAdapter(
    private val comentarios: List<String>
) : RecyclerView.Adapter<ComentarioAvaliacaoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSeparador: TextView = view.findViewById(R.id.tvSeparadorComentario)
        val tvComentario: TextView = view.findViewById(R.id.tvComentarioAvaliacao)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comentario_avaliacao, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvComentario.text = comentarios[position]
        // Sem traço antes do primeiro comentário — só entre um e outro.
        holder.tvSeparador.visibility = if (position == 0) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = comentarios.size
}

package com.cjstudio.caronas

import android.content.Context
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

// Diálogo de avaliação (nota 1-5 + comentário opcional) — reutilizado tanto
// por TelaCaronasActivity (passageiro avalia motorista) quanto por
// TelaCaronasActivity (motorista avalia passageiro, seção Minhas Ofertas), mesmo formulário nos
// dois casos, só muda quem é avaliado.
object AvaliacaoDialogUtil {

    fun mostrar(
        context: Context,
        nomeAvaliado: String,
        onEnviar: (nota: Int, comentario: String?) -> Unit
    ) {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_avaliar, null)
        val tvSubtitulo = view.findViewById<TextView>(R.id.tvSubtituloAvaliar)
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBarAvaliar)
        val etComentario = view.findViewById<TextInputEditText>(R.id.etComentarioAvaliar)
        val btnEnviar = view.findViewById<android.widget.Button>(R.id.btnEnviarAvaliacao)

        tvSubtitulo.text = context.getString(R.string.avaliar_subtitulo_formato, nomeAvaliado)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        btnEnviar.setOnClickListener {
            val nota = ratingBar.rating.toInt()
            if (nota < 1) {
                Toast.makeText(context, R.string.avaliar_erro_nota, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val comentario = etComentario.text?.toString()?.trim()
            dialog.dismiss()
            onEnviar(nota, comentario)
        }

        dialog.show()
    }
}

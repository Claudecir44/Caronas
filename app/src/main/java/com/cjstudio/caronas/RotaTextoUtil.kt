package com.cjstudio.caronas

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

// Título "Origem → Destino" dos cards de carona: cidade de partida em verde e o
// resto (seta e cidade de chegada) na cor do próprio TextView, que nos layouts
// é preto. Compartilhado por Minhas Ofertas, Minhas Viagens e pelos resultados
// da busca — a origem é sempre a primeira parte de procurar_rota_formato.
object RotaTextoUtil {
    private val COR_ORIGEM: Int = Color.parseColor("#2E7D32")

    fun formatar(context: Context, origem: String?, destino: String?): CharSequence {
        val cidadeOrigem = origem ?: ""
        val texto = context.getString(R.string.procurar_rota_formato, cidadeOrigem, destino ?: "")
        val spannable = SpannableString(texto)
        if (cidadeOrigem.isNotEmpty() && texto.startsWith(cidadeOrigem)) {
            spannable.setSpan(ForegroundColorSpan(COR_ORIGEM), 0, cidadeOrigem.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}

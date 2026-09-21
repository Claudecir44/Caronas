package com.cjstudio.caronas

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

// Título "Origem → Destino" dos cards de carona: cidade de partida em verde e o
// resto (seta e cidade de chegada) na cor do próprio TextView, que nos layouts
// é preto. Compartilhado por Minhas Ofertas, Minhas Viagens, resultados da
// busca e Solicitações Recebidas — a origem é sempre a primeira parte de
// procurar_rota_formato. Solicitações Recebidas também pinta a chegada de
// vermelho e Minhas Ofertas ainda ativas pinta de verde-água (corDestino).
object RotaTextoUtil {
    private val COR_ORIGEM: Int = Color.parseColor("#2E7D32")
    val COR_DESTINO_VERMELHO: Int = Color.parseColor("#D32F2F")
    // Mesmo verde-água dos botões (botao_verde_agua) — chegada das ofertas ainda ativas.
    val COR_DESTINO_VERDE_AGUA: Int = Color.parseColor("#00897B")

    fun formatar(context: Context, origem: String?, destino: String?, corDestino: Int? = null): CharSequence {
        val cidadeOrigem = origem ?: ""
        val cidadeDestino = destino ?: ""
        val texto = context.getString(R.string.procurar_rota_formato, cidadeOrigem, cidadeDestino)
        val spannable = SpannableString(texto)
        if (cidadeOrigem.isNotEmpty() && texto.startsWith(cidadeOrigem)) {
            spannable.setSpan(ForegroundColorSpan(COR_ORIGEM), 0, cidadeOrigem.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // O destino é sempre o final do texto (procurar_rota_formato termina em %2$s).
        if (corDestino != null && cidadeDestino.isNotEmpty() && texto.endsWith(cidadeDestino)) {
            spannable.setSpan(ForegroundColorSpan(corDestino), texto.length - cidadeDestino.length, texto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}

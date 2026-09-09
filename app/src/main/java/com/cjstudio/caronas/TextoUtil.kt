package com.cjstudio.caronas

import java.text.Normalizer
import java.util.Locale

object TextoUtil {

    // Normaliza texto de cidade pra comparar busca com oferta mesmo quando
    // digitadas de formas diferentes (maiúsculas/minúsculas, com/sem
    // acento, espaços extras) — usado tanto ao publicar quanto ao buscar
    // carona (ver Carona.cidadeOrigemBusca/cidadeDestinoBusca).
    fun normalizar(texto: String): String {
        val semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return semAcento.lowercase(Locale.ROOT)
    }
}

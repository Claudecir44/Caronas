package com.cjstudio.caronas

import android.content.Context
import android.widget.TextView
import android.view.View
import kotlin.math.roundToInt

// Tempo aproximado de viagem a partir da distância já aproximada do app
// (DistanciaUtil: linha reta × fator de estrada, sem API paga de rotas). Sem
// trânsito em tempo real, então é uma estimativa por velocidade média
// conforme o tamanho do trecho: trecho curto é quase todo urbano (mais
// lento), trecho longo é quase todo rodovia.
object TempoViagemUtil {

    private const val ATE_KM_URBANO = 20.0
    private const val ATE_KM_MEDIO = 100.0
    private const val VELOCIDADE_URBANO_KMH = 35.0
    private const val VELOCIDADE_MEDIO_KMH = 60.0
    private const val VELOCIDADE_RODOVIA_KMH = 75.0
    private const val ARREDONDAR_MINUTOS = 5

    // Arredondado de 5 em 5 minutos (é uma aproximação — "2h07" passaria uma
    // falsa precisão), com piso de 5 minutos.
    fun minutosEstimados(distanciaKm: Double): Int {
        val velocidade = when {
            distanciaKm <= ATE_KM_URBANO -> VELOCIDADE_URBANO_KMH
            distanciaKm <= ATE_KM_MEDIO -> VELOCIDADE_MEDIO_KMH
            else -> VELOCIDADE_RODOVIA_KMH
        }
        val minutos = distanciaKm / velocidade * 60.0
        return maxOf((minutos / ARREDONDAR_MINUTOS).roundToInt() * ARREDONDAR_MINUTOS, ARREDONDAR_MINUTOS)
    }

    // Partida + tempo estimado. Sem distância, a própria hora de partida.
    fun chegadaPrevistaEm(dataHoraPartida: Long, distanciaKm: Double?): Long {
        if (distanciaKm == null || distanciaKm <= 0.0) return dataHoraPartida
        return dataHoraPartida + minutosEstimados(distanciaKm) * 60_000L
    }

    // "Tempo aproximado: 2h30" — null quando não há distância (ofertas
    // antigas sem distanciaKm, geocodificação que falhou).
    fun formatar(context: Context, distanciaKm: Double?): String? {
        if (distanciaKm == null || distanciaKm <= 0.0) return null
        val minutos = minutosEstimados(distanciaKm)
        val horas = minutos / 60
        val resto = minutos % 60
        val duracao = when {
            horas == 0 -> context.getString(R.string.tempo_viagem_minutos, resto)
            resto == 0 -> context.getString(R.string.tempo_viagem_horas, horas)
            else -> context.getString(R.string.tempo_viagem_horas_minutos, horas, resto)
        }
        return context.getString(R.string.tempo_viagem_formato, duracao)
    }

    // Preenche o TextView do card (já com a mesma fonte/cor da data, só em
    // negrito — ver layouts) ou o esconde se não houver distância.
    fun preencher(textView: TextView, distanciaKm: Double?) {
        val texto = formatar(textView.context, distanciaKm)
        textView.text = texto ?: ""
        textView.visibility = if (texto != null) View.VISIBLE else View.GONE
    }
}

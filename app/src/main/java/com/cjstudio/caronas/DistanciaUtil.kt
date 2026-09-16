package com.cjstudio.caronas

import android.content.Context
import android.location.Geocoder
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Sugestão de valor no espírito da BlaBlaCar: rateio do custo da viagem
// (combustível + desgaste do carro), não preço de mercado. A BlaBlaCar
// calcula em cima da distância real de rodovia (API paga de rotas); aqui
// aproximamos com a distância em linha reta entre as cidades (Geocoder, sem
// custo) × um fator de correção pra estrada. Menos preciso, mas não depende
// de nenhuma chave de API paga.
object DistanciaUtil {

    private const val FATOR_ROTA_ESTRADA = 1.3
    private const val RAIO_TERRA_KM = 6371.0

    // Tarifa base calibrada pra ficar perto da referência da BlaBlaCar (o
    // valor original de R$0,12/km estava saindo pela metade do preço real
    // dela — +75% corrige isso pros dias normais). +30% adicional pedido
    // depois — como o adicional de fim de semana/feriado é um multiplicador
    // EM CIMA desta base, subir a base em 30% sobe os dois valores em 30%
    // "respectivamente": dia de semana fica 30% maior que antes, e fim de
    // semana/feriado também fica 30% maior que o que era antes (não só 30%
    // maior que o dia de semana — essa diferença relativa continua sendo a
    // de ADICIONAL_FIM_DE_SEMANA_FERIADO, inalterada).
    private const val TARIFA_POR_KM_REAIS = 0.12 * 1.75 * 1.30
    private const val ADICIONAL_FIM_DE_SEMANA_FERIADO = 0.30
    private const val VALOR_MINIMO_REAIS = 10.0

    data class Sugestao(val distanciaKm: Double, val valorSugerido: Double, val fimDeSemanaOuFeriado: Boolean)

    // Coordenada já resolvida de um ponto (cidade ou endereço) — separado de
    // android.location.Address pra não vazar esse tipo pro resto do app.
    data class LatLngPonto(val latitude: Double, val longitude: Double)

    // Geocodifica UM ponto (cidade ou endereço completo, o Geocoder aceita
    // os dois). Isolado do cálculo de distância pra poder geocodificar cada
    // parada de uma rota com paradas UMA vez só (ver calcularRota) — antes,
    // calcularSugestao geocodificava origem e destino do zero a cada
    // chamada; numa rota A→B→C→D isso repetiria B e C duas vezes cada.
    // Bloqueante (API síncrona do Geocoder, disponível desde a minSdk 24 do
    // projeto) — sempre chamar de dentro de Dispatchers.IO.
    fun geocodificar(context: Context, local: String): LatLngPonto? {
        val geocoder = Geocoder(context)
        @Suppress("DEPRECATION")
        val endereco = geocoder.getFromLocationName(local, 1)?.firstOrNull() ?: return null
        return LatLngPonto(endereco.latitude, endereco.longitude)
    }

    fun distanciaKm(a: LatLngPonto, b: LatLngPonto): Double {
        return distanciaHaversineKm(a.latitude, a.longitude, b.latitude, b.longitude) * FATOR_ROTA_ESTRADA
    }

    fun ehFimDeSemanaOuFeriado(data: Calendar): Boolean {
        val diaSemana = data.get(Calendar.DAY_OF_WEEK)
        if (diaSemana == Calendar.SATURDAY || diaSemana == Calendar.SUNDAY) return true
        return ehFeriadoNacional(data)
    }

    // Tarifa + arredondamento (múltiplo de R$5, piso de R$10 — mais fácil de
    // negociar/dividir do que um valor quebrado) pra uma distância já
    // calculada. Separado de calcularSugestao pra poder ser reaproveitado
    // tanto pra um trecho único quanto por trecho dentro de calcularRota.
    fun valorParaDistancia(distanciaKm: Double, dataViagem: Calendar): Double {
        val tarifaPorKm = if (ehFimDeSemanaOuFeriado(dataViagem)) {
            TARIFA_POR_KM_REAIS * (1 + ADICIONAL_FIM_DE_SEMANA_FERIADO)
        } else {
            TARIFA_POR_KM_REAIS
        }
        val valorBruto = distanciaKm * tarifaPorKm
        return maxOf((valorBruto / 5.0).roundToInt() * 5.0, VALOR_MINIMO_REAIS)
    }

    // "dataViagem" define se entra o adicional de fim de semana/feriado.
    // Retorna null se alguma das duas cidades não for encontrada.
    suspend fun calcularSugestao(
        context: Context,
        cidadeOrigem: String,
        cidadeDestino: String,
        dataViagem: Calendar
    ): Sugestao? {
        val origem = geocodificar(context, cidadeOrigem) ?: return null
        val destino = geocodificar(context, cidadeDestino) ?: return null
        val distancia = distanciaKm(origem, destino)
        return Sugestao(distancia, valorParaDistancia(distancia, dataViagem), ehFimDeSemanaOuFeriado(dataViagem))
    }

    // Geocodifica cada ponto UMA vez só e devolve o preço SEMPRE a partir da
    // ORIGEM (pontos[0]) — pontos[0]->pontos[1], pontos[0]->pontos[2], ...,
    // pontos[0]->pontos.last() — nunca encadeado entre pontos intermediários
    // (ver OferecerCaronaActivity.calcularSugestao: uma parada no meio da
    // rota é um desvio pra pegar OUTRO passageiro; o preço de quem embarca
    // na origem até qualquer ponto seguinte não pode depender de quantas
    // paradas existem antes dele, só da distância direta origem->ponto).
    // Retorna 1 Sugestao por ponto em pontos.drop(1), na mesma ordem.
    // Retorna null se pontos.size < 2 ou algum ponto não for geocodificado.
    suspend fun calcularSugestoesAPartirDaOrigem(context: Context, pontos: List<String>, dataViagem: Calendar): List<Sugestao>? {
        if (pontos.size < 2) return null
        val coordenadas = pontos.map { geocodificar(context, it) ?: return null }
        val origem = coordenadas.first()
        val fimDeSemanaOuFeriado = ehFimDeSemanaOuFeriado(dataViagem)
        return coordenadas.drop(1).map { ponto ->
            val distancia = distanciaKm(origem, ponto)
            Sugestao(distancia, valorParaDistancia(distancia, dataViagem), fimDeSemanaOuFeriado)
        }
    }

    private fun distanciaHaversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RAIO_TERRA_KM * c
    }

    // Feriados nacionais fixos + os móveis mais comuns (calculados a partir
    // da Páscoa) — cobre o essencial sem precisar de uma tabela/API externa
    // de feriados.
    private fun ehFeriadoNacional(data: Calendar): Boolean {
        val mes = data.get(Calendar.MONTH)
        val dia = data.get(Calendar.DAY_OF_MONTH)

        val feriadosFixos = setOf(
            Calendar.JANUARY to 1,
            Calendar.APRIL to 21,
            Calendar.MAY to 1,
            Calendar.SEPTEMBER to 7,
            Calendar.OCTOBER to 12,
            Calendar.NOVEMBER to 2,
            Calendar.NOVEMBER to 15,
            Calendar.DECEMBER to 25
        )
        if ((mes to dia) in feriadosFixos) return true

        val diaSoData = (data.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pascoa = calcularDomingoDePascoa(data.get(Calendar.YEAR))
        val diasEmRelacaoAPascoa = ((diaSoData.timeInMillis - pascoa.timeInMillis) / 86_400_000L).toInt()

        // Carnaval (terça-feira, 47 dias antes da Páscoa), Sexta-feira Santa
        // (2 dias antes) e Corpus Christi (60 dias depois).
        return diasEmRelacaoAPascoa == -47 || diasEmRelacaoAPascoa == -2 || diasEmRelacaoAPascoa == 60
    }

    // Algoritmo de Gauss/Meeus pra data do Domingo de Páscoa.
    private fun calcularDomingoDePascoa(ano: Int): Calendar {
        val a = ano % 19
        val b = ano / 100
        val c = ano % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31
        val dia = ((h + l - 7 * m + 114) % 31) + 1
        return Calendar.getInstance().apply {
            clear()
            set(ano, mes - 1, dia)
        }
    }
}

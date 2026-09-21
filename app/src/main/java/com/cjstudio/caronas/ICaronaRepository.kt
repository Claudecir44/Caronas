package com.cjstudio.caronas

import java.util.Calendar

interface ICaronaRepository {
    // Preenche motoristaId/motoristaNome/motoristaFotoUrl com o usuário
    // logado antes de gravar.
    suspend fun publicarCarona(carona: Carona): Result<String>

    // Caronas ativas que batem com origem, destino (comparados
    // normalizados, ver TextoUtil) e a data (dia inteiro, ignora hora).
    suspend fun buscarCaronas(cidadeOrigem: String, cidadeDestino: String, data: Calendar): Result<List<Carona>>

    // Todas as caronas publicadas pelo motorista logado (qualquer status),
    // mais recentes primeiro — usado pela tela "Minhas Ofertas".
    suspend fun buscarMinhasOfertas(): Result<List<Carona>>

    // Atualiza uma oferta já publicada por completo — rota (origem,
    // paradas intermediárias e destino, cada uma já com "ordem" certa,
    // mesmo formato de OferecerCaronaActivity.montarRota), data/hora, vagas
    // e valor por vaga. Reconstrói cidadeOrigem/cidadeDestino/cidadesBusca
    // a partir de "rota", mesma normalização de publicarCarona — sem isso,
    // uma edição de rota não apareceria em buscas por trecho novo.
    suspend fun atualizarOferta(caronaId: String, rota: List<ParadaRota>, dataHoraPartida: Long, vagas: Int, valorPorVaga: Double, distanciaKm: Double?): Result<Unit>

    suspend fun excluirOferta(caronaId: String): Result<Unit>
}

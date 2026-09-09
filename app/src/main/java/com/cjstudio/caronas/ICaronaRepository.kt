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

    // Atualiza só data/hora, vagas e valor por vaga de uma oferta já
    // publicada — rota não é editável (mudar a rota é publicar outra oferta).
    suspend fun atualizarOferta(caronaId: String, dataHoraPartida: Long, vagas: Int, valorPorVaga: Double): Result<Unit>

    suspend fun excluirOferta(caronaId: String): Result<Unit>
}

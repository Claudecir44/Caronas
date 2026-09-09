package com.cjstudio.caronas

interface ISolicitacaoRepository {
    // Cria a solicitação a partir da carona escolhida (copia rota/data/valor
    // pra dentro do documento) e preenche passageiroId com o usuário logado.
    suspend fun solicitarVaga(carona: Carona): Result<String>

    // Todas as solicitações feitas pelo usuário logado, mais recentes primeiro.
    suspend fun buscarMinhasSolicitacoes(): Result<List<Solicitacao>>

    // Todas as solicitações recebidas pelo motorista logado (de todas as
    // suas ofertas), mais recentes primeiro — usado por "Minhas Ofertas".
    suspend fun buscarSolicitacoesRecebidas(): Result<List<Solicitacao>>

    suspend fun confirmarSolicitacao(solicitacaoId: String): Result<Unit>

    suspend fun cancelarSolicitacao(solicitacaoId: String): Result<Unit>
}

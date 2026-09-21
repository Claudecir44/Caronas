package com.cjstudio.caronas

import kotlinx.coroutines.flow.Flow

interface ISolicitacaoRepository {
    // Cria a solicitação a partir da carona escolhida e do TRECHO escolhido
    // pelo passageiro (índices das paradas de embarque/desembarque em
    // carona.paradas — quando a carona não tem parada no meio, é sempre 0 e
    // paradas.lastIndex, o trecho inteiro, igual ao comportamento antigo).
    // valorTrecho é o preço já calculado (DistanciaUtil) pra esse trecho
    // específico, não necessariamente carona.valorPorVaga inteiro. Antes de
    // criar, checa vagasDisponiveis pro trecho pedido e falha se não houver
    // — ver a ressalva de concorrência no comentário de vagasDisponiveis.
    suspend fun solicitarVaga(carona: Carona, indiceOrigem: Int, indiceDestino: Int, valorTrecho: Double, distanciaTrechoKm: Double?): Result<String>

    // Vagas ainda livres pro trecho [indiceOrigem, indiceDestino) de uma
    // carona — carona.vagas (capacidade total do carro) menos a maior
    // ocupação entre as pernas desse trecho (uma solicitação ativa cujo
    // intervalo sobrepõe qualquer perna do trecho pedido ocupa 1 vaga em
    // cada perna que ela cobre). Leitura simples (não transação): o
    // Firestore não permite uma *query* dentro de uma transação, só get()
    // por referência já conhecida, e não dá pra saber de antemão quais
    // documentos de ocupação existem — ou seja, dois pedidos simultâneos
    // pela última vaga do mesmo trecho ainda podem, em teoria, os dois
    // passar. Isso não é pior que o comportamento anterior (que não
    // verificava vaga nenhuma) — é uma melhoria real, só não é 100% à prova
    // de concorrência. Ver caronas/{id}/ocupacao em firestore.rules.
    suspend fun vagasDisponiveis(carona: Carona, indiceOrigem: Int, indiceDestino: Int): Result<Int>

    // Todas as solicitações feitas pelo usuário logado, mais recentes primeiro.
    suspend fun buscarMinhasSolicitacoes(): Result<List<Solicitacao>>

    // Todas as solicitações recebidas pelo motorista logado (de todas as
    // suas ofertas), mais recentes primeiro — usado por "Minhas Ofertas".
    suspend fun buscarSolicitacoesRecebidas(): Result<List<Solicitacao>>

    suspend fun confirmarSolicitacao(solicitacaoId: String): Result<Unit>

    suspend fun cancelarSolicitacao(solicitacaoId: String): Result<Unit>

    // Motorista desiste de uma viagem que já tinha confirmado (botão
    // "Cancelar" em Solicitações Recebidas, só aparece pra viagem
    // confirmada e ainda não ocorrida — ver SolicitacaoRecebidaAdapter).
    // Mesmo efeito de cancelarSolicitacao (status "cancelada" + libera a
    // vaga), só muda quem fez e o campo canceladoPor gravado, usado pela
    // Cloud Function notificarViagemCancelada pra avisar o OUTRO lado.
    suspend fun cancelarComoMotorista(solicitacaoId: String): Result<Unit>

    // Apaga o documento de vez (toque e segure numa solicitação recebida) —
    // diferente de cancelar, que só muda o status e mantém no histórico.
    suspend fun excluirSolicitacao(solicitacaoId: String): Result<Unit>

    // Contagem ao vivo de solicitações PENDENTES (status "solicitada") MAIS
    // cancelamentos feitos pelo passageiro que o motorista ainda não viu —
    // badge do botão "Minhas Ofertas". Ver marcarCancelamentosComoVistos/
    // Solicitacao.canceladoVisto.
    fun escutarContagemPendentes(): Flow<Int>

    // Contagem ao vivo de solicitações CONFIRMADAS MAIS canceladas pelo
    // motorista que o passageiro logado ainda não viu — badge do botão
    // "Minhas Viagens". Ver marcarConfirmacoesComoVistas/
    // Solicitacao.confirmacaoVista/canceladoVisto.
    fun escutarContagemConfirmacoesNaoVistas(): Flow<Int>

    // Marca como vistas as confirmações E os cancelamentos (feitos pelo
    // MOTORISTA) que o passageiro logado ainda não tinha visto — chamado ao
    // abrir "Minhas Viagens".
    suspend fun marcarConfirmacoesComoVistas(): Result<Unit>

    // Marca como vistos os cancelamentos feitos pelo PASSAGEIRO que o
    // motorista logado ainda não tinha visto — chamado ao abrir
    // "Solicitações Recebidas" (dentro de "Minhas Ofertas").
    suspend fun marcarCancelamentosComoVistos(): Result<Unit>
}

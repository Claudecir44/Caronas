package com.cjstudio.caronas

interface IAvaliacaoRepository {
    // avaliadoId é sempre "a outra pessoa" da solicitacao (motorista, se
    // quem avalia é o passageiro, e vice-versa) — decidido pelo chamador,
    // não aqui, porque só ele sabe se é a tela do passageiro ou do motorista.
    suspend fun avaliar(solicitacao: Solicitacao, avaliadoId: String, nota: Int, comentario: String?): Result<Unit>

    // Todas as avaliações recebidas por um usuário, mais recentes primeiro —
    // usada tanto pra listar na tela de perfil público quanto pra calcular a
    // média (não guardamos média/contador agregado em Usuario.kt; a mesma
    // consulta que lista já dá pra tirar a média, sem leitura nem transação
    // extra).
    suspend fun buscarAvaliacoesRecebidas(usuarioId: String): Result<List<Avaliacao>>

    // Usada pra esconder o botão "Avaliar" depois que a pessoa já avaliou
    // aquela viagem.
    suspend fun jaAvaliei(solicitacaoId: String): Result<Boolean>

    // Ids de todas as solicitações que o usuário logado já avaliou (como
    // avaliador) — usada ao montar a lista de Minhas Viagens/Solicitações
    // Recebidas pra decidir de uma vez só (1 consulta) quais cards mostram o
    // botão "Avaliar", em vez de 1 consulta por card (ver jaAvaliei acima).
    suspend fun buscarSolicitacoesJaAvaliadas(): Result<Set<String>>
}

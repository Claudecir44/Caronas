package com.cjstudio.caronas

import kotlinx.coroutines.flow.Flow

interface IChatCaronaRepository {
    // Acha a conversa já existente pra essa solicitação ou cria uma nova
    // — só se o chat estiver aberto (ver ChatUtil); fechado e sem conversa
    // falha com ChatIndisponivelException.
    suspend fun buscarOuCriarConversa(solicitacao: Solicitacao): Result<ConversaCarona>

    // Mensagens da conversa em tempo real, mais antigas primeiro.
    fun escutarMensagens(conversaId: String): Flow<List<MensagemCarona>>

    suspend fun enviarMensagem(conversa: ConversaCarona, texto: String): Result<Unit>

    // Todas as conversas do usuário logado (como motorista ou passageiro)
    // em tempo real, mais recentes primeiro — usado pelo botão "Chat".
    fun escutarMinhasConversas(): Flow<List<ConversaCarona>>

    suspend fun marcarConversaComoLida(conversa: ConversaCarona): Result<Unit>

    // Solicitação dessa conversa em tempo real (null = apagada) — a tela do
    // chat calcula o estado (aberto/cancelado/encerrado, ver ChatUtil) a
    // partir dela. Mensagens NÃO são apagadas por ninguém (nem "só pra mim"
    // nem "pra todos") e a conversa também não: depois de fechado o
    // histórico fica só pra leitura dos dois (ver firestore.rules).
    fun escutarSolicitacao(solicitacaoId: String): Flow<Solicitacao?>
}

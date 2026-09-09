package com.cjstudio.caronas

import kotlinx.coroutines.flow.Flow

interface IChatCaronaRepository {
    // Acha a conversa já existente pra essa solicitação ou cria uma nova
    // (só na hora que a primeira mensagem é enviada — ver ChatCaronaActivity).
    suspend fun buscarOuCriarConversa(solicitacao: Solicitacao): Result<ConversaCarona>

    // Mensagens da conversa em tempo real, mais antigas primeiro.
    fun escutarMensagens(conversaId: String): Flow<List<MensagemCarona>>

    suspend fun enviarMensagem(conversa: ConversaCarona, texto: String): Result<Unit>

    // Todas as conversas do usuário logado (como motorista ou passageiro)
    // em tempo real, mais recentes primeiro — usado pelo botão "Chat".
    fun escutarMinhasConversas(): Flow<List<ConversaCarona>>

    suspend fun marcarConversaComoLida(conversa: ConversaCarona): Result<Unit>

    // Recebe a mensagem inteira (não só o id) porque ela mora numa
    // subcoleção de conversas/{conversaId} — precisa do conversaId pra
    // montar o caminho do documento (ver ChatCaronaRepository).
    suspend fun apagarMensagemParaMim(mensagem: MensagemCarona): Result<Unit>

    suspend fun apagarMensagemParaTodos(mensagem: MensagemCarona): Result<Unit>
}

package com.cjstudio.caronas

import kotlinx.coroutines.flow.Flow

// Chat 1-a-1 entre administradores (separado do chat de carona entre
// motorista/passageiro, ver IChatCaronaRepository) — mesmas funções do chat
// entre admins do Match (lista de conversas, escolher outro admin, mandar/
// apagar mensagem, marcar como lida, apagar conversa inteira, badge de não
// lidas), só que seguindo o padrão Repository do Caronas em vez de tudo
// direto na Activity.
interface IChatAdminCaronaRepository {

    // Conversas onde o admin logado participa, mais recente primeiro —
    // alimenta ConversasAdminCaronasActivity.
    fun escutarMinhasConversas(): Flow<List<ConversaAdmin>>

    // Acha a conversa já existente com esse outro admin ou cria uma nova
    // (ver EscolherAdminChatCaronasActivity -> aqui).
    suspend fun buscarOuCriarConversa(outroAdminId: String, outroAdminNome: String?, outroAdminFoto: String?): Result<ConversaAdmin>

    // Mensagens de uma conversa, mais antiga primeiro — alimenta
    // ChatAdminCaronaActivity. Já devolve o texto decifrado (ver
    // ChatAdminCryptoUtil.descriptografar).
    fun escutarMensagens(conversaId: String): Flow<List<MensagemChatAdmin>>

    suspend fun enviarMensagem(conversa: ConversaAdmin, texto: String): Result<Unit>

    // Zera o contador de não lidas da conversa (pro admin logado) e marca
    // como lida cada mensagem recebida ainda não lida.
    suspend fun marcarConversaComoLida(conversa: ConversaAdmin): Result<Unit>

    // "Apagar só pra mim" — a mensagem continua existindo pro outro lado.
    suspend fun apagarMensagemParaMim(mensagem: MensagemChatAdmin): Result<Unit>

    // "Apagar pra todos" — só quem mandou pode; sobrescreve o conteúdo.
    suspend fun apagarMensagemParaTodos(mensagem: MensagemChatAdmin): Result<Unit>

    // Apaga a conversa inteira (todas as mensagens da subcoleção + o
    // documento da conversa) — ver ConversasAdminCaronasActivity, toque e
    // segure num item da lista.
    suspend fun apagarConversa(conversa: ConversaAdmin): Result<Unit>

    // Soma de não lidas em TODAS as conversas do admin logado — alimenta o
    // selo do botão "Mensagens Admins" no dashboard (ver
    // AdministracaoCaronasActivity.escutarBadgeMensagensAdmins).
    fun escutarTotalNaoLidas(): Flow<Int>
}

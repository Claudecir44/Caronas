package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Uma conversa entre o motorista e o passageiro de uma Solicitacao — só
// passa a existir (e a aparecer em "Chat") quando a primeira mensagem é
// enviada (ver ChatCaronaRepository.buscarOuCriarConversa). Guarda um
// resumo (última mensagem, não lidas por lado) pra montar a lista de
// conversas sem precisar ler todas as mensagens — mesmo espírito de
// Conversa.kt no Match. Serializable só pra passar o objeto inteiro numa
// Intent (ver ConversasCaronaActivity -> ChatCaronaActivity).
data class ConversaCarona(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var solicitacaoId: String? = null,
    var cidadeOrigem: String? = null,
    var cidadeDestino: String? = null,

    var motoristaId: String? = null,
    var motoristaNome: String? = null,
    var motoristaFotoUrl: String? = null,

    var passageiroId: String? = null,
    var passageiroNome: String? = null,
    var passageiroFotoUrl: String? = null,

    var ultimaMensagem: String? = null,
    @ServerTimestamp
    var ultimoTimestamp: Date? = null,
    var ultimoRemetenteId: String? = null,
    var naoLidasMotorista: Int = 0,
    var naoLidasPassageiro: Int = 0
) : java.io.Serializable {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 0)

    fun idOutroUsuario(meuId: String?) = if (motoristaId == meuId) passageiroId else motoristaId
    fun nomeOutroUsuario(meuId: String?) = if (motoristaId == meuId) passageiroNome else motoristaNome
    fun fotoOutroUsuario(meuId: String?) = if (motoristaId == meuId) passageiroFotoUrl else motoristaFotoUrl
    fun naoLidasParaMim(meuId: String?) = if (motoristaId == meuId) naoLidasMotorista else naoLidasPassageiro
}

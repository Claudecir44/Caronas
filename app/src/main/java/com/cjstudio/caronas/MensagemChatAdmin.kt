package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Mensagem do chat entre administradores (conversasAdmin/{id}/mensagens/{id}).
// "conteudo" fica salvo cifrado (ver ChatAdminCryptoUtil) — diferente de
// MensagemCarona, que é sempre texto puro. Duas formas de apagar (mesmo
// espírito do Match): "só pra mim" (deletadoParaRemetente/Destinatario,
// conforme quem apaga) e "pra todos" (deletadoParaTodos, só quem mandou),
// que sobrescreve o conteúdo por um texto fixo de "mensagem apagada".
data class MensagemChatAdmin(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var conversaId: String? = null,
    var remetenteId: String? = null,
    var remetenteNome: String? = null,
    var destinatarioId: String? = null,
    var destinatarioNome: String? = null,
    var conteudo: String? = null,
    @ServerTimestamp
    var timestamp: Date? = null,
    var lida: Boolean = false,
    var tipo: String = "texto",
    var deletadoParaTodos: Boolean = false,
    var deletadoParaRemetente: Boolean = false,
    var deletadoParaDestinatario: Boolean = false
) {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, null, null, false, "texto", false, false, false)

    fun deletadaParaMim(meuId: String?): Boolean =
        deletadoParaTodos || (remetenteId == meuId && deletadoParaRemetente) || (destinatarioId == meuId && deletadoParaDestinatario)
}

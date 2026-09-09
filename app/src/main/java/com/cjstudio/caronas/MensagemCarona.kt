package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MensagemCarona(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var conversaId: String? = null,
    var remetenteId: String? = null,
    var destinatarioId: String? = null,
    var conteudo: String? = null,
    @ServerTimestamp
    var timestamp: Date? = null,
    var lida: Boolean = false,
    var deletadaParaTodos: Boolean = false,
    var deletadaParaRemetente: Boolean = false,
    var deletadaParaDestinatario: Boolean = false
) {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, false, false, false, false)
}

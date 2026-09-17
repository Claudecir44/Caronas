package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Avaliação de uma viagem, na direção passageiro->motorista ou
// motorista->passageiro (mesma coleção pros dois sentidos, diferenciados só
// pelos ids). Documento com ID determinístico "{solicitacaoId}_{avaliadorId}"
// (mesmo truque de usuariosBloqueados no Match) — impede a mesma pessoa
// avaliar a mesma viagem duas vezes sem precisar de transação: uma segunda
// tentativa de gravar cai como "update" (documento já existe), não "create",
// e a regra do Firestore bloqueia update (ver firestore.rules).
data class Avaliacao(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var solicitacaoId: String? = null,
    var avaliadorId: String? = null,
    var avaliadoId: String? = null,
    var nota: Int = 0,
    var comentario: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    // Construtor vazio necessário para o Firestore (mesmo padrão do projeto).
    constructor() : this(null, null, null, null, 0, null, null)
}

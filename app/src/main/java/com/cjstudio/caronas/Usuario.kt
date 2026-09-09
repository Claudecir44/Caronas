package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Mesmo usuário serve pra motorista e passageiro — "motorista" é só um
// papel marcado no cadastro, não um tipo de conta separado (ver
// CadastroCaronasActivity). "id" nunca é lido do Firestore (é o próprio
// nome do documento, usuarios/{uid}) — sempre setado manualmente depois
// da leitura, igual ao padrão do Usuario.kt do Match.
data class Usuario(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var nomeCompleto: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var fotoUrl: String? = null,
    var motorista: Boolean = false,
    var veiculo: Veiculo? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    // Construtor vazio necessário para o Firestore.
    constructor() : this(null, null, null, null, null, false, null, null)
}

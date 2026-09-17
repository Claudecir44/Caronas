package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Identidade de um administrador — separada de Usuario (um admin não
// precisa ter uma conta comum de passageiro/motorista). Criado só pela
// Cloud Function "cadastrarAdmin" (ver CadastroAdminCaronasActivity), nunca
// por um "create" direto do cliente (ver firestore.rules).
data class Admin(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var nome: String? = null,
    var sobrenome: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var cpf: String? = null,
    var fotoUrl: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null)

    val nomeCompleto: String
        get() = listOfNotNull(nome, sobrenome).joinToString(" ").trim()
}

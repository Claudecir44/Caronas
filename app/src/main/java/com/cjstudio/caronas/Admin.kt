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

    // "admin" | "colaborador" — só rótulo de exibição (badge "(colaborador)"
    // embaixo do nome na tela do admin logado, ver AdministracaoCaronasActivity)
    // e valor default dos checkboxes de permissão no cadastro; não é
    // consultado por temPermissao (quem manda é o mapa permissoes mesmo).
    var role: String? = "admin",

    // Ausente/null = admin "legado" (criado antes deste sistema existir) =
    // acesso total, mesma regra do servidor (ver temPermissao em
    // functions/index.js) — sem isso, todo admin já cadastrado perderia
    // acesso ao publicar esta mudança.
    var permissoes: Map<String, Boolean>? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    constructor() : this(null, null, null, null, null, null, "admin", null, null)

    val nomeCompleto: String
        get() = listOfNotNull(nome, sobrenome).joinToString(" ").trim()

    val ehColaborador: Boolean
        get() = role == "colaborador"

    // Admin master de verdade, identificado por CPF fixo (mesmo CPF já
    // usado como master no Match) — tem permissão total e irrestrita
    // sempre, independente do mapa permissoes (ver temPermissao abaixo e
    // ehMaster/CPF_ADMIN_MASTER em functions/index.js).
    val ehMaster: Boolean
        get() = cpf == CPF_ADMIN_MASTER

    fun temPermissao(chave: String): Boolean {
        if (ehMaster) return true
        val mapa = permissoes ?: return true
        return mapa[chave] == true
    }

    companion object {
        const val CPF_ADMIN_MASTER = "56413025034"

        // Mesma ordem/rótulos usados na grade de checkboxes de
        // CadastroAdminCaronasActivity — precisam bater com CHAVES_PERMISSOES
        // em functions/index.js.
        val CHAVES_PERMISSOES = listOf(
            "usuarios" to "Motoristas e Passageiros",
            "viagens" to "Viagens",
            "financeiro" to "Financeiro",
            "mensagens" to "Mensagens",
            "manifestacoes" to "Reclamações, Sugestões e Denúncias",
            "relatorios" to "Relatórios",
            "administradores" to "Administradores",
            "chatAdmin" to "Chat entre Administradores"
        )
    }
}

package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Uma entrada do registro de ações administrativas sensíveis (criar/editar/
// excluir admin, editar/excluir usuário) — só a Cloud Function grava (ver
// registrarLogAdministracao em functions/index.js); usada pela seção
// "Administração" de RelatoriosCaronasActivity.
data class LogAdministracao(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    // "admin_criado" | "admin_editado" | "admin_excluido" |
    // "usuario_editado" | "usuario_excluido"
    var tipo: String? = null,
    var alvoNome: String? = null,
    var alvoId: String? = null,
    var executadoPorAdminId: String? = null,
    var executadoPorNome: String? = null,
    var executadoPorCpf: String? = null,

    // "senhaMaster" | "senhaPropria" — qual trava autorizou a ação.
    var autorizadoPor: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null, null)
}

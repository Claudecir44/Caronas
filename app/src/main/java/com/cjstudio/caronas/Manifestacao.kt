package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Reclamação, sugestão ou denúncia enviada pelo usuário (ver
// ConfiguracoesCaronasActivity -> EnviarManifestacaoActivity) e tratada pelo
// painel admin (nativo + web, ver AdministracaoCaronasActivity/
// public/index.html). "tipo" separa as três naturezas na mesma coleção —
// mesmo modelo já usado pelo Match ("sugestoes"), só que aqui sem a
// distinção denunciante/denunciado: é sempre uma mensagem de UM autor pros
// admins, nunca uma denúncia formal contra outro usuário específico.
data class Manifestacao(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var tipo: String? = null, // "reclamacao" | "sugestao" | "denuncia"
    var nomeCompleto: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var mensagem: String? = null,
    var usuarioId: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null,

    var status: String = STATUS_PENDENTE,
    var resposta: String? = null,
    var respondidoEm: Date? = null,
    var respondidoPorCpf: String? = null,

    var arquivado: Boolean = false,
    var arquivadoEm: Date? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null, STATUS_PENDENTE, null, null, null, false, null)

    companion object {
        const val TIPO_RECLAMACAO = "reclamacao"
        const val TIPO_SUGESTAO = "sugestao"
        const val TIPO_DENUNCIA = "denuncia"

        const val STATUS_PENDENTE = "pendente"
        const val STATUS_RESPONDIDO = "respondido"
    }
}

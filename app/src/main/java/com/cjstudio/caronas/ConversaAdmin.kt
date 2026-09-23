package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Uma conversa 1-a-1 entre dois administradores (chat interno, separado do
// chat de carona entre motorista/passageiro) — mesmo espírito de
// ConversaCarona, mas os dois lados são genéricos ("admin1"/"admin2") em vez
// de papéis fixos (motorista/passageiro). Nasce ao escolher outro admin em
// EscolherAdminChatCaronasActivity (ver ChatAdminCaronaRepository
// .buscarOuCriarConversa). Serializable só pra passar o objeto inteiro numa
// Intent (ver ConversasAdminCaronasActivity -> ChatAdminCaronaActivity).
data class ConversaAdmin(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var participantes: List<String>? = null,

    var admin1Id: String? = null,
    var admin1Nome: String? = null,
    var admin1Foto: String? = null,

    var admin2Id: String? = null,
    var admin2Nome: String? = null,
    var admin2Foto: String? = null,

    var ultimaMensagem: String? = null,
    @ServerTimestamp
    var ultimoTimestamp: Date? = null,
    var ultimoRemetenteId: String? = null,
    var naoLidas1: Int = 0,
    var naoLidas2: Int = 0
) : java.io.Serializable {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, 0, 0)

    fun idOutroAdmin(meuId: String?) = if (admin1Id == meuId) admin2Id else admin1Id
    fun nomeOutroAdmin(meuId: String?) = if (admin1Id == meuId) admin2Nome else admin1Nome
    fun fotoOutroAdmin(meuId: String?) = if (admin1Id == meuId) admin2Foto else admin1Foto
    fun naoLidasParaMim(meuId: String?) = if (admin1Id == meuId) naoLidas1 else naoLidas2
}

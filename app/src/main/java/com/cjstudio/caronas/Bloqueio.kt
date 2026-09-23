package com.cjstudio.caronas

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Um usuário bloqueou outro (ver IBloqueioRepository, SegurancaUsuarioDialogUtil).
// ID do documento é sempre "{bloqueadorId}_{bloqueadoId}" — é o que deixa o
// firestore.rules checar o bloqueio com um exists() direto, sem consulta, ao
// criar solicitação/conversa/mensagem. Vale nos dois sentidos: quem bloqueou
// e quem foi bloqueado deixam de se ver na busca e não trocam mais mensagens.
data class Bloqueio(
    var bloqueadorId: String? = null,
    var bloqueadoId: String? = null,
    // Só pra listar "Usuários bloqueados" em Configurações sem buscar o
    // cadastro de cada um.
    var bloqueadoNome: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    companion object {
        fun idDocumento(bloqueadorId: String, bloqueadoId: String) = "${bloqueadorId}_$bloqueadoId"
    }
}

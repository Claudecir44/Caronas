package com.cjstudio.caronas

// Registro do token FCM do aparelho, usado pelas Cloud Functions pra saber pra
// qual aparelho mandar cada notificação. Fire-and-forget de propósito: nunca
// deve travar login/abertura do app por causa de push.
interface INotificacaoRepository {
    // Grava em usuarios/{uid}.fcmToken (flavor usuario).
    fun atualizarTokenUsuario(uid: String?)

    // Grava em admins/{uid}.fcmToken (flavor admin) — recebe o push de
    // notificarNovaManifestacao (functions/index.js).
    fun atualizarTokenAdmin(uid: String?)
}

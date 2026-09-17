package com.cjstudio.caronas

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

// Salva o token FCM do aparelho no próprio documento do usuário
// (usuarios/{uid}.fcmToken) — usado pelas Cloud Functions
// notificarSolicitacaoViagem/notificarViagemAceita/notificarMensagemCaronas
// pra saber pra qual aparelho mandar a notificação. Mesmo padrão do Match
// (FcmTokenUtil.atualizarTokenFcm): um campo só, sobrescrito a cada
// chamada — modelo de um aparelho por conta, não uma lista. Falha
// silenciosa de propósito (só Log.e) — nunca deve travar login/abertura
// do app por causa de push notification.
object FcmTokenUtil {
    private const val TAG = "FcmTokenUtil"

    fun atualizarToken(uid: String?) {
        if (uid.isNullOrBlank()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener { e -> Log.e(TAG, "Erro ao salvar fcmToken: ${e.message}") }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Erro ao obter fcmToken: ${e.message}") }
    }
}

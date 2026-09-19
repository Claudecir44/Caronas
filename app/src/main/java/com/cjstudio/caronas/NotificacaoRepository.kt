package com.cjstudio.caronas

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton

// Um campo só (fcmToken), sobrescrito a cada chamada — modelo de um aparelho
// por conta, não uma lista. Falha silenciosa (só Log.e). Substitui o antigo
// FcmTokenUtil, que falava com Firestore/FirebaseMessaging direto.
@Singleton
class NotificacaoRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : INotificacaoRepository {

    override fun atualizarTokenUsuario(uid: String?) = salvarToken("usuarios", uid)

    override fun atualizarTokenAdmin(uid: String?) = salvarToken("admins", uid)

    private fun salvarToken(colecao: String, uid: String?) {
        if (uid.isNullOrBlank()) return
        messaging.token
            .addOnSuccessListener { token ->
                db.collection(colecao).document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener { e -> Log.e(TAG, "Erro ao salvar fcmToken ($colecao): ${e.message}") }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Erro ao obter fcmToken: ${e.message}") }
    }

    private companion object {
        const val TAG = "NotificacaoRepository"
    }
}

package com.cjstudio.caronas

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FalhasRepository @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) : IFalhasRepository {

    override fun definirUsuario(uid: String?) {
        // Crashlytics usa string vazia pra limpar o identificador.
        runCatching { crashlytics.setUserId(uid.orEmpty()) }
    }

    override fun definirChave(chave: String, valor: String) {
        runCatching { crashlytics.setCustomKey(chave, valor) }
    }

    override fun registrar(erro: Throwable) {
        runCatching { crashlytics.recordException(erro) }
    }
}

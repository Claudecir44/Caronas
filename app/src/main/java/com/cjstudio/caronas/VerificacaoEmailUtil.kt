package com.cjstudio.caronas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

// Reenvia o e-mail de verificação só se já não tiver mandado um há menos
// de 1 minuto (ver KEY_ULTIMO_REENVIO_VERIFICACAO) — usado tanto no login
// comum (UsuarioRepository.login) quanto no login admin
// (LoginAdminCaronasActivity), os dois pontos onde uma conta não
// verificada tenta entrar.
//
// Antes disso, TODA tentativa de login com conta não verificada disparava
// um reenvio sem nenhum limite — login errado/repetido em sequência rápida
// estourava o próprio limite de envio do Firebase (erro real visto em
// produção: "TOO_MANY_ATTEMPTS_TRY_LATER"), e a partir daí os reenvios
// seguintes falhavam calados (capturados por um catch genérico) enquanto a
// mensagem pro usuário continuava dizendo "reenviamos o e-mail" — nenhum
// e-mail novo estava saindo, mas o app garantia que sim. Essa função
// devolve a mensagem certa pra cada caso (reenviou agora / já tinha
// mandado há pouco / o próprio Firebase recusou por excesso de pedidos),
// pra nunca mais prometer um reenvio que não aconteceu.
private const val COOLDOWN_REENVIO_VERIFICACAO_MS = 60_000L

suspend fun reenviarVerificacaoComCooldown(firebaseUser: FirebaseUser, prefs: DataStore<Preferences>): String {
    val agora = System.currentTimeMillis()
    val ultimoReenvio = prefs.data.first()[KEY_ULTIMO_REENVIO_VERIFICACAO] ?: 0L

    if (agora - ultimoReenvio < COOLDOWN_REENVIO_VERIFICACAO_MS) {
        return "Valide seu cadastro pelo e-mail para poder entrar. Você já tem um e-mail de verificação enviado há pouco — confira sua caixa de entrada e o spam antes de pedir outro."
    }

    return try {
        firebaseUser.sendEmailVerification().await()
        prefs.edit { it[KEY_ULTIMO_REENVIO_VERIFICACAO] = agora }
        "Valide seu cadastro pelo e-mail para poder entrar. Reenviamos o e-mail de verificação — confira também a caixa de spam."
    } catch (e: Exception) {
        val mensagemErro = e.message.orEmpty()
        if (mensagemErro.contains("TOO_MANY", ignoreCase = true) || mensagemErro.contains("too-many-requests", ignoreCase = true)) {
            "Você pediu e-mails de verificação demais recentemente. Aguarde alguns minutos e confira o spam do e-mail já enviado antes de tentar de novo."
        } else {
            // Outra falha (sem rede, etc.) — não dá pra saber se o e-mail
            // saiu ou não, então não afirma "reenviamos".
            "Valide seu cadastro pelo e-mail para poder entrar. Não conseguimos confirmar o reenvio agora — confira a caixa de entrada e o spam do e-mail já enviado antes."
        }
    }
}

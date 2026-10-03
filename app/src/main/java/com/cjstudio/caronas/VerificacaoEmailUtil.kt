package com.cjstudio.caronas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

// Todo envio de e-mail de verificação do app passa por aqui e grava a hora
// em KEY_ULTIMO_REENVIO_VERIFICACAO — cadastro (enviarVerificacaoInicial),
// reenvio pela caixa Suporte (registrarEnvioVerificacao) e o reenvio
// automático do login comum/admin (reenviarVerificacaoComCooldown).
//
// Por que importa: cada e-mail novo gera um link novo e o Firebase invalida
// o link anterior do mesmo tipo. Antes, o cadastro mandava o e-mail mas NÃO
// gravava a hora — a primeira tentativa de login (antes de abrir o e-mail)
// mandava outro na hora, e o usuário abria o primeiro (o que chegou antes)
// e via "link expirado". Erro real relatado por usuários em produção.
//
// O cooldown de 1 hora garante que o link já enviado continue valendo pelo
// menos esse tempo — o login não troca o link enquanto o usuário ainda está
// indo abrir o e-mail.
//
// Histórico: o cooldown existe desde que login errado/repetido em
// sequência estourava o limite de envio do Firebase ("TOO_MANY_ATTEMPTS_TRY_LATER")
// e o app seguia dizendo "reenviamos" sem nenhum e-mail sair — a mensagem
// devolvida aqui sempre diz o que de fato aconteceu.
private const val COOLDOWN_REENVIO_VERIFICACAO_MS = 60 * 60_000L

suspend fun registrarEnvioVerificacao(prefs: DataStore<Preferences>) {
    prefs.edit { it[KEY_ULTIMO_REENVIO_VERIFICACAO] = System.currentTimeMillis() }
}

// Primeiro e-mail (cadastro de usuário ou de admin). Grava a hora pra o
// login logo em seguida não mandar outro e invalidar este.
suspend fun enviarVerificacaoInicial(firebaseUser: FirebaseUser, prefs: DataStore<Preferences>) {
    firebaseUser.sendEmailVerification().await()
    registrarEnvioVerificacao(prefs)
}

suspend fun reenviarVerificacaoComCooldown(firebaseUser: FirebaseUser, prefs: DataStore<Preferences>): String {
    val agora = System.currentTimeMillis()
    val ultimoReenvio = prefs.data.first()[KEY_ULTIMO_REENVIO_VERIFICACAO] ?: 0L

    if (agora - ultimoReenvio < COOLDOWN_REENVIO_VERIFICACAO_MS) {
        return "Valide seu cadastro pelo e-mail para poder entrar. Já enviamos um e-mail de verificação há menos de 1 hora — abra o link dele (confira também o spam) e depois volte para entrar."
    }

    return try {
        firebaseUser.sendEmailVerification().await()
        registrarEnvioVerificacao(prefs)
        "Valide seu cadastro pelo e-mail para poder entrar. Enviamos um novo e-mail de verificação — use o link do e-mail MAIS RECENTE (os anteriores deixam de valer) e confira também o spam."
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

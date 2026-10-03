package com.cjstudio.caronas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
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
// indo abrir o e-mail. O servidor repete a mesma trava
// (enviarVerificacaoEmailPropria em functions/index.js), que vale mesmo se
// o app for reinstalado e perder a hora salva aqui.
//
// O e-mail em si sai pelo servidor (texto próprio em português, passo a
// passo). Se a function falhar, cai no envio padrão do Firebase Auth pra
// a pessoa nunca ficar sem e-mail nenhum.
//
// Histórico: o cooldown existe desde que login errado/repetido em
// sequência estourava o limite de envio do Firebase ("TOO_MANY_ATTEMPTS_TRY_LATER")
// e o app seguia dizendo "reenviamos" sem nenhum e-mail sair — a mensagem
// devolvida aqui sempre diz o que de fato aconteceu.
private const val COOLDOWN_REENVIO_VERIFICACAO_MS = 60 * 60_000L

suspend fun registrarEnvioVerificacao(prefs: DataStore<Preferences>) {
    prefs.edit { it[KEY_ULTIMO_REENVIO_VERIFICACAO] = System.currentTimeMillis() }
}

private enum class ResultadoEnvioVerificacao { ENVIADO, RECENTE }

private suspend fun enviarVerificacao(
    firebaseUser: FirebaseUser,
    functions: FirebaseFunctions,
    origem: String,
    nome: String?
): ResultadoEnvioVerificacao {
    val resposta = try {
        functions.getHttpsCallable("enviarVerificacaoEmailPropria")
            .call(mapOf("origem" to origem, "nome" to nome.orEmpty()))
            .await()
            .getData() as? Map<*, *>
    } catch (_: Exception) {
        // Reserva: e-mail padrão do Firebase. Se este também falhar, a
        // exceção sobe pra quem chamou.
        firebaseUser.sendEmailVerification().await()
        return ResultadoEnvioVerificacao.ENVIADO
    }
    return if (resposta?.get("recente") == true) ResultadoEnvioVerificacao.RECENTE else ResultadoEnvioVerificacao.ENVIADO
}

// Primeiro e-mail (cadastro de usuário ou de admin). Grava a hora pra o
// login logo em seguida não mandar outro e invalidar este.
suspend fun enviarVerificacaoInicial(
    firebaseUser: FirebaseUser,
    prefs: DataStore<Preferences>,
    functions: FirebaseFunctions,
    nome: String?
) {
    enviarVerificacao(firebaseUser, functions, "cadastro", nome)
    registrarEnvioVerificacao(prefs)
}

// Devolve só a situação do e-mail (sem "valide seu cadastro…" — isso o
// aviso VerificarEmailDialogUtil já diz no título).
suspend fun reenviarVerificacaoComCooldown(
    firebaseUser: FirebaseUser,
    prefs: DataStore<Preferences>,
    functions: FirebaseFunctions
): String {
    val agora = System.currentTimeMillis()
    val ultimoReenvio = prefs.data.first()[KEY_ULTIMO_REENVIO_VERIFICACAO] ?: 0L
    val msgRecente = "Já enviamos o e-mail de confirmação há menos de 1 hora. Use o link desse e-mail — ele continua valendo."

    if (agora - ultimoReenvio < COOLDOWN_REENVIO_VERIFICACAO_MS) {
        return msgRecente
    }

    return try {
        when (enviarVerificacao(firebaseUser, functions, "login", null)) {
            ResultadoEnvioVerificacao.RECENTE -> msgRecente
            ResultadoEnvioVerificacao.ENVIADO -> {
                registrarEnvioVerificacao(prefs)
                "Enviamos agora um NOVO e-mail de confirmação. Use o link desse e-mail mais recente — os anteriores deixam de valer."
            }
        }
    } catch (e: Exception) {
        val mensagemErro = e.message.orEmpty()
        if (mensagemErro.contains("TOO_MANY", ignoreCase = true) || mensagemErro.contains("too-many-requests", ignoreCase = true)) {
            "Você pediu e-mails de confirmação demais em pouco tempo. Aguarde alguns minutos e procure o e-mail que já enviamos."
        } else {
            // Outra falha (sem rede, etc.) — não dá pra saber se o e-mail
            // saiu ou não, então não afirma "reenviamos".
            "Não conseguimos confirmar o reenvio agora. Procure o e-mail de confirmação que já enviamos."
        }
    }
}

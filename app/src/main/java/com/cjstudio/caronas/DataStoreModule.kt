package com.cjstudio.caronas

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Sessão do app inteira num único DataStore (fase 1 não tem idioma/
// consentimento ainda — ver DataStoreModule.kt do Match pra esse padrão
// mais elaborado, caso vire necessário depois).
val KEY_USUARIO_ID = stringPreferencesKey("usuarioId")

// Hora (millis) do último reenvio automático de e-mail de verificação —
// ver UsuarioRepository.login/LoginAdminCaronasActivity. Antes, TODA
// tentativa de login com conta não verificada disparava um reenvio, e
// login errado/repetido em sequência rápida estourava o limite de envio do
// Firebase (erro real visto: "TOO_MANY_ATTEMPTS_TRY_LATER") — a partir daí
// os reenvios seguintes falhavam calados, mas a mensagem continuava
// dizendo "reenviamos o e-mail", enganando o usuário. Um único valor
// global (não por e-mail) é suficiente — o cenário raro de duas contas
// diferentes tentando logar no mesmo aparelho no mesmo minuto só atrasa um
// reenvio por bem pouco tempo, não é motivo pra complicar a chave.
val KEY_ULTIMO_REENVIO_VERIFICACAO = longPreferencesKey("ultimoReenvioVerificacao")

val Context.caronasPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "CaronasPrefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideCaronasPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.caronasPrefsDataStore
}

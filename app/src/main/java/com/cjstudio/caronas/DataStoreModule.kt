package com.cjstudio.caronas

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

val Context.caronasPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "CaronasPrefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideCaronasPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.caronasPrefsDataStore
}

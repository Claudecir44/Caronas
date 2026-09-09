package com.cjstudio.caronas

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUsuarioRepository(impl: UsuarioRepository): IUsuarioRepository

    @Binds
    @Singleton
    abstract fun bindCaronaRepository(impl: CaronaRepository): ICaronaRepository

    @Binds
    @Singleton
    abstract fun bindSolicitacaoRepository(impl: SolicitacaoRepository): ISolicitacaoRepository

    @Binds
    @Singleton
    abstract fun bindChatCaronaRepository(impl: ChatCaronaRepository): IChatCaronaRepository
}

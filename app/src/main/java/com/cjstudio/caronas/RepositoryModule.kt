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

    @Binds
    @Singleton
    abstract fun bindAvaliacaoRepository(impl: AvaliacaoRepository): IAvaliacaoRepository

    @Binds
    @Singleton
    abstract fun bindAutocompleteRepository(impl: AutocompleteRepository): IAutocompleteRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepository): IAdminRepository

    @Binds
    @Singleton
    abstract fun bindNotificacaoRepository(impl: NotificacaoRepository): INotificacaoRepository

    @Binds
    @Singleton
    abstract fun bindFalhasRepository(impl: FalhasRepository): IFalhasRepository

    @Binds
    @Singleton
    abstract fun bindChatAdminCaronaRepository(impl: ChatAdminCaronaRepository): IChatAdminCaronaRepository

    @Binds
    @Singleton
    abstract fun bindBloqueioRepository(impl: BloqueioRepository): IBloqueioRepository
}

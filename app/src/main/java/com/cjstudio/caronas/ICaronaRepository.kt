package com.cjstudio.caronas

interface ICaronaRepository {
    // Preenche motoristaId com o usuário logado antes de gravar.
    suspend fun publicarCarona(carona: Carona): Result<String>
}

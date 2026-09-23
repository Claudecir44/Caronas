package com.cjstudio.caronas

interface IBloqueioRepository {
    suspend fun bloquear(outroId: String, outroNome: String?): Result<Unit>

    suspend fun desbloquear(outroId: String): Result<Unit>

    // Se o usuário logado bloqueou essa pessoa (decide o texto
    // "Bloquear"/"Desbloquear" no menu de segurança).
    suspend fun bloqueei(outroId: String): Result<Boolean>

    // Quem o usuário logado bloqueou — lista "Usuários bloqueados" em
    // Configurações.
    suspend fun listarMeusBloqueios(): Result<List<Bloqueio>>

    // Ids de todo mundo com bloqueio em QUALQUER sentido com o usuário
    // logado (eu bloqueei ou fui bloqueado) — usada pra esconder caronas
    // dessas pessoas na busca.
    suspend fun idsComBloqueio(): Result<Set<String>>
}

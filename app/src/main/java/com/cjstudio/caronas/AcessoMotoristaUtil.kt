package com.cjstudio.caronas

// Mesma regra de firestore.rules:permiteOferecerCarona, calculada aqui só
// pra dar um aviso amigável ANTES de tentar publicar (ver
// OferecerCaronaActivity) — quem realmente garante que não dá pra burlar
// são as regras do servidor; isso aqui é só UX, nunca a trava de verdade.
object AcessoMotoristaUtil {
    const val CARONAS_GRATUITAS = 10

    fun permiteOferecerCarona(usuario: Usuario): Boolean {
        if (usuario.caronasOferecidas < CARONAS_GRATUITAS) return true
        val expiraEm = usuario.acessoMotoristaExpiraEm ?: return false
        return expiraEm.time > System.currentTimeMillis()
    }
}

package com.cjstudio.caronas

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

// Mesma regra de firestore.rules:permiteOferecerCarona, calculada aqui só
// pra dar um aviso amigável ANTES de tentar publicar (ver
// OferecerCaronaActivity) — quem realmente garante que não dá pra burlar
// são as regras do servidor; isso aqui é só UX, nunca a trava de verdade.
object AcessoMotoristaUtil {
    const val CARONAS_GRATUITAS = 10

    // Só dá pra pagar de novo faltando no máximo isso pro acesso atual vencer
    // (mesma regra de createPaymentPreferenceMotorista, que é a trava real).
    const val JANELA_RENOVACAO_DIAS = 2
    private const val DIA_MS = 24L * 60 * 60 * 1000

    fun permiteOferecerCarona(usuario: Usuario): Boolean {
        if (usuario.caronasOferecidas < CARONAS_GRATUITAS) return true
        return temAcessoPagoValido(usuario)
    }

    fun temAcessoPagoValido(usuario: Usuario): Boolean =
        usuario.acessoMotoristaExpiraEm?.time?.let { it > System.currentTimeMillis() } == true

    // Momento a partir do qual o motorista pode pagar de novo: 2 dias antes do
    // acesso atual vencer. null = sem acesso pago válido, pode pagar já.
    fun liberaRenovacaoEm(usuario: Usuario): Long? {
        val expiraEm = usuario.acessoMotoristaExpiraEm?.time ?: return null
        if (expiraEm <= System.currentTimeMillis()) return null
        return expiraEm - JANELA_RENOVACAO_DIAS * DIA_MS
    }

    fun podePagarNovamente(usuario: Usuario): Boolean {
        val libera = liberaRenovacaoEm(usuario) ?: return true
        return System.currentTimeMillis() >= libera
    }

    // Texto de status do motorista, compartilhado pela tela principal (acima
    // do "Sair") e pelo card "Pagamentos" das Configurações — "Acesso pago
    // até DD/MM/AAAA" ou quantas caronas grátis AINDA RESTAM (não quantas já
    // usou) — pedido explícito do usuário: "1/10" (já usou 1) confundia,
    // "09/10" (restam 9) deixa claro quanto ainda dá pra oferecer grátis.
    fun textoStatus(context: Context, usuario: Usuario): String {
        val expiraEm = usuario.acessoMotoristaExpiraEm
        if (expiraEm != null && temAcessoPagoValido(usuario)) {
            val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            return context.getString(R.string.tela_contador_viagens_gratis_pago_formato, formato.format(expiraEm))
        }
        val usadas = minOf(usuario.caronasOferecidas, CARONAS_GRATUITAS)
        return context.getString(
            R.string.tela_contador_viagens_gratis_formato,
            CARONAS_GRATUITAS - usadas,
            CARONAS_GRATUITAS
        )
    }
}

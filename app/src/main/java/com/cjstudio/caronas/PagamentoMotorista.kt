package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName

// Um documento por pagamento aprovado do acesso pago do motorista (ver
// concederAcessoMotorista em functions/index.js) — histórico completo de
// cobranças, ao contrário de usuarios.acessoMotoristaExpiraEm, que só guarda
// a validade da ÚLTIMA compra. Mesmo papel da coleção "pagamentos" do Match
// (ver RelatoriosFinanceirosActivity de lá), adaptado: aqui só existe um
// valor/plano fixo (sem "Mensal"/"Trimestral"), então não guarda "plano".
// dataCompra/expiraEm em millis (Long), não Timestamp — mesmo formato que o
// Match usa nesse documento, grava direto de Date.now()/Date.getTime() na
// Cloud Function.
data class PagamentoMotorista(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var usuarioId: String? = null,
    var usuarioNome: String? = null,
    var usuarioEmail: String? = null,
    var valor: Double = 0.0,
    var dataCompra: Long? = null,
    var expiraEm: Long? = null,
    var mercadoPagoPaymentId: String? = null,
    // Preenchidos quando o motorista pagou pelo Google Play (ver
    // confirmarCompraGooglePlayMotorista) em vez do Mercado Pago.
    var googlePlayPurchaseToken: String? = null,
    var googlePlayOrderId: String? = null,

    // Marcado pela Cloud Function quando o Mercado Pago avisa estorno/chargeback
    // (ver revogarAcessoMotoristaPorEstorno) — o acesso desse pagamento já foi
    // retirado; o painel financeiro ignora esses documentos.
    var estornado: Boolean = false,

    // Plano comprado (Mensal/Trimestral) e quantos dias deu — ausentes nos
    // pagamentos antigos, de antes dos planos (todos de 30 dias).
    var plano: String? = null,
    var dias: Int? = null
) {
    constructor() : this(null, null, null, null, 0.0, null, null, null, null, null, false, null, null)
}

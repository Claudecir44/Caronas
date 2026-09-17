package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName

// Um documento por pagamento aprovado dos R$15,99/30 dias do motorista (ver
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
    var mercadoPagoPaymentId: String? = null
) {
    constructor() : this(null, null, null, null, 0.0, null, null, null)
}

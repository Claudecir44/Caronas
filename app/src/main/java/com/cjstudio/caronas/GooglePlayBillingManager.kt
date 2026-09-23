package com.cjstudio.caronas

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UserChoiceBillingListener

interface GooglePlayBillingCallback {
    // Motorista escolheu pagar pelo Google Play na tela de escolha do
    // Google — a compra ainda precisa ser confirmada no servidor
    // (confirmarCompraGooglePlayMotorista) antes de liberar o acesso.
    fun onGooglePlayPurchaseCompleted(purchase: Purchase)

    // Motorista escolheu a opção alternativa (Mercado Pago) — segue o fluxo
    // de sempre (createPaymentPreferenceMotorista), levando esse token pro
    // servidor reportar a transação ao Google depois.
    fun onUserChoseAlternativeBilling(externalTransactionToken: String)

    fun onBillingError(mensagem: String)
}

// Play Billing Library só no que o Caronas precisa — mesmo desenho do
// GooglePlayBillingManager do Match. No Brasil o Google exige "User Choice
// Billing" pra manter o Mercado Pago: o próprio Google mostra a escolha
// Google Play x Mercado Pago antes de qualquer pagamento, não dá pra ir
// direto pro Mercado Pago. Ver AssinaturaMotoristaActivity.iniciarPagamento().
class GooglePlayBillingManager(
    context: Context,
    private val callback: GooglePlayBillingCallback
) {
    private var estaConectado = false
    private val produtosDisponiveis = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { callback.onGooglePlayPurchaseCompleted(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit // fechou a tela do Google
            else -> {
                Log.e(TAG, "Erro na compra via Google Play: ${billingResult.debugMessage}")
                callback.onBillingError(billingResult.debugMessage)
            }
        }
    }

    private val userChoiceBillingListener = UserChoiceBillingListener { detalhes ->
        callback.onUserChoseAlternativeBilling(detalhes.externalTransactionToken)
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableUserChoiceBilling(userChoiceBillingListener)
        .build()

    // Idempotente: já conectado (e produtos carregados) chama aoConectar() na hora.
    fun conectar(aoConectar: () -> Unit) {
        if (estaConectado) {
            aoConectar()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    carregarProdutos { estaConectado = true; aoConectar() }
                } else {
                    Log.e(TAG, "Falha ao conectar à Play Billing: ${billingResult.debugMessage}")
                    callback.onBillingError(billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                estaConectado = false
            }
        })
    }

    private fun carregarProdutos(aoTerminar: () -> Unit) {
        val produtos = PlanoMotorista.entries.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.produtoGooglePlay)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(produtos).build()

        billingClient.queryProductDetailsAsync(params) { billingResult, resultado ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                produtosDisponiveis.clear()
                resultado.productDetailsList.forEach { produtosDisponiveis[it.productId] = it }
                if (resultado.productDetailsList.isEmpty()) {
                    // A consulta funcionou, só não achou produto: o app ainda
                    // não está no Play Console ou os produtos não foram criados
                    // lá com esses IDs.
                    Log.w(TAG, "Nenhum produto encontrado no Play Console (acesso_motorista_mensal/trimestral).")
                }
            } else {
                Log.e(TAG, "Erro ao consultar produtos no Google Play: ${billingResult.debugMessage}")
            }
            aoTerminar()
        }
    }

    // Abre a tela do Google (no Brasil, com a escolha Google Play x Mercado
    // Pago). O resultado chega depois pelos listeners, nunca por retorno.
    // usuarioId vai como obfuscatedAccountId: o servidor confere que a
    // compra é da mesma conta que pede a confirmação.
    fun iniciarCompra(activity: Activity, plano: PlanoMotorista, usuarioId: String) {
        val productDetails = produtosDisponiveis[plano.produtoGooglePlay]
        if (productDetails == null) {
            callback.onBillingError(activity.getString(R.string.assinatura_motorista_produto_indisponivel))
            return
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .setObfuscatedAccountId(usuarioId)
            .build()

        val resultado = billingClient.launchBillingFlow(activity, params)
        if (resultado.responseCode != BillingClient.BillingResponseCode.OK) {
            callback.onBillingError(resultado.debugMessage)
        }
    }

    // Reconsulta compras já pagas — chamado no onResume da tela. Cobre Pix
    // e outros meios "atrasados" que estavam pendentes na primeira
    // confirmação. Reprocessar não tem problema: o servidor é idempotente.
    fun verificarComprasPendentes() {
        if (!estaConectado) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, compras ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            compras
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { callback.onGooglePlayPurchaseCompleted(it) }
        }
    }

    fun encerrar() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "GooglePlayBilling"
    }
}

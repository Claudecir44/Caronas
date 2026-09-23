package com.cjstudio.caronas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.Purchase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Checkout do acesso pago do motorista — planos avulsos (Mensal 30 dias /
// Trimestral 90 dias, ver PlanoMotorista), sem
// renovação automática (ver Usuario.kt, firestore.rules:
// permiteOferecerCarona, functions/index.js:createPaymentPreferenceMotorista/
// paymentWebhookMotorista). Aberta pela OferecerCaronaActivity quando o
// motorista já usou as 10 caronas gratuitas e não tem acesso pago válido.
// Mesmo fluxo do AssinaturaActivity do Match: primeiro o Google mostra a
// escolha Google Play x Mercado Pago (User Choice Billing, ver
// GooglePlayBillingManager); pelo Google Play a compra é confirmada no
// servidor, pelo Mercado Pago abre o checkout no navegador (não numa WebView) e escuta em tempo real o próprio
// documento usuarios/{uid} até acessoMotoristaExpiraEm mudar — quem decide
// se o pagamento foi aprovado é sempre esse listener, nunca o retorno do
// deep link em si (ver onNewIntent).
//
// Abaixo do botão de pagar fica "Meus pagamentos" (data, valor e validade de
// cada pagamento) e, enquanto o acesso atual ainda tem mais de 2 dias, o botão
// fica desabilitado com o aviso de quando dá pra renovar (mesma regra que
// createPaymentPreferenceMotorista aplica no servidor — a trava de verdade).
@AndroidEntryPoint
class AssinaturaMotoristaActivity : AppCompatActivity(), GooglePlayBillingCallback {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var btnPagar: Button
    private lateinit var cardMensal: View
    private lateinit var cardTrimestral: View
    private var planoSelecionado = PlanoMotorista.MENSAL
    private lateinit var layoutAguardando: LinearLayout
    private lateinit var tvAvisoRenovacao: TextView
    private lateinit var tvSemPagamentos: TextView
    private lateinit var containerPagamentos: LinearLayout
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private var jobConfirmacao: Job? = null
    private var expiraEmAntesDaCompra: Long? = null
    private lateinit var billingManager: GooglePlayBillingManager
    // Tokens do Google Play já em confirmação — o mesmo pode chegar pelo
    // listener da compra e pela reconsulta do onResume ao mesmo tempo.
    private val comprasEmConfirmacao = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assinatura_motorista)

        btnPagar = findViewById(R.id.btnPagarAssinaturaMotorista)
        layoutAguardando = findViewById(R.id.layoutAguardandoConfirmacaoMotorista)
        tvAvisoRenovacao = findViewById(R.id.tvAvisoRenovacaoMotorista)
        tvSemPagamentos = findViewById(R.id.tvSemPagamentosMotorista)
        containerPagamentos = findViewById(R.id.containerMeusPagamentosMotorista)

        cardMensal = findViewById(R.id.cardPlanoMensal)
        cardTrimestral = findViewById(R.id.cardPlanoTrimestral)
        cardMensal.setOnClickListener { selecionarPlano(PlanoMotorista.MENSAL) }
        cardTrimestral.setOnClickListener { selecionarPlano(PlanoMotorista.TRIMESTRAL) }
        selecionarPlano(PlanoMotorista.MENSAL)

        billingManager = GooglePlayBillingManager(this, this)
        btnPagar.setOnClickListener { iniciarPagamento() }
    }

    private fun selecionarPlano(plano: PlanoMotorista) {
        planoSelecionado = plano
        cardMensal.isSelected = plano == PlanoMotorista.MENSAL
        cardTrimestral.isSelected = plano == PlanoMotorista.TRIMESTRAL
    }

    override fun onResume() {
        super.onResume()
        carregarStatusEPagamentos()
        // Compra pelo Google Play que ficou pendente (Pix/boleto) e já foi paga.
        billingManager.verificarComprasPendentes()
    }

    // Relê usuário e pagamentos toda vez que a tela volta ao topo — cobre a
    // volta do checkout e a virada do dia (o botão libera sozinho quando entra
    // na janela de 2 dias).
    private fun carregarStatusEPagamentos() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario -> aplicarRegraDeRenovacao(usuario) }
            usuarioRepository.buscarMeusPagamentosMotorista()
                .onSuccess { mostrarPagamentos(it) }
                .onFailure {
                    Log.e(TAG, "Erro ao carregar pagamentos", it)
                    Toast.makeText(this@AssinaturaMotoristaActivity, R.string.assinatura_motorista_erro_carregar_pagamentos, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun aplicarRegraDeRenovacao(usuario: Usuario) {
        if (AcessoMotoristaUtil.podePagarNovamente(usuario)) {
            // Se um checkout foi aberto e ainda estamos esperando a confirmação
            // (jobConfirmacao ativo), o botão continua desabilitado — senão
            // a volta do navegador reabilitaria e daria pra pagar em dobro.
            btnPagar.isEnabled = jobConfirmacao == null
            tvAvisoRenovacao.visibility = View.GONE
        } else {
            val libera = AcessoMotoristaUtil.liberaRenovacaoEm(usuario) ?: return
            btnPagar.isEnabled = false
            tvAvisoRenovacao.text = getString(R.string.assinatura_motorista_renovacao_bloqueada, formatoData.format(Date(libera)))
            tvAvisoRenovacao.visibility = View.VISIBLE
        }
    }

    private fun mostrarPagamentos(pagamentos: List<PagamentoMotorista>) {
        containerPagamentos.removeAllViews()
        tvSemPagamentos.visibility = if (pagamentos.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (p in pagamentos) {
            val linha = inflater.inflate(R.layout.item_meu_pagamento_motorista, containerPagamentos, false)
            val dataTexto = getString(R.string.assinatura_motorista_pagamento_linha_data, p.dataCompra?.let { formatoData.format(Date(it)) } ?: "—")
            linha.findViewById<TextView>(R.id.tvPagamentoData).text =
                if (p.plano.isNullOrBlank()) dataTexto else "$dataTexto · ${p.plano}"
            linha.findViewById<TextView>(R.id.tvPagamentoValidade).text = if (p.estornado) {
                getString(R.string.assinatura_motorista_pagamento_estornado)
            } else {
                getString(R.string.assinatura_motorista_pagamento_linha_validade, p.expiraEm?.let { formatoData.format(Date(it)) } ?: "—")
            }
            linha.findViewById<TextView>(R.id.tvPagamentoValor).text =
                getString(R.string.assinatura_motorista_pagamento_linha_valor, String.format(Locale("pt", "BR"), "%.2f", p.valor))
            containerPagamentos.addView(linha)
        }
    }

    // Abre a tela do Google com a escolha Google Play x Mercado Pago — o que
    // o motorista escolher chega por onGooglePlayPurchaseCompleted ou
    // onUserChoseAlternativeBilling.
    private fun iniciarPagamento() {
        val uid = usuarioRepository.uidLogado() ?: return
        btnPagar.isEnabled = false
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                expiraEmAntesDaCompra = usuario.acessoMotoristaExpiraEm?.time
            }
            billingManager.conectar { billingManager.iniciarCompra(this@AssinaturaMotoristaActivity, planoSelecionado, uid) }
        }
    }

    override fun onUserChoseAlternativeBilling(externalTransactionToken: String) {
        lifecycleScope.launch {
            usuarioRepository.iniciarPagamentoAcessoMotorista(planoSelecionado, externalTransactionToken)
                .onSuccess { initPoint ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(initPoint)))
                        iniciarEscutaDeConfirmacao()
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao abrir checkout", e)
                        btnPagar.isEnabled = true
                        Toast.makeText(this@AssinaturaMotoristaActivity, R.string.assinatura_motorista_erro_abrir_checkout, Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure { e ->
                    btnPagar.isEnabled = true
                    Toast.makeText(this@AssinaturaMotoristaActivity, getString(R.string.assinatura_motorista_erro_checkout, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onGooglePlayPurchaseCompleted(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        if (!comprasEmConfirmacao.add(purchase.purchaseToken)) return
        lifecycleScope.launch {
            // Compra retomada pelo onResume (sem passar por iniciarPagamento):
            // a validade "de antes" ainda não foi lida — sem ela, um acesso
            // já válido seria confundido com a confirmação desta compra.
            if (jobConfirmacao == null) {
                usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                    expiraEmAntesDaCompra = usuario.acessoMotoristaExpiraEm?.time
                }
                iniciarEscutaDeConfirmacao()
            }
            usuarioRepository.confirmarCompraGooglePlayMotorista(purchase.purchaseToken, productId)
                .onSuccess { pendente ->
                    if (pendente) {
                        // O listener continua esperando; o onResume reconsulta.
                        Toast.makeText(this@AssinaturaMotoristaActivity, R.string.assinatura_motorista_pendente, Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Erro ao confirmar compra do Google Play", e)
                    pararEscutaDeConfirmacao()
                    Toast.makeText(this@AssinaturaMotoristaActivity, getString(R.string.assinatura_motorista_erro_google_play, e.message), Toast.LENGTH_LONG).show()
                }
            comprasEmConfirmacao.remove(purchase.purchaseToken)
        }
    }

    override fun onBillingError(mensagem: String) {
        runOnUiThread {
            if (jobConfirmacao == null) btnPagar.isEnabled = true
            Toast.makeText(this, getString(R.string.assinatura_motorista_erro_google_play, mensagem), Toast.LENGTH_LONG).show()
        }
    }

    private fun pararEscutaDeConfirmacao() {
        jobConfirmacao?.cancel()
        jobConfirmacao = null
        layoutAguardando.visibility = View.GONE
        btnPagar.isEnabled = true
    }

    private fun iniciarEscutaDeConfirmacao() {
        layoutAguardando.visibility = View.VISIBLE

        jobConfirmacao?.cancel()
        jobConfirmacao = lifecycleScope.launch {
            // Espera o webhook mudar a validade do acesso pra uma data futura
            // diferente da de antes da compra — só isso confirma o pagamento.
            usuarioRepository.escutarAcessoMotorista()
                .catch { Log.e(TAG, "Erro ao escutar confirmação de pagamento", it) }
                .firstOrNull { expiraEm -> expiraEm > System.currentTimeMillis() && expiraEm != expiraEmAntesDaCompra }
                ?: return@launch

            jobConfirmacao = null
            layoutAguardando.visibility = View.GONE

            Toast.makeText(this@AssinaturaMotoristaActivity, R.string.assinatura_motorista_sucesso, Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    // Retorno do checkout via deep link (caronasapp://payment_success/...,
    // ver AndroidManifest.xml + back_urls em createPaymentPreferenceMotorista).
    // Só traz a tela de volta pro topo — quem decide se o pagamento foi
    // aprovado é sempre o listener acima.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        jobConfirmacao?.cancel()
        billingManager.encerrar()
    }

    companion object {
        private const val TAG = "AssinaturaMotorista"
    }
}

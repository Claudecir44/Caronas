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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Checkout do acesso pago do motorista — R$15,99 = 30 dias avulsos, sem
// renovação automática (ver Usuario.kt, firestore.rules:
// permiteOferecerCarona, functions/index.js:createPaymentPreferenceMotorista/
// paymentWebhookMotorista). Aberta pela OferecerCaronaActivity quando o
// motorista já usou as 10 caronas gratuitas e não tem acesso pago válido.
// Mesmo fluxo do AssinaturaActivity do Match: abre o checkout do Mercado
// Pago no navegador (não numa WebView) e escuta em tempo real o próprio
// documento usuarios/{uid} até acessoMotoristaExpiraEm mudar — quem decide
// se o pagamento foi aprovado é sempre esse listener, nunca o retorno do
// deep link em si (ver onNewIntent).
//
// Abaixo do botão de pagar fica "Meus pagamentos" (data, valor e validade de
// cada pagamento) e, enquanto o acesso atual ainda tem mais de 2 dias, o botão
// fica desabilitado com o aviso de quando dá pra renovar (mesma regra que
// createPaymentPreferenceMotorista aplica no servidor — a trava de verdade).
@AndroidEntryPoint
class AssinaturaMotoristaActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var btnPagar: Button
    private lateinit var layoutAguardando: LinearLayout
    private lateinit var tvAvisoRenovacao: TextView
    private lateinit var tvSemPagamentos: TextView
    private lateinit var containerPagamentos: LinearLayout
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private var jobConfirmacao: Job? = null
    private var expiraEmAntesDaCompra: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assinatura_motorista)

        btnPagar = findViewById(R.id.btnPagarAssinaturaMotorista)
        layoutAguardando = findViewById(R.id.layoutAguardandoConfirmacaoMotorista)
        tvAvisoRenovacao = findViewById(R.id.tvAvisoRenovacaoMotorista)
        tvSemPagamentos = findViewById(R.id.tvSemPagamentosMotorista)
        containerPagamentos = findViewById(R.id.containerMeusPagamentosMotorista)

        btnPagar.setOnClickListener { iniciarPagamento() }
    }

    override fun onResume() {
        super.onResume()
        carregarStatusEPagamentos()
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
            linha.findViewById<TextView>(R.id.tvPagamentoData).text =
                getString(R.string.assinatura_motorista_pagamento_linha_data, p.dataCompra?.let { formatoData.format(Date(it)) } ?: "—")
            linha.findViewById<TextView>(R.id.tvPagamentoValidade).text =
                getString(R.string.assinatura_motorista_pagamento_linha_validade, p.expiraEm?.let { formatoData.format(Date(it)) } ?: "—")
            linha.findViewById<TextView>(R.id.tvPagamentoValor).text =
                getString(R.string.assinatura_motorista_pagamento_linha_valor, String.format(Locale("pt", "BR"), "%.2f", p.valor))
            containerPagamentos.addView(linha)
        }
    }

    private fun iniciarPagamento() {
        btnPagar.isEnabled = false
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                expiraEmAntesDaCompra = usuario.acessoMotoristaExpiraEm?.time
            }
            usuarioRepository.iniciarPagamentoAcessoMotorista()
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
    }

    companion object {
        private const val TAG = "AssinaturaMotorista"
    }
}

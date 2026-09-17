package com.cjstudio.caronas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
@AndroidEntryPoint
class AssinaturaMotoristaActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var btnPagar: Button
    private lateinit var layoutAguardando: LinearLayout
    private var listenerConfirmacao: ListenerRegistration? = null
    private var expiraEmAntesDaCompra: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assinatura_motorista)

        btnPagar = findViewById(R.id.btnPagarAssinaturaMotorista)
        layoutAguardando = findViewById(R.id.layoutAguardandoConfirmacaoMotorista)

        btnPagar.setOnClickListener { iniciarPagamento() }
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        layoutAguardando.visibility = View.VISIBLE

        listenerConfirmacao?.remove()
        listenerConfirmacao = FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao escutar confirmação de pagamento", error)
                    return@addSnapshotListener
                }
                val expiraEm = snapshot?.getTimestamp("acessoMotoristaExpiraEm")?.toDate()?.time ?: return@addSnapshotListener
                val agora = System.currentTimeMillis()
                if (expiraEm > agora && expiraEm != expiraEmAntesDaCompra) {
                    listenerConfirmacao?.remove()
                    listenerConfirmacao = null
                    layoutAguardando.visibility = View.GONE

                    Toast.makeText(this, R.string.assinatura_motorista_sucesso, Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
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
        listenerConfirmacao?.remove()
    }

    companion object {
        private const val TAG = "AssinaturaMotorista"
    }
}

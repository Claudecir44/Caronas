package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela de Configurações — aberta pelo ícone ⚙️ no canto superior esquerdo
// da tela principal (mesmo lugar/ícone do Match). Bem mais enxuta que a
// ConfiguracoesActivity do Match (que tem ~9 opções: idioma, pagamentos,
// remoção de conta com CPF, etc.) — Caronas só precisa das opções pedidas.
// Cada opção é um CardView com ícone+texto+seta (não mais um Button
// simples) — visual de lista de configurações, mais próximo do que apps
// nativos costumam usar.
//
// "Editar Perfil" NÃO fica mais aqui: já existe o "Meu Perfil" na tela
// principal (TelaCaronasActivity.tvMeuPerfil), que abre a mesma tela de
// edição — ter os dois era duplicado.
//
// "Pagamentos" só aparece pro motorista (passageiro não paga nada): abre o
// checkout do acesso pago (AssinaturaMotoristaActivity) e mostra logo abaixo
// o status atual ("Grátis: 09/10" ou "Acesso pago até DD/MM/AAAA"), o mesmo
// texto do contador da tela principal (AcessoMotoristaUtil.textoStatus).
// O status é relido no onResume pra refletir o pagamento assim que o
// motorista volta do checkout.
@AndroidEntryPoint
class ConfiguracoesCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var cardPagamentos: View
    private lateinit var tvPagamentosStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes_caronas)

        cardPagamentos = findViewById(R.id.cardConfigPagamentos)
        tvPagamentosStatus = findViewById(R.id.tvConfigPagamentosStatus)

        findViewById<View>(R.id.btnConfigPagamentos).setOnClickListener {
            startActivity(Intent(this, AssinaturaMotoristaActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigTermos).setOnClickListener {
            startActivity(Intent(this, TermosPrivacidadeCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigManifestacoes).setOnClickListener {
            startActivity(Intent(this, EnviarManifestacaoActivity::class.java))
        }
        findViewById<View>(R.id.btnVoltarConfig).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarPagamentos()
    }

    private fun atualizarPagamentos() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                if (usuario.motorista) {
                    cardPagamentos.visibility = View.VISIBLE
                    tvPagamentosStatus.visibility = View.VISIBLE
                    tvPagamentosStatus.text = AcessoMotoristaUtil.textoStatus(this@ConfiguracoesCaronasActivity, usuario)
                } else {
                    cardPagamentos.visibility = View.GONE
                }
            }
        }
    }
}

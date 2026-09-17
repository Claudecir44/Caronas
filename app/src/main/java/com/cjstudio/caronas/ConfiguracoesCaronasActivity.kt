package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

// Tela de Configurações — aberta pelo ícone ⚙️ no canto superior esquerdo
// da tela principal (mesmo lugar/ícone do Match). Bem mais enxuta que a
// ConfiguracoesActivity do Match (que tem ~9 opções: idioma, pagamentos,
// remoção de conta com CPF, etc.) — Caronas só precisa das opções pedidas.
// Sem @AndroidEntryPoint: não injeta nada, é só navegação. Cada opção é um
// CardView com ícone+texto+seta (não mais um Button simples) — visual de
// lista de configurações, mais próximo do que apps nativos costumam usar.
class ConfiguracoesCaronasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes_caronas)

        findViewById<View>(R.id.btnConfigTermos).setOnClickListener {
            startActivity(Intent(this, TermosPrivacidadeCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigEditarPerfil).setOnClickListener {
            startActivity(Intent(this, EditarCadastroCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigManifestacoes).setOnClickListener {
            startActivity(Intent(this, EnviarManifestacaoActivity::class.java))
        }
        findViewById<View>(R.id.btnVoltarConfig).setOnClickListener {
            finish()
        }
    }
}

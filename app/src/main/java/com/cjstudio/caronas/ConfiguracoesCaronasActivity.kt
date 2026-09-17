package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

// Tela de Configurações — aberta pelo ícone ⚙️ no canto superior esquerdo
// da tela principal (mesmo lugar/ícone do Match). Bem mais enxuta que a
// ConfiguracoesActivity do Match (que tem ~9 opções: idioma, pagamentos,
// remoção de conta com CPF, etc.) — Caronas só precisa das duas pedidas.
// Sem @AndroidEntryPoint: não injeta nada, é só navegação.
class ConfiguracoesCaronasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes_caronas)

        findViewById<Button>(R.id.btnConfigTermos).setOnClickListener {
            startActivity(Intent(this, TermosPrivacidadeCaronasActivity::class.java))
        }
        findViewById<Button>(R.id.btnConfigEditarPerfil).setOnClickListener {
            startActivity(Intent(this, EditarCadastroCaronasActivity::class.java))
        }
    }
}

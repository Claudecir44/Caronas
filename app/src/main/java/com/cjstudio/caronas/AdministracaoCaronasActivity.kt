package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Placeholder — o painel administrativo de verdade (gestão de usuários,
// viagens, denúncias) fica pra próxima fase (ver plano da fase 1).
@AndroidEntryPoint
class AdministracaoCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_administracao_caronas)

        findViewById<Button>(R.id.btnSair).setOnClickListener {
            lifecycleScope.launch {
                usuarioRepository.logout()
                startActivity(Intent(this@AdministracaoCaronasActivity, LoginAdminCaronasActivity::class.java))
                finish()
            }
        }
    }
}

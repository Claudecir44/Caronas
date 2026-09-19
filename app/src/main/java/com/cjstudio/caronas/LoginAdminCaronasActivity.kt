package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Login da flavor Admin Caronas — mesmo Firebase Auth dos usuários comuns,
// mas só entra quem tem entrada na coleção "admins" (ver AdminRepository
// .souAdmin e firestore.rules, função ehAdmin()).
@AndroidEntryPoint
class LoginAdminCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_admin_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etSenha = findViewById<EditText>(R.id.etSenha)
        etSenha.habilitarToggleSenha()
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        findViewById<android.widget.TextView>(R.id.tvCadastrarAdmin).setOnClickListener {
            startActivity(Intent(this, CadastroAdminCaronasActivity::class.java))
        }

        btnEntrar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString().trim()
            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, R.string.login_erro_campos_obrigatorios, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                adminRepository.loginAdmin(email, senha)
                    .onSuccess {
                        startActivity(Intent(this@LoginAdminCaronasActivity, AdministracaoCaronasActivity::class.java))
                        finish()
                    }
                    .onFailure { e ->
                        progressBar.visibility = View.GONE
                        val mensagem = when (e) {
                            is EmailNaoVerificadoException -> e.message.orEmpty()
                            is AcessoAdminRestritoException -> getString(R.string.login_erro_acesso_restrito_admin)
                            else -> getString(R.string.erro_generico, e.message)
                        }
                        Toast.makeText(this@LoginAdminCaronasActivity, mensagem, Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
}

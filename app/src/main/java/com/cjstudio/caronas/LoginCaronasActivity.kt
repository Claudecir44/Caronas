package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var cbLoginMotorista: CheckBox
    private lateinit var cbLoginPassageiro: CheckBox
    private lateinit var btnEntrar: Button
    private lateinit var tvEsqueciSenha: TextView
    private lateinit var tvCriarConta: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        etSenha.habilitarToggleSenha()
        cbLoginMotorista = findViewById(R.id.cbLoginMotorista)
        cbLoginPassageiro = findViewById(R.id.cbLoginPassageiro)
        btnEntrar = findViewById(R.id.btnEntrar)
        tvEsqueciSenha = findViewById(R.id.tvEsqueciSenha)
        tvCriarConta = findViewById(R.id.tvCriarConta)
        progressBar = findViewById(R.id.progressBar)

        // Mutuamente exclusivas — marcar uma desmarca a outra (não é papel
        // de conta separado, é só a escolha de como entrar desta vez;
        // atualiza o campo "motorista" do usuário se ele marcar alguma).
        cbLoginMotorista.setOnCheckedChangeListener { _, marcado ->
            if (marcado) cbLoginPassageiro.isChecked = false
        }
        cbLoginPassageiro.setOnCheckedChangeListener { _, marcado ->
            if (marcado) cbLoginMotorista.isChecked = false
        }

        btnEntrar.setOnClickListener { fazerLogin() }
        tvCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroCaronasActivity::class.java))
        }
        tvEsqueciSenha.setOnClickListener { enviarRedefinicaoSenha() }
    }

    private fun fazerLogin() {
        val email = etEmail.text.toString().trim()
        val senha = etSenha.text.toString().trim()

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, R.string.login_erro_campos_obrigatorios, Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val resultado = usuarioRepository.login(email, senha)
            resultado.onSuccess { usuario ->
                // Se marcou alguma opção, aplica a escolha desta sessão ao
                // cadastro; se não marcou nenhuma, mantém o papel que já
                // estava salvo (login sem trocar nada).
                if (cbLoginMotorista.isChecked) {
                    usuarioRepository.atualizarPapelMotorista(usuario.id!!, true)
                } else if (cbLoginPassageiro.isChecked) {
                    usuarioRepository.atualizarPapelMotorista(usuario.id!!, false)
                }
                progressBar.visibility = View.GONE
                startActivity(Intent(this@LoginCaronasActivity, TelaCaronasActivity::class.java))
                finish()
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this@LoginCaronasActivity, getString(R.string.erro_generico, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarRedefinicaoSenha() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, R.string.login_erro_campos_obrigatorios, Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.login_email_redefinicao_enviado, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.erro_generico, e.message), Toast.LENGTH_LONG).show()
            }
    }
}

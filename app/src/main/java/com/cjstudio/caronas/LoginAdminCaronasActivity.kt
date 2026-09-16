package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Login da flavor Admin Caronas — mesmo Firebase Auth dos usuários comuns,
// mas só entra quem tem entrada na coleção "admins" (ver AdminRepository
// .souAdmin e firestore.rules, função ehAdmin()).
@AndroidEntryPoint
class LoginAdminCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: DataStore<Preferences>

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
                try {
                    val auth = FirebaseAuth.getInstance()
                    val resultado = auth.signInWithEmailAndPassword(email, senha).await()
                    val firebaseUser = resultado.user

                    // reload() antes de checar isEmailVerified — mesmo
                    // motivo do UsuarioRepository.login (ver comentário lá):
                    // sem isso, o valor pode vir desatualizado mesmo já
                    // tendo validado o e-mail.
                    firebaseUser?.reload()?.await()

                    // Mesma trava do login de usuário comum (ver
                    // UsuarioRepository.login) — a conta admin usa o mesmo
                    // Firebase Auth do app, então precisa do mesmo cadastro
                    // validado por e-mail antes de entrar. Com cooldown (ver
                    // reenviarVerificacaoComCooldown) — mesmo motivo do
                    // login comum.
                    if (firebaseUser != null && !firebaseUser.isEmailVerified) {
                        val mensagem = reenviarVerificacaoComCooldown(firebaseUser, prefs)
                        auth.signOut()
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@LoginAdminCaronasActivity, mensagem, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val uid = firebaseUser?.uid
                    if (uid == null) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@LoginAdminCaronasActivity, R.string.erro_generico, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    // Fecha a brecha que existia até aqui: autenticar no
                    // mesmo Firebase Auth dos usuários comuns não bastava
                    // pra entrar como admin — precisa também ter entrada em
                    // admins/{uid} (ver firestore.rules, função ehAdmin()).
                    val ehAdmin = adminRepository.souAdmin(uid).getOrDefault(false)
                    if (!ehAdmin) {
                        auth.signOut()
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@LoginAdminCaronasActivity, R.string.login_erro_acesso_restrito_admin, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    prefs.edit { it[KEY_USUARIO_ID] = uid }
                    startActivity(Intent(this@LoginAdminCaronasActivity, AdministracaoCaronasActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginAdminCaronasActivity, getString(R.string.erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

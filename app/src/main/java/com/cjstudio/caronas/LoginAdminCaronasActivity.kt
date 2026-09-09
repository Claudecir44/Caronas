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

// Login mínimo da flavor Admin Caronas — fase 1 não tem painel de verdade
// ainda (ver AdministracaoCaronasActivity), então isso só prova que a
// flavor compila/instala e loga de ponta a ponta.
@AndroidEntryPoint
class LoginAdminCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restaurarRedimensionamentoComTeclado()
        setContentView(R.layout.activity_login_admin_caronas)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etSenha = findViewById<EditText>(R.id.etSenha)
        etSenha.habilitarToggleSenha()
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

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
                    val resultado = FirebaseAuth.getInstance().signInWithEmailAndPassword(email, senha).await()
                    val uid = resultado.user?.uid
                    if (uid != null) {
                        prefs.edit { it[KEY_USUARIO_ID] = uid }
                    }
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

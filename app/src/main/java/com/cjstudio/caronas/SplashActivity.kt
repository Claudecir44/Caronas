package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Decide pra onde ir com base em duas coisas — sessão do Firebase Auth
// (currentUser != null) E o id salvo na DataStore (mesmo padrão de dupla
// checagem do SplashActivity.kt do Match) — e qual flavor está rodando
// (BuildConfig.TIPO), já que "usuario" e "admin" têm telas totalmente
// diferentes.
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: DataStore<Preferences>

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            try {
                val logado = usuarioRepository.estaLogado() &&
                    !prefs.data.first()[KEY_USUARIO_ID].isNullOrEmpty()

                val destino = if (BuildConfig.TIPO == "admin") {
                    if (logado) AdministracaoCaronasActivity::class.java else LoginAdminCaronasActivity::class.java
                } else {
                    if (logado) TelaCaronasActivity::class.java else LoginCaronasActivity::class.java
                }
                startActivity(Intent(this@SplashActivity, destino))
            } catch (e: Exception) {
                val destinoFallback = if (BuildConfig.TIPO == "admin") LoginAdminCaronasActivity::class.java else LoginCaronasActivity::class.java
                startActivity(Intent(this@SplashActivity, destinoFallback))
            } finally {
                finish()
            }
        }
    }
}

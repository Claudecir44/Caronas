package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Lista todos os admins/colaboradores (ver Admin.kt/AdminRepository
// .listarTodosAdmins) — aberta a partir do card "Administração" em
// Configurações (só visível pra quem tem a permissão "administradores", ver
// ConfiguracoesCaronasActivity), com um botão "Novo Admin" e toque em
// qualquer linha abrindo CadastroAdminCaronasActivity em modo edição
// (papel/permissões inclusos). Recarrega no onResume pra refletir uma
// criação/edição/exclusão recém-feita.
@AndroidEntryPoint
class GerenciarAdministradoresCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var tvVazio: TextView
    private lateinit var rvAdministradores: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_administradores_caronas)

        progressBar = findViewById(R.id.progressBarAdmins)
        tvVazio = findViewById(R.id.tvAdminsVazio)
        rvAdministradores = findViewById(R.id.rvAdministradores)
        rvAdministradores.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnNovoAdmin).setOnClickListener {
            startActivity(Intent(this, CadastroAdminCaronasActivity::class.java))
        }
        findViewById<Button>(R.id.btnVoltarAdmins).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        carregarAdministradores()
    }

    private fun carregarAdministradores() {
        progressBar.visibility = View.VISIBLE
        tvVazio.visibility = View.GONE
        rvAdministradores.visibility = View.GONE

        lifecycleScope.launch {
            adminRepository.listarTodosAdmins()
                .onSuccess { admins ->
                    progressBar.visibility = View.GONE
                    if (admins.isEmpty()) {
                        tvVazio.visibility = View.VISIBLE
                    } else {
                        rvAdministradores.visibility = View.VISIBLE
                        rvAdministradores.adapter = AdministradorAdapter(admins) { admin ->
                            val uid = admin.id ?: return@AdministradorAdapter
                            val intent = Intent(this@GerenciarAdministradoresCaronasActivity, CadastroAdminCaronasActivity::class.java)
                            intent.putExtra(CadastroAdminCaronasActivity.EXTRA_UID_EDITAR, uid)
                            startActivity(intent)
                        }
                    }
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@GerenciarAdministradoresCaronasActivity, getString(R.string.admin_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }
}

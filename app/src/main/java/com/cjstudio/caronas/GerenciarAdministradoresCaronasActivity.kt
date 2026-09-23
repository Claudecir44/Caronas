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
// .listarTodosAdmins) — aberta a partir do card "Administradores" em
// Configurações (só visível pra quem tem a permissão "administradores", ver
// ConfiguracoesCaronasActivity), com um botão "Novo Admin" e toque em
// qualquer linha abrindo CadastroAdminCaronasActivity em modo edição
// (papel/permissões inclusos). Recarrega no onResume pra refletir uma
// criação/edição/exclusão recém-feita.
//
// Duas abas (Admins | Colaboradores, ver btnAbaAdmins/btnAbaColaboradores),
// mesmo formato de Motoristas/Passageiros no dashboard
// (AdministracaoCaronasActivity) — uma busca só ao Firestore, filtrada
// cliente-side ao trocar de aba (ver exibirListaFiltrada).
@AndroidEntryPoint
class GerenciarAdministradoresCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var tvVazio: TextView
    private lateinit var rvAdministradores: RecyclerView
    private lateinit var tvTituloAba: TextView
    private lateinit var tvContadorAba: TextView

    private var todosAdmins: List<Admin> = emptyList()
    private var abaColaboradores = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_administradores_caronas)

        progressBar = findViewById(R.id.progressBarAdmins)
        tvVazio = findViewById(R.id.tvAdminsVazio)
        rvAdministradores = findViewById(R.id.rvAdministradores)
        rvAdministradores.layoutManager = LinearLayoutManager(this)
        tvTituloAba = findViewById(R.id.tvTituloAbaAdministradores)
        tvContadorAba = findViewById(R.id.tvContadorAbaAdministradores)

        findViewById<TextView>(R.id.btnAbaAdmins).setOnClickListener {
            abaColaboradores = false
            exibirListaFiltrada()
        }
        findViewById<TextView>(R.id.btnAbaColaboradores).setOnClickListener {
            abaColaboradores = true
            exibirListaFiltrada()
        }

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
        tvTituloAba.visibility = View.GONE
        tvContadorAba.visibility = View.GONE
        tvVazio.visibility = View.GONE
        rvAdministradores.visibility = View.GONE

        lifecycleScope.launch {
            adminRepository.listarTodosAdmins()
                .onSuccess { admins ->
                    todosAdmins = admins
                    progressBar.visibility = View.GONE
                    exibirListaFiltrada()
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@GerenciarAdministradoresCaronasActivity, getString(R.string.admin_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun exibirListaFiltrada() {
        val lista = todosAdmins.filter { it.ehColaborador == abaColaboradores }

        tvTituloAba.text = getString(if (abaColaboradores) R.string.gerenciar_admins_aba_colaboradores else R.string.gerenciar_admins_aba_admins)
        tvTituloAba.visibility = View.VISIBLE
        tvContadorAba.text = lista.size.toString()
        tvContadorAba.visibility = View.VISIBLE

        if (lista.isEmpty()) {
            tvVazio.text = getString(if (abaColaboradores) R.string.gerenciar_admins_vazio_colaboradores else R.string.gerenciar_admins_vazio_admins)
            tvVazio.visibility = View.VISIBLE
            rvAdministradores.visibility = View.GONE
        } else {
            tvVazio.visibility = View.GONE
            rvAdministradores.visibility = View.VISIBLE
            rvAdministradores.adapter = AdministradorAdapter(lista) { admin ->
                val uid = admin.id ?: return@AdministradorAdapter
                val intent = Intent(this@GerenciarAdministradoresCaronasActivity, CadastroAdminCaronasActivity::class.java)
                intent.putExtra(CadastroAdminCaronasActivity.EXTRA_UID_EDITAR, uid)
                startActivity(intent)
            }
        }
    }
}

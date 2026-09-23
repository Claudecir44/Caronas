package com.cjstudio.caronas

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Escolher com quem começar uma conversa nova no chat entre administradores
// (ver ConversasAdminCaronasActivity, botão "+ Nova Conversa") — devolve o
// admin escolhido via resultado da Activity, mesmo espírito do
// EscolherAdminChatActivity do Match.
@AndroidEntryPoint
class EscolherAdminChatCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escolher_admin_chat_caronas)

        val rvAdmins = findViewById<RecyclerView>(R.id.rvEscolherAdmin)
        val tvVazio = findViewById<TextView>(R.id.tvSemAdminsEscolher)
        rvAdmins.layoutManager = LinearLayoutManager(this)

        val meuId = adminRepository.uidLogado()

        lifecycleScope.launch {
            adminRepository.listarTodosAdmins()
                .onSuccess { admins ->
                    val outros = admins.filter { it.id != meuId }.sortedBy { it.nomeCompleto.lowercase() }
                    if (outros.isEmpty()) {
                        tvVazio.visibility = View.VISIBLE
                        rvAdmins.visibility = View.GONE
                    } else {
                        tvVazio.visibility = View.GONE
                        rvAdmins.visibility = View.VISIBLE
                        rvAdmins.adapter = SelecionarAdminAdapter(outros) { admin -> escolher(admin) }
                    }
                }
                .onFailure { e ->
                    Toast.makeText(this@EscolherAdminChatCaronasActivity, getString(R.string.escolher_admin_chat_erro, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun escolher(admin: Admin) {
        val resultado = Intent().apply {
            putExtra(EXTRA_ADMIN_ID, admin.id)
            putExtra(EXTRA_ADMIN_NOME, admin.nomeCompleto.ifEmpty { admin.email ?: "" })
            putExtra(EXTRA_ADMIN_FOTO, admin.fotoUrl)
        }
        setResult(Activity.RESULT_OK, resultado)
        finish()
    }

    companion object {
        const val EXTRA_ADMIN_ID = "adminId"
        const val EXTRA_ADMIN_NOME = "adminNome"
        const val EXTRA_ADMIN_FOTO = "adminFoto"
    }
}

package com.cjstudio.caronas

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Lista de conversas do chat entre administradores — mesmo espírito de
// ConversasCaronaActivity, com um botão de nova conversa (escolhe outro
// admin, ver EscolherAdminChatCaronasActivity) e toque-e-segure pra excluir
// a conversa inteira (mesma função do Match).
@AndroidEntryPoint
class ConversasAdminCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var chatAdminCaronaRepository: IChatAdminCaronaRepository

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var rvConversas: RecyclerView
    private lateinit var tvSemConversas: TextView

    private val seletorAdmin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val dados = resultado.data ?: return@registerForActivityResult
        val adminId = dados.getStringExtra(EscolherAdminChatCaronasActivity.EXTRA_ADMIN_ID) ?: return@registerForActivityResult
        val adminNome = dados.getStringExtra(EscolherAdminChatCaronasActivity.EXTRA_ADMIN_NOME)
        val adminFoto = dados.getStringExtra(EscolherAdminChatCaronasActivity.EXTRA_ADMIN_FOTO)
        abrirOuCriarConversa(adminId, adminNome, adminFoto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversas_admin_caronas)

        rvConversas = findViewById(R.id.rvConversasAdmin)
        tvSemConversas = findViewById(R.id.tvSemConversasAdmin)
        rvConversas.layoutManager = LinearLayoutManager(this)

        findViewById<android.widget.Button>(R.id.btnNovaConversaAdmin).setOnClickListener {
            seletorAdmin.launch(Intent(this, EscolherAdminChatCaronasActivity::class.java))
        }

        val meuId = adminRepository.uidLogado() ?: return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatAdminCaronaRepository.escutarMinhasConversas().catch { }.collect { conversas ->
                    tvSemConversas.visibility = if (conversas.isEmpty()) View.VISIBLE else View.GONE
                    rvConversas.visibility = if (conversas.isEmpty()) View.GONE else View.VISIBLE
                    rvConversas.adapter = ConversaAdminAdapter(
                        conversas,
                        meuId,
                        onClick = { conversa -> abrirChat(conversa) },
                        onLongClick = { conversa -> confirmarExclusao(conversa, meuId) }
                    )
                }
            }
        }
    }

    private fun abrirOuCriarConversa(adminId: String, adminNome: String?, adminFoto: String?) {
        lifecycleScope.launch {
            chatAdminCaronaRepository.buscarOuCriarConversa(adminId, adminNome, adminFoto)
                .onSuccess { conversa -> abrirChat(conversa) }
                .onFailure { e ->
                    Toast.makeText(this@ConversasAdminCaronasActivity, getString(R.string.escolher_admin_chat_erro, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun abrirChat(conversa: ConversaAdmin) {
        val intent = Intent(this, ChatAdminCaronaActivity::class.java)
        intent.putExtra(ChatAdminCaronaActivity.EXTRA_CONVERSA, conversa)
        startActivity(intent)
    }

    private fun confirmarExclusao(conversa: ConversaAdmin, meuId: String) {
        val nomeOutro = conversa.nomeOutroAdmin(meuId).orEmpty()
        AlertDialog.Builder(this)
            .setTitle(R.string.conversas_admin_excluir_titulo)
            .setMessage(getString(R.string.conversas_admin_excluir_mensagem, nomeOutro))
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    chatAdminCaronaRepository.apagarConversa(conversa)
                        .onSuccess {
                            Toast.makeText(this@ConversasAdminCaronasActivity, R.string.conversas_admin_excluir_sucesso, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@ConversasAdminCaronasActivity, getString(R.string.conversas_admin_excluir_erro, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}

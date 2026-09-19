package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

@AndroidEntryPoint
class ConversasCaronaActivity : AppCompatActivity() {

    @Inject
    lateinit var chatCaronaRepository: IChatCaronaRepository

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var rvConversas: RecyclerView
    private lateinit var tvSemConversas: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversas_carona)

        rvConversas = findViewById(R.id.rvConversasCarona)
        tvSemConversas = findViewById(R.id.tvSemConversas)
        rvConversas.layoutManager = LinearLayoutManager(this)

        val meuId = usuarioRepository.uidLogado() ?: return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatCaronaRepository.escutarMinhasConversas().catch { }.collect { conversas ->
                    tvSemConversas.visibility = if (conversas.isEmpty()) View.VISIBLE else View.GONE
                    rvConversas.visibility = if (conversas.isEmpty()) View.GONE else View.VISIBLE
                    rvConversas.adapter = ConversaCaronaAdapter(
                        conversas,
                        meuId,
                        onClick = { conversa ->
                            val intent = Intent(this@ConversasCaronaActivity, ChatCaronaActivity::class.java)
                            intent.putExtra(ChatCaronaActivity.EXTRA_CONVERSA, conversa)
                            startActivity(intent)
                        },
                        onLongClick = { conversa -> confirmarExcluirConversa(conversa) }
                    )
                }
            }
        }
    }

    private fun confirmarExcluirConversa(conversa: ConversaCarona) {
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_conversa_excluir_titulo)
            .setMessage(R.string.chat_conversa_excluir_mensagem)
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    chatCaronaRepository.excluirConversa(conversa)
                        .onSuccess {
                            Toast.makeText(this@ConversasCaronaActivity, R.string.chat_conversa_excluida_sucesso, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@ConversasCaronaActivity, getString(R.string.chat_conversa_erro_excluir, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}

package com.cjstudio.caronas

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatCaronaActivity : AppCompatActivity() {

    @Inject
    lateinit var chatCaronaRepository: IChatCaronaRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var tvNomeOutro: TextView
    private lateinit var tvRota: TextView
    private lateinit var rvMensagens: RecyclerView
    private lateinit var etMensagem: EditText

    private var conversaAtual: ConversaCarona? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_carona)
        findViewById<View>(R.id.rootChatCarona).ajustarPaddingParaTeclado()

        tvNomeOutro = findViewById(R.id.tvNomeOutroChat)
        tvRota = findViewById(R.id.tvRotaChat)
        rvMensagens = findViewById(R.id.rvMensagensChat)
        etMensagem = findViewById(R.id.etMensagemChat)
        val btnEnviar = findViewById<android.widget.Button>(R.id.btnEnviarChat)
        rvMensagens.layoutManager = LinearLayoutManager(this)
        btnEnviar.setOnClickListener { enviarMensagem() }

        @Suppress("DEPRECATION")
        val conversaExistente = intent.getSerializableExtra(EXTRA_CONVERSA) as? ConversaCarona
        @Suppress("DEPRECATION")
        val solicitacao = intent.getSerializableExtra(EXTRA_SOLICITACAO) as? Solicitacao

        when {
            // Veio da lista de conversas (botão Chat) — já sabemos qual é a
            // conversa, não precisa buscar/criar de novo.
            conversaExistente != null -> abrirConversa(conversaExistente)
            // Veio de "Solicitar vaga"/"Minhas Ofertas"/"Minhas Viagens" —
            // acha a conversa dessa solicitação ou cria na primeira mensagem.
            solicitacao != null -> iniciarConversa(solicitacao)
            else -> finish()
        }
    }

    private fun iniciarConversa(solicitacao: Solicitacao) {
        lifecycleScope.launch {
            chatCaronaRepository.buscarOuCriarConversa(solicitacao)
                .onSuccess { conversa -> abrirConversa(conversa) }
                .onFailure { e ->
                    Toast.makeText(this@ChatCaronaActivity, getString(R.string.chat_carona_erro_generico, e.message), Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun abrirConversa(conversa: ConversaCarona) {
        conversaAtual = conversa
        val meuId = auth.currentUser?.uid
        tvNomeOutro.text = conversa.nomeOutroUsuario(meuId) ?: getString(R.string.procurar_motorista_desconhecido)
        tvRota.text = getString(R.string.procurar_rota_formato, conversa.cidadeOrigem ?: "", conversa.cidadeDestino ?: "")

        lifecycleScope.launch { chatCaronaRepository.marcarConversaComoLida(conversa) }
        escutarMensagens(conversa.id!!)
    }

    private fun escutarMensagens(conversaId: String) {
        val meuId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatCaronaRepository.escutarMensagens(conversaId).catch { }.collect { mensagens ->
                    rvMensagens.adapter = MensagemCaronaAdapter(mensagens, meuId) { mensagem ->
                        mostrarDialogApagar(mensagem)
                    }
                    if (mensagens.isNotEmpty()) {
                        rvMensagens.scrollToPosition(mensagens.size - 1)
                    }
                }
            }
        }
    }

    private fun enviarMensagem() {
        val conversa = conversaAtual ?: return
        val texto = etMensagem.text.toString().trim()
        if (texto.isEmpty()) return

        etMensagem.setText("")
        lifecycleScope.launch {
            chatCaronaRepository.enviarMensagem(conversa, texto)
                .onFailure { e ->
                    Toast.makeText(this@ChatCaronaActivity, getString(R.string.chat_carona_erro_enviar, e.message), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun mostrarDialogApagar(mensagem: MensagemCarona) {
        val opcoes = arrayOf(
            getString(R.string.chat_carona_apagar_para_mim),
            getString(R.string.chat_carona_apagar_para_todos)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_carona_apagar_titulo)
            .setItems(opcoes) { _, escolha ->
                lifecycleScope.launch {
                    val resultado = if (escolha == 0) {
                        chatCaronaRepository.apagarMensagemParaMim(mensagem)
                    } else {
                        chatCaronaRepository.apagarMensagemParaTodos(mensagem)
                    }
                    resultado.onFailure { e ->
                        Toast.makeText(this@ChatCaronaActivity, getString(R.string.chat_carona_erro_apagar, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        const val EXTRA_SOLICITACAO = "solicitacao"
        const val EXTRA_CONVERSA = "conversa"
    }
}

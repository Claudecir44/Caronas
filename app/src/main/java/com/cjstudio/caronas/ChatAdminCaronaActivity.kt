package com.cjstudio.caronas

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Chat 1-a-1 entre dois administradores — mesmas funções do ChatAdminActivity
// do Match: mensagens cifradas (ver ChatAdminCryptoUtil, tratado dentro do
// Repository), status de leitura, apagar mensagem (só pra mim / pra todos).
@AndroidEntryPoint
class ChatAdminCaronaActivity : AppCompatActivity() {

    @Inject
    lateinit var chatAdminCaronaRepository: IChatAdminCaronaRepository

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var ivFotoOutro: ImageView
    private lateinit var tvNomeOutro: TextView
    private lateinit var rvMensagens: RecyclerView
    private lateinit var etMensagem: EditText

    private var conversaAtual: ConversaAdmin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_admin_caronas)
        findViewById<android.view.View>(R.id.rootChatAdminCaronas).ajustarPaddingParaTeclado()

        ivFotoOutro = findViewById(R.id.ivFotoOutroChatAdmin)
        tvNomeOutro = findViewById(R.id.tvNomeOutroChatAdmin)
        rvMensagens = findViewById(R.id.rvMensagensChatAdmin)
        etMensagem = findViewById(R.id.etMensagemChatAdmin)
        rvMensagens.layoutManager = LinearLayoutManager(this)
        findViewById<android.widget.Button>(R.id.btnEnviarChatAdmin).setOnClickListener { enviarMensagem() }

        @Suppress("DEPRECATION")
        val conversa = intent.getSerializableExtra(EXTRA_CONVERSA) as? ConversaAdmin
        if (conversa == null) {
            finish()
            return
        }
        abrirConversa(conversa)
    }

    private fun abrirConversa(conversa: ConversaAdmin) {
        conversaAtual = conversa
        val meuId = adminRepository.uidLogado()
        tvNomeOutro.text = conversa.nomeOutroAdmin(meuId).orEmpty()
        val fotoOutro = conversa.fotoOutroAdmin(meuId)
        if (!fotoOutro.isNullOrEmpty()) {
            ivFotoOutro.load(fotoOutro) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        }

        lifecycleScope.launch { chatAdminCaronaRepository.marcarConversaComoLida(conversa) }
        escutarMensagens(conversa.id!!)
    }

    private fun escutarMensagens(conversaId: String) {
        val meuId = adminRepository.uidLogado() ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatAdminCaronaRepository.escutarMensagens(conversaId).catch { }.collect { mensagens ->
                    rvMensagens.adapter = MensagemChatAdminAdapter(mensagens, meuId) { mensagem ->
                        mostrarDialogApagar(mensagem, meuId)
                    }
                    if (mensagens.isNotEmpty()) {
                        rvMensagens.scrollToPosition(mensagens.size - 1)
                    }
                }
            }
        }
    }

    // "Apagar para todos" só aparece pra quem mandou a mensagem — mesma
    // regra do Match (ver MensagemChatAdminAdapter/firestore.rules).
    private fun mostrarDialogApagar(mensagem: MensagemChatAdmin, meuId: String) {
        val souRemetente = mensagem.remetenteId == meuId
        val opcoes = if (souRemetente) {
            arrayOf(getString(R.string.chat_admin_apagar_opcao_mim), getString(R.string.chat_admin_apagar_opcao_todos))
        } else {
            arrayOf(getString(R.string.chat_admin_apagar_opcao_mim))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_admin_apagar_titulo)
            .setItems(opcoes) { _, which ->
                lifecycleScope.launch {
                    val resultado = if (which == 1) {
                        chatAdminCaronaRepository.apagarMensagemParaTodos(mensagem)
                    } else {
                        chatAdminCaronaRepository.apagarMensagemParaMim(mensagem)
                    }
                    resultado.onFailure { e ->
                        Toast.makeText(this@ChatAdminCaronaActivity, getString(R.string.chat_admin_apagar_erro, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun enviarMensagem() {
        val conversa = conversaAtual ?: return
        val texto = etMensagem.text.toString().trim()
        if (texto.isEmpty()) return

        etMensagem.setText("")
        lifecycleScope.launch {
            chatAdminCaronaRepository.enviarMensagem(conversa, texto)
                .onFailure { e ->
                    Toast.makeText(this@ChatAdminCaronaActivity, getString(R.string.chat_admin_erro_enviar, e.message), Toast.LENGTH_SHORT).show()
                }
        }
    }

    companion object {
        const val EXTRA_CONVERSA = "conversaAdmin"
    }
}

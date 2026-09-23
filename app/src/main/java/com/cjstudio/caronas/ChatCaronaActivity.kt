package com.cjstudio.caronas

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
class ChatCaronaActivity : AppCompatActivity() {

    @Inject
    lateinit var chatCaronaRepository: IChatCaronaRepository

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var bloqueioRepository: IBloqueioRepository

    private lateinit var tvNomeOutro: TextView
    private lateinit var tvRota: TextView
    private lateinit var rvMensagens: RecyclerView
    private lateinit var etMensagem: EditText
    private lateinit var layoutEntradaMensagem: View
    private lateinit var tvAvisoChatFechado: TextView

    private var conversaAtual: ConversaCarona? = null
    // Estado atual do chat (ver ChatUtil) — vem do listener da solicitação.
    private var statusChat = StatusChat.ABERTO
    // Bloqueio em qualquer sentido entre os dois (ver Bloqueio.kt): fecha o
    // campo de mensagem como um chat encerrado, mesmo com a viagem ativa.
    private var bloqueado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_carona)
        findViewById<View>(R.id.rootChatCarona).ajustarPaddingParaTeclado()

        tvNomeOutro = findViewById(R.id.tvNomeOutroChat)
        tvRota = findViewById(R.id.tvRotaChat)
        rvMensagens = findViewById(R.id.rvMensagensChat)
        etMensagem = findViewById(R.id.etMensagemChat)
        layoutEntradaMensagem = findViewById(R.id.layoutEntradaMensagemChat)
        tvAvisoChatFechado = findViewById(R.id.tvAvisoChatFechado)
        val btnEnviar = findViewById<android.widget.Button>(R.id.btnEnviarChat)
        rvMensagens.layoutManager = LinearLayoutManager(this)
        btnEnviar.setOnClickListener { enviarMensagem() }
        findViewById<View>(R.id.tvSegurancaChat).setOnClickListener { abrirSeguranca() }

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
                    // Chat já fechado (cancelado/encerrado) e sem conversa
                    // anterior: nada pra mostrar, só avisa por quê.
                    val mensagem = if (e is ChatIndisponivelException) {
                        getString(mensagemDeChatFechado(e.status))
                    } else {
                        getString(R.string.chat_carona_erro_generico, e.message)
                    }
                    Toast.makeText(this@ChatCaronaActivity, mensagem, Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun abrirConversa(conversa: ConversaCarona) {
        conversaAtual = conversa
        val meuId = usuarioRepository.uidLogado()
        tvNomeOutro.text = conversa.nomeOutroUsuario(meuId) ?: getString(R.string.procurar_motorista_desconhecido)
        tvRota.text = getString(R.string.procurar_rota_formato, conversa.cidadeOrigem ?: "", conversa.cidadeDestino ?: "")

        lifecycleScope.launch { chatCaronaRepository.marcarConversaComoLida(conversa) }
        lifecycleScope.launch {
            val outroId = conversa.idOutroUsuario(meuId) ?: return@launch
            bloqueado = bloqueioRepository.idsComBloqueio().getOrDefault(emptySet()).contains(outroId)
            aplicarStatus(statusChat)
        }
        escutarMensagens(conversa.id!!)
        conversa.solicitacaoId?.let { escutarStatus(it) }
    }

    // Chat aberto: campo de mensagem normal. Cancelado/encerrado: as
    // mensagens continuam na tela, mas o campo some e entra o aviso do
    // motivo (ver ChatUtil). O estado muda ao vivo (cancelamento do outro
    // lado) e é recalculado ao enviar (o relógio passa com a tela aberta).
    private fun escutarStatus(solicitacaoId: String) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatCaronaRepository.escutarSolicitacao(solicitacaoId)
                    // Erro de leitura (ex.: sem permissão) = sem como provar
                    // que o chat está aberto: trata como encerrado.
                    .catch { emit(null) }
                    .collect { solicitacao -> aplicarStatus(ChatUtil.status(solicitacao)) }
            }
        }
    }

    private fun aplicarStatus(novo: StatusChat) {
        statusChat = novo
        val aberto = novo == StatusChat.ABERTO && !bloqueado
        layoutEntradaMensagem.visibility = if (aberto) View.VISIBLE else View.GONE
        tvAvisoChatFechado.visibility = if (aberto) View.GONE else View.VISIBLE
        if (bloqueado) {
            tvAvisoChatFechado.setText(R.string.chat_carona_bloqueado)
        } else if (!aberto) {
            tvAvisoChatFechado.setText(mensagemDeChatFechado(novo))
        }
    }

    private fun abrirSeguranca() {
        val conversa = conversaAtual ?: return
        val meuId = usuarioRepository.uidLogado()
        val outroId = conversa.idOutroUsuario(meuId) ?: return
        SegurancaUsuarioDialogUtil.mostrarOpcoes(
            this, outroId, conversa.nomeOutroUsuario(meuId), SegurancaUsuarioDialogUtil.ORIGEM_CHAT,
            usuarioRepository, bloqueioRepository
        ) {
            // Desbloquear só libera se o OUTRO também não me bloqueou —
            // confere de novo em vez de assumir.
            lifecycleScope.launch {
                bloqueado = bloqueioRepository.idsComBloqueio().getOrDefault(emptySet()).contains(outroId)
                aplicarStatus(statusChat)
            }
        }
    }

    private fun mensagemDeChatFechado(status: StatusChat): Int = when (status) {
        StatusChat.CANCELADO -> R.string.chat_carona_fechado_cancelado
        else -> R.string.chat_carona_fechado_encerrado
    }

    private fun escutarMensagens(conversaId: String) {
        val meuId = usuarioRepository.uidLogado() ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatCaronaRepository.escutarMensagens(conversaId).catch { }.collect { mensagens ->
                    rvMensagens.adapter = MensagemCaronaAdapter(mensagens, meuId)
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
        if (statusChat != StatusChat.ABERTO || bloqueado) return

        etMensagem.setText("")
        lifecycleScope.launch {
            chatCaronaRepository.enviarMensagem(conversa, texto)
                .onFailure { e ->
                    Toast.makeText(this@ChatCaronaActivity, getString(R.string.chat_carona_erro_enviar, e.message), Toast.LENGTH_SHORT).show()
                }
        }
    }

    companion object {
        const val EXTRA_SOLICITACAO = "solicitacao"
        const val EXTRA_CONVERSA = "conversa"
    }
}

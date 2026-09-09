package com.cjstudio.caronas

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.catch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TelaCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var caronaRepository: ICaronaRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    @Inject
    lateinit var chatCaronaRepository: IChatCaronaRepository

    @Inject
    lateinit var auth: com.google.firebase.auth.FirebaseAuth

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var tvPapelUsuario: TextView
    private lateinit var tvNomeUsuario: TextView
    private lateinit var btnMinhasOfertasOuViagens: TextView
    private lateinit var btnProcurar: TextView
    private lateinit var btnOferecer: TextView
    private lateinit var badgeChatNaoLidas: TextView
    private lateinit var btnSair: Button
    private lateinit var tvResumoBusca: TextView
    private lateinit var tvSemResultadosBusca: TextView
    private lateinit var rvResultadosBusca: RecyclerView
    private lateinit var progressBarBusca: ProgressBar
    private var usuarioAtual: Usuario? = null
    private var mostrandoMinhasViagens = false
    private var adapterResultadosBusca: CaronaResultadoAdapter? = null

    private val formatoDataBusca = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tela_caronas)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        tvPapelUsuario = findViewById(R.id.tvPapelUsuario)
        tvNomeUsuario = findViewById(R.id.tvNomeUsuario)
        btnMinhasOfertasOuViagens = findViewById(R.id.btnMinhasOfertasOuViagens)
        btnSair = findViewById(R.id.btnSair)
        tvResumoBusca = findViewById(R.id.tvResumoBusca)
        tvSemResultadosBusca = findViewById(R.id.tvSemResultadosBusca)
        rvResultadosBusca = findViewById(R.id.rvResultadosBusca)
        progressBarBusca = findViewById(R.id.progressBarBusca)
        rvResultadosBusca.layoutManager = LinearLayoutManager(this)

        val tvMeuPerfil = findViewById<TextView>(R.id.tvMeuPerfil)
        btnProcurar = findViewById(R.id.btnProcurar)
        btnOferecer = findViewById(R.id.btnOferecer)
        val btnChat = findViewById<TextView>(R.id.btnChat)
        badgeChatNaoLidas = findViewById(R.id.badgeChatNaoLidas)

        carregarPerfil()
        escutarBadgeChat()

        // "Meu Perfil" já abre a tela de edição (que reúne ver + editar +
        // excluir cadastro) — não tem mais link separado de "Editar Perfil".
        tvMeuPerfil.setOnClickListener {
            startActivity(Intent(this, EditarCadastroCaronasActivity::class.java))
        }

        btnProcurar.setOnClickListener { abrirDialogBuscarCarona() }
        btnOferecer.setOnClickListener { abrirOferecerCarona() }
        btnChat.setOnClickListener { startActivity(Intent(this, ConversasCaronaActivity::class.java)) }

        btnSair.setOnClickListener {
            lifecycleScope.launch {
                usuarioRepository.logout()
                startActivity(Intent(this@TelaCaronasActivity, LoginCaronasActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar da tela de Editar Perfil, pra refletir mudanças
        // (nome, foto, papel motorista/passageiro) sem precisar reabrir o app.
        carregarPerfil()
    }

    // Só quem está cadastrado como motorista (com veículo) pode oferecer
    // carona — passageiro sem carro não tem o que oferecer. Ver
    // CadastroCaronasActivity/EditarCadastroCaronasActivity pra virar
    // motorista.
    private fun abrirOferecerCarona() {
        val usuario = usuarioAtual
        if (usuario == null) {
            Toast.makeText(this, R.string.carregando, Toast.LENGTH_SHORT).show()
            return
        }
        if (!usuario.motorista) {
            Toast.makeText(this, R.string.tela_oferecer_precisa_ser_motorista, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, OferecerCaronaActivity::class.java))
    }

    // Diálogo com layout próprio (dialog_buscar_carona.xml, campos com
    // TextInputLayout) em vez dos botões padrão do AlertDialog — origem,
    // destino e data. Ao confirmar, dispara a busca e os resultados
    // aparecem na lista abaixo do quadro azul, nesta mesma tela.
    private fun abrirDialogBuscarCarona() {
        val view = layoutInflater.inflate(R.layout.dialog_buscar_carona, null)
        val etOrigem = view.findViewById<EditText>(R.id.etOrigemBusca)
        val etDestino = view.findViewById<EditText>(R.id.etDestinoBusca)
        val etData = view.findViewById<EditText>(R.id.etDataBusca)
        val btnBuscar = view.findViewById<Button>(R.id.btnBuscarDialog)

        val dataSelecionada = Calendar.getInstance()
        var dataEscolhida = false
        val abrirSeletorData = {
            DatePickerDialog(this, { _, ano, mes, dia ->
                dataSelecionada.set(Calendar.YEAR, ano)
                dataSelecionada.set(Calendar.MONTH, mes)
                dataSelecionada.set(Calendar.DAY_OF_MONTH, dia)
                dataEscolhida = true
                etData.setText(formatoDataBusca.format(dataSelecionada.time))
            }, dataSelecionada.get(Calendar.YEAR), dataSelecionada.get(Calendar.MONTH), dataSelecionada.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }
        etData.setOnClickListener { abrirSeletorData() }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        btnBuscar.setOnClickListener {
            val origem = etOrigem.text.toString().trim()
            val destino = etDestino.text.toString().trim()
            if (origem.isEmpty()) {
                Toast.makeText(this, R.string.procurar_erro_origem, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (destino.isEmpty()) {
                Toast.makeText(this, R.string.procurar_erro_destino, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!dataEscolhida) {
                Toast.makeText(this, R.string.procurar_erro_data, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            buscarCaronas(origem, destino, dataSelecionada)
        }

        dialog.show()
    }

    private fun buscarCaronas(origem: String, destino: String, data: Calendar) {
        mostrandoMinhasViagens = false
        progressBarBusca.visibility = View.VISIBLE

        lifecycleScope.launch {
            caronaRepository.buscarCaronas(origem, destino, data)
                .onSuccess { caronas ->
                    progressBarBusca.visibility = View.GONE
                    tvResumoBusca.visibility = View.VISIBLE
                    tvResumoBusca.text = getString(
                        R.string.procurar_resumo_formato,
                        caronas.size,
                        origem,
                        destino,
                        formatoDataBusca.format(data.time)
                    )

                    if (caronas.isEmpty()) {
                        rvResultadosBusca.visibility = View.GONE
                        tvSemResultadosBusca.text = getString(R.string.procurar_sem_resultados)
                        tvSemResultadosBusca.visibility = View.VISIBLE
                    } else {
                        tvSemResultadosBusca.visibility = View.GONE
                        rvResultadosBusca.visibility = View.VISIBLE
                        adapterResultadosBusca = CaronaResultadoAdapter(caronas) { carona -> solicitarVaga(carona) }
                        rvResultadosBusca.adapter = adapterResultadosBusca
                    }
                }
                .onFailure { e ->
                    progressBarBusca.visibility = View.GONE
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.procurar_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun solicitarVaga(carona: Carona) {
        val caronaId = carona.id
        // Marca otimista (antes da resposta do servidor) pra desabilitar o
        // botão na hora — evita um segundo toque durante a espera da rede
        // virar uma segunda solicitação.
        caronaId?.let { adapterResultadosBusca?.marcarComoSolicitada(it) }

        lifecycleScope.launch {
            solicitacaoRepository.solicitarVaga(carona)
                .onSuccess {
                    Toast.makeText(this@TelaCaronasActivity, R.string.procurar_solicitacao_enviada, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    caronaId?.let { adapterResultadosBusca?.desmarcarComoSolicitada(it) }
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.procurar_erro_solicitar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // "Minhas Viagens" (passageiro) mostra a lista no mesmo espaço abaixo
    // do quadro azul usado por "Procurar" — só aparece quando o botão é
    // tocado, e tocar de novo esconde (toggle). Cada card vem fechado, só
    // com rota/status/data; abrir mostra valor pago + chat/cancelar (ver
    // MinhaViagemAdapter, que também garante só um card aberto por vez).
    private fun toggleMinhasViagens() {
        if (mostrandoMinhasViagens) {
            mostrandoMinhasViagens = false
            progressBarBusca.visibility = View.GONE
            tvResumoBusca.visibility = View.GONE
            tvSemResultadosBusca.visibility = View.GONE
            rvResultadosBusca.visibility = View.GONE
            return
        }
        carregarMinhasViagens()
    }

    private fun carregarMinhasViagens() {
        mostrandoMinhasViagens = true
        progressBarBusca.visibility = View.VISIBLE
        tvResumoBusca.visibility = View.GONE
        tvSemResultadosBusca.visibility = View.GONE
        rvResultadosBusca.visibility = View.GONE

        lifecycleScope.launch {
            solicitacaoRepository.buscarMinhasSolicitacoes()
                .onSuccess { viagens ->
                    progressBarBusca.visibility = View.GONE
                    if (viagens.isEmpty()) {
                        tvSemResultadosBusca.visibility = View.VISIBLE
                        tvSemResultadosBusca.text = getString(R.string.minhas_viagens_vazio)
                    } else {
                        // Mais recente (pela data da viagem) sempre no topo —
                        // já vem nessa ordem de buscarMinhasSolicitacoes().
                        val totalPago = viagens.filter { it.status != "cancelada" }.sumOf { it.valorPago ?: 0.0 }
                        tvResumoBusca.visibility = View.VISIBLE
                        tvResumoBusca.text = getString(
                            R.string.minhas_viagens_total_pago_formato,
                            String.format(Locale("pt", "BR"), "%.2f", totalPago)
                        )
                        rvResultadosBusca.visibility = View.VISIBLE
                        rvResultadosBusca.adapter = MinhaViagemAdapter(
                            viagens,
                            onCancelarClick = { confirmarCancelarViagem(it) },
                            onChatClick = { abrirChatViagem(it) }
                        )
                    }
                }
                .onFailure { e ->
                    progressBarBusca.visibility = View.GONE
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_viagens_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmarCancelarViagem(viagem: Solicitacao) {
        val solicitacaoId = viagem.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.minhas_viagens_cancelar_titulo)
            .setMessage(R.string.minhas_viagens_cancelar_mensagem)
            .setPositiveButton(R.string.minhas_viagens_botao_cancelar) { _, _ ->
                lifecycleScope.launch {
                    solicitacaoRepository.cancelarSolicitacao(solicitacaoId)
                        .onSuccess {
                            Toast.makeText(this@TelaCaronasActivity, R.string.minhas_viagens_cancelada_sucesso, Toast.LENGTH_SHORT).show()
                            carregarMinhasViagens()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_viagens_erro_cancelar, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun abrirChatViagem(viagem: Solicitacao) {
        val intent = Intent(this, ChatCaronaActivity::class.java)
        intent.putExtra(ChatCaronaActivity.EXTRA_SOLICITACAO, viagem)
        startActivity(intent)
    }

    // Soma as não lidas de todas as conversas do usuário (como motorista
    // ou passageiro) pra mostrar no badge do botão Chat — mesmo espírito
    // do badgeMensagensNaoLidas do Match.
    private fun escutarBadgeChat() {
        val meuId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // .catch evita que um erro do listener (ex.: permissão
                // reavaliada no meio de um logout, rede instável) derrube o
                // app — sem isso, uma falha aqui subia sem tratamento e
                // travava a Activity.
                chatCaronaRepository.escutarMinhasConversas().catch { }.collect { conversas ->
                    val total = conversas.sumOf { it.naoLidasParaMim(meuId) }
                    if (total > 0) {
                        badgeChatNaoLidas.visibility = View.VISIBLE
                        badgeChatNaoLidas.text = total.toString()
                    } else {
                        badgeChatNaoLidas.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun carregarPerfil() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                usuarioAtual = usuario
                tvPapelUsuario.text = getString(
                    if (usuario.motorista) R.string.tela_papel_motorista else R.string.tela_papel_passageiro
                )
                tvNomeUsuario.text = usuario.nomeCompleto?.trim()?.substringBefore(" ") ?: ""

                // Mesmo botão, texto e destino diferentes conforme o papel
                // atual — motorista vê as ofertas que publicou, passageiro
                // vê as viagens que solicitou.
                if (usuario.motorista) {
                    btnMinhasOfertasOuViagens.text = getString(R.string.tela_botao_minhas_ofertas)
                    btnMinhasOfertasOuViagens.setOnClickListener {
                        startActivity(Intent(this@TelaCaronasActivity, MinhasOfertasActivity::class.java))
                    }
                } else {
                    btnMinhasOfertasOuViagens.text = getString(R.string.tela_botao_minhas_viagens)
                    btnMinhasOfertasOuViagens.setOnClickListener { toggleMinhasViagens() }
                }

                // Motorista oferece, passageiro procura — não faz sentido
                // mostrar os dois pro mesmo papel.
                btnOferecer.visibility = if (usuario.motorista) View.VISIBLE else View.GONE
                btnProcurar.visibility = if (usuario.motorista) View.GONE else View.VISIBLE
                if (!usuario.fotoUrl.isNullOrEmpty()) {
                    ivFotoPerfil.load(usuario.fotoUrl) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_person_default)
                        error(R.drawable.ic_person_default)
                    }
                }
            }.onFailure {
                // Sessão inválida/expirada — volta pro login.
                startActivity(Intent(this@TelaCaronasActivity, LoginCaronasActivity::class.java))
                finish()
            }
        }
    }
}

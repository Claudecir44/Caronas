package com.cjstudio.caronas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Dashboard do admin — mostra a foto/nome de quem está logado (mesma ideia
// do AdministracaoActivity.carregarFotoAdminLogado do Match), lendo o
// próprio documento admins/{uid} (ver Admin.kt/AdminRepository), e dá
// acesso às seções de gestão (Motoristas/Passageiros/Viagens Motorista/
// Viagens Passageiro) — todas carregadas NA MESMA TELA, logo abaixo do box
// azul (não abrem uma Activity nova, mesmo espírito da lista de resultados
// da busca na tela do usuário). "Editar Perfil" continua abrindo
// BuscarAdminActivity (busca por CPF) à parte, já que edita outra
// identidade (não é uma lista de registros).
@AndroidEntryPoint
class AdministracaoCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var ivFoto: ImageView
    private lateinit var tvNome: TextView
    private lateinit var tvTituloSecao: TextView
    private lateinit var progressBarSecao: ProgressBar
    private lateinit var tvVazioSecao: TextView
    private lateinit var rvSecao: RecyclerView
    private lateinit var containerBuscaMensagens: LinearLayout
    private lateinit var etBuscaMensagens: EditText
    private lateinit var btnArquivadosManifestacoes: Button
    private lateinit var badgeManifestacoes: TextView
    private lateinit var tvContadorSecao: TextView
    private var mostrandoArquivadas = false
    private var badgeManifestacoesIniciado = false

    // Mesmo padrão de TelaCaronasActivity — pede a permissão de notificação
    // em runtime (Android 13+) pra o push de notificarNovaManifestacao
    // conseguir de fato aparecer na tela do admin, não só chegar no app.
    private val lancadorPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_administracao_caronas)

        ivFoto = findViewById(R.id.ivFotoAdminLogado)
        tvNome = findViewById(R.id.tvAdminNome)
        tvTituloSecao = findViewById(R.id.tvTituloSecaoAdmin)
        tvContadorSecao = findViewById(R.id.tvContadorSecaoAdmin)
        progressBarSecao = findViewById(R.id.progressBarSecaoAdmin)
        tvVazioSecao = findViewById(R.id.tvVazioSecaoAdmin)
        rvSecao = findViewById(R.id.rvSecaoAdmin)
        rvSecao.layoutManager = LinearLayoutManager(this)
        containerBuscaMensagens = findViewById(R.id.containerBuscaMensagens)
        etBuscaMensagens = findViewById(R.id.etBuscaMensagens)
        btnArquivadosManifestacoes = findViewById(R.id.btnArquivadosManifestacoes)
        badgeManifestacoes = findViewById(R.id.badgeManifestacoes)

        findViewById<TextView>(R.id.tvEditarPerfilAdmin).setOnClickListener {
            startActivity(Intent(this, BuscarAdminActivity::class.java))
        }
        findViewById<TextView>(R.id.btnMotoristas).setOnClickListener { mostrarMotoristas() }
        findViewById<TextView>(R.id.btnPassageiros).setOnClickListener { mostrarPassageiros() }
        findViewById<TextView>(R.id.btnViagens).setOnClickListener { abrirDialogEscolherViagens() }
        findViewById<TextView>(R.id.btnFinanceiro).setOnClickListener {
            startActivity(Intent(this, FinanceiroCaronasActivity::class.java))
        }
        findViewById<TextView>(R.id.btnMensagens).setOnClickListener { abrirBuscaMensagens() }
        findViewById<TextView>(R.id.btnManifestacoes).setOnClickListener {
            mostrandoArquivadas = false
            mostrarManifestacoes()
        }
        btnArquivadosManifestacoes.setOnClickListener {
            mostrandoArquivadas = !mostrandoArquivadas
            if (mostrandoArquivadas) mostrarManifestacoesArquivadas() else mostrarManifestacoes()
        }
        findViewById<Button>(R.id.btnBuscarMensagens).setOnClickListener { buscarMensagens() }
        etBuscaMensagens.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarMensagens()
                true
            } else {
                false
            }
        }
        findViewById<Button>(R.id.btnSair).setOnClickListener {
            lifecycleScope.launch {
                usuarioRepository.logout()
                startActivity(Intent(this@AdministracaoCaronasActivity, LoginAdminCaronasActivity::class.java))
                finish()
            }
        }
        solicitarPermissaoNotificacaoSeNecessario()
    }

    private fun solicitarPermissaoNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val jaConcedida = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!jaConcedida) {
            lancadorPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarrega sempre (não só no onCreate) — cobre a volta da tela de
        // Editar Perfil, caso o admin tenha editado o PRÓPRIO cadastro.
        carregarAdminLogado(ivFoto, tvNome)
        if (!badgeManifestacoesIniciado) {
            badgeManifestacoesIniciado = true
            escutarBadgeManifestacoes()
        }
    }

    private fun carregarAdminLogado(ivFoto: ImageView, tvNome: TextView) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FcmTokenUtil.atualizarTokenAdmin(uid)
        lifecycleScope.launch {
            adminRepository.buscarAdminLogado(uid).onSuccess { admin ->
                tvNome.text = admin.nome?.ifEmpty { null } ?: admin.email ?: ""
                if (!admin.fotoUrl.isNullOrEmpty()) {
                    ivFoto.load(admin.fotoUrl) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_person_default)
                        error(R.drawable.ic_person_default)
                    }
                }
            }
        }
    }

    // Badge no botão "Sugestões" — total de reclamações/sugestões/denúncias
    // pendentes (não respondidas, não arquivadas), em tempo real. Mesmo
    // padrão de TelaCaronasActivity.escutarBadgeMinhasOfertasOuViagens.
    private fun escutarBadgeManifestacoes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adminRepository.escutarContagemManifestacoesPendentes().catch { }.collect { total ->
                    if (total > 0) {
                        badgeManifestacoes.visibility = View.VISIBLE
                        badgeManifestacoes.text = total.toString()
                    } else {
                        badgeManifestacoes.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun mostrarMotoristas() {
        prepararSecao(getString(R.string.admin_lista_motoristas_titulo), getString(R.string.admin_lista_motoristas_vazio))
        lifecycleScope.launch {
            adminRepository.listarTodosUsuarios()
                .onSuccess { usuarios ->
                    val motoristas = usuarios.filter { it.veiculo?.estaPreenchido() == true }
                    exibirContadorSecao(motoristas.size)
                    exibirResultadoSecao(motoristas) { lista ->
                        UsuarioAdminAdapter(
                            lista,
                            onClick = { usuario -> abrirDetalhesUsuario(usuario) },
                            onLongClick = { usuario -> confirmarRemoverUsuario(usuario) { mostrarMotoristas() } }
                        )
                    }
                }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    private fun mostrarPassageiros() {
        prepararSecao(getString(R.string.admin_lista_passageiros_titulo), getString(R.string.admin_lista_passageiros_vazio))
        lifecycleScope.launch {
            adminRepository.listarTodosUsuarios()
                .onSuccess { usuarios ->
                    exibirContadorSecao(usuarios.count { it.veiculo?.estaPreenchido() != true })
                    exibirResultadoSecao(usuarios.filter { it.veiculo?.estaPreenchido() != true }) { lista ->
                        UsuarioAdminAdapter(
                            lista,
                            onClick = { usuario -> abrirDetalhesUsuario(usuario) },
                            onLongClick = { usuario -> confirmarRemoverUsuario(usuario) { mostrarPassageiros() } }
                        )
                    }
                }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    // Toque e segure num motorista ou passageiro — remove o cadastro dele
    // por completo (ver IAdminRepository.excluirUsuario), com a mesma
    // trava de senha do administrador master das outras ações destrutivas
    // do painel. aoRemover recarrega a lista certa (Motoristas ou
    // Passageiros, dependendo de onde veio o toque).
    // Toque simples (não toque-e-segure, que continua excluindo) num
    // motorista ou passageiro — abre o cadastro completo, com opção de
    // editar/salvar (ver DetalhesUsuarioAdminActivity).
    private fun abrirDetalhesUsuario(usuario: Usuario) {
        val uid = usuario.id ?: return
        startActivity(Intent(this, DetalhesUsuarioAdminActivity::class.java).putExtra(DetalhesUsuarioAdminActivity.EXTRA_UID, uid))
    }

    private fun confirmarRemoverUsuario(usuario: Usuario, aoRemover: () -> Unit) {
        val uid = usuario.id ?: return
        val nome = usuario.nomeCompleto?.ifBlank { null } ?: usuario.email ?: "esse usuário"
        ConfirmarSenhaMasterDialogUtil.mostrar(
            this,
            getString(R.string.admin_remover_usuario_titulo),
            getString(R.string.admin_remover_usuario_mensagem, nome)
        ) { senhaMaster ->
            lifecycleScope.launch {
                adminRepository.excluirUsuario(uid, senhaMaster)
                    .onSuccess {
                        Toast.makeText(this@AdministracaoCaronasActivity, R.string.admin_remover_usuario_sucesso, Toast.LENGTH_LONG).show()
                        // aoRemover() já rechama mostrarMotoristas()/mostrarPassageiros(),
                        // que recalcula o contador sozinho (ver exibirContadorSecao).
                        aoRemover()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_remover_usuario_erro, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    // Um só botão "Viagens" — ao tocar, pergunta motoristas ou passageiros
    // num cartão compacto (2 opções lado a lado, com ícone) em vez da lista
    // padrão do AlertDialog, e mostra a lista escolhida na mesma seção
    // abaixo (mostrarViagensMotorista/mostrarViagensPassageiro já existiam
    // antes como dois botões separados; aqui só muda COMO se chega em cada
    // uma, não o que elas fazem).
    private fun abrirDialogEscolherViagens() {
        val view = layoutInflater.inflate(R.layout.dialog_escolher_viagens, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()
        // Fundo transparente na própria janela do diálogo — quem desenha o
        // cartão branco arredondado é o CardView-like padrão do tema
        // Material; sem isso sobrava uma moldura retangular por trás do
        // conteúdo já arredondado do layout.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<View>(R.id.opcaoViagensMotoristas).setOnClickListener {
            dialog.dismiss()
            mostrarViagensMotorista()
        }
        view.findViewById<View>(R.id.opcaoViagensPassageiros).setOnClickListener {
            dialog.dismiss()
            mostrarViagensPassageiro()
        }
        view.findViewById<View>(R.id.btnCancelarEscolherViagens).setOnClickListener { dialog.dismiss() }

        dialog.show()
        // Largura FIXA e pequena (300dp) em vez do padrão quase-cheio da
        // tela — é o "tamanho menor" pedido pro diálogo. WRAP_CONTENT
        // sozinho não bastava: o tema Material do AlertDialog impõe uma
        // largura mínima própria (windowMinWidthMajor/Minor) que ignora o
        // wrap_content do layout, então precisa forçar em pixels.
        val larguraDp = 260
        val larguraPx = (larguraDp * resources.displayMetrics.density).toInt()
        dialog.window?.setLayout(larguraPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun mostrarViagensMotorista() {
        prepararSecao(getString(R.string.admin_lista_viagens_titulo), getString(R.string.admin_lista_viagens_vazio))
        lifecycleScope.launch {
            adminRepository.listarTodasCaronas()
                .onSuccess { caronas -> exibirResultadoSecao(caronas) { lista -> ViagemAdminAdapter(lista) } }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    private fun mostrarViagensPassageiro() {
        prepararSecao(getString(R.string.admin_lista_viagens_passageiro_titulo), getString(R.string.admin_lista_viagens_passageiro_vazio))
        lifecycleScope.launch {
            adminRepository.listarTodasSolicitacoes()
                .onSuccess { solicitacoes -> exibirResultadoSecao(solicitacoes) { lista -> ViagemPassageiroAdminAdapter(lista) } }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    // Só a seção "Mensagens" usa a caixa de busca — as outras (Motoristas/
    // Passageiros/Viagens/Sugestões) escondem ela de novo ao trocar de
    // seção. "mostrarArquivados" só liga pra seção "Sugestões" (ver
    // mostrarManifestacoes/mostrarManifestacoesArquivadas).
    private fun prepararSecao(titulo: String, textoVazio: String, mostrarBusca: Boolean = false, mostrarArquivados: Boolean = false) {
        containerBuscaMensagens.visibility = if (mostrarBusca) View.VISIBLE else View.GONE
        btnArquivadosManifestacoes.visibility = if (mostrarArquivados) View.VISIBLE else View.GONE
        tvTituloSecao.text = titulo
        tvTituloSecao.visibility = if (titulo.isEmpty()) View.GONE else View.VISIBLE
        // Só Motoristas/Passageiros preenchem isso (ver exibirContadorSecao) —
        // some de novo ao trocar pra qualquer outra seção.
        tvContadorSecao.visibility = View.GONE
        tvVazioSecao.text = textoVazio
        tvVazioSecao.visibility = View.GONE
        rvSecao.visibility = View.GONE
        progressBarSecao.visibility = View.VISIBLE
    }

    // Total de motoristas/passageiros, ao lado do título da seção (não mais
    // em cima dos botões — pedido explícito do usuário).
    private fun exibirContadorSecao(total: Int) {
        tvContadorSecao.text = total.toString()
        tvContadorSecao.visibility = View.VISIBLE
    }

    // Toque em "Mensagens" — só mostra a caixa de busca, ainda não busca
    // nada (precisa do admin digitar e tocar em Buscar/Enter primeiro).
    private fun abrirBuscaMensagens() {
        containerBuscaMensagens.visibility = View.VISIBLE
        btnArquivadosManifestacoes.visibility = View.GONE
        tvTituloSecao.visibility = View.GONE
        tvVazioSecao.visibility = View.GONE
        rvSecao.visibility = View.GONE
        progressBarSecao.visibility = View.GONE
    }

    private fun mostrarManifestacoes() {
        btnArquivadosManifestacoes.text = getString(R.string.admin_manifestacoes_botao_arquivados)
        prepararSecao(
            getString(R.string.admin_manifestacoes_titulo),
            getString(R.string.admin_manifestacoes_vazio),
            mostrarArquivados = true
        )
        lifecycleScope.launch {
            adminRepository.listarManifestacoes()
                .onSuccess { itens -> exibirResultadoSecao(itens) { lista -> criarAdapterManifestacoes(lista, mostrarBotoes = true) } }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    private fun mostrarManifestacoesArquivadas() {
        btnArquivadosManifestacoes.text = getString(R.string.admin_manifestacoes_botao_voltar)
        prepararSecao(
            getString(R.string.admin_manifestacoes_arquivadas_titulo),
            getString(R.string.admin_manifestacoes_arquivadas_vazio),
            mostrarArquivados = true
        )
        lifecycleScope.launch {
            adminRepository.listarManifestacoesArquivadas()
                .onSuccess { itens -> exibirResultadoSecao(itens) { lista -> criarAdapterManifestacoes(lista, mostrarBotoes = false) } }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    private fun criarAdapterManifestacoes(itens: List<Manifestacao>, mostrarBotoes: Boolean): ManifestacaoAdminAdapter {
        return ManifestacaoAdminAdapter(
            itens,
            mostrarBotoes = mostrarBotoes,
            onResponderClick = { item -> abrirDialogResponderManifestacao(item) },
            onArquivarClick = { item -> confirmarArquivarManifestacao(item) },
            onExcluirClick = { item -> confirmarExcluirManifestacao(item) }
        )
    }

    private fun abrirDialogResponderManifestacao(item: Manifestacao) {
        val id = item.id ?: return
        val view = layoutInflater.inflate(R.layout.dialog_responder_manifestacao, null)
        view.findViewById<TextView>(R.id.tvMensagemOriginalResponder).text = item.mensagem
        val etResposta = view.findViewById<EditText>(R.id.etRespostaManifestacao)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.admin_manifestacoes_responder_titulo)
            .setView(view)
            .setPositiveButton(R.string.admin_manifestacoes_botao_responder) { _, _ ->
                val resposta = etResposta.text.toString().trim()
                if (resposta.isEmpty()) {
                    Toast.makeText(this, R.string.admin_manifestacoes_responder_erro_vazio, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    adminRepository.responderManifestacao(id, resposta)
                        .onSuccess {
                            Toast.makeText(this@AdministracaoCaronasActivity, R.string.admin_manifestacoes_responder_sucesso, Toast.LENGTH_LONG).show()
                            mostrarManifestacoes()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_manifestacoes_responder_erro, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // Só arquiva depois de respondida — ver Manifestacao.STATUS_RESPONDIDO
    // (mesma trava reforçada em AdminRepository.arquivarManifestacao e em
    // firestore.rules).
    private fun confirmarArquivarManifestacao(item: Manifestacao) {
        val id = item.id ?: return
        if (item.status != Manifestacao.STATUS_RESPONDIDO) {
            Toast.makeText(this, R.string.admin_manifestacoes_arquivar_erro_nao_respondida, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.admin_manifestacoes_arquivar_titulo)
            .setMessage(R.string.admin_manifestacoes_arquivar_mensagem)
            .setPositiveButton(R.string.admin_manifestacoes_botao_arquivar) { _, _ ->
                lifecycleScope.launch {
                    adminRepository.arquivarManifestacao(id)
                        .onSuccess {
                            Toast.makeText(this@AdministracaoCaronasActivity, R.string.admin_manifestacoes_arquivar_sucesso, Toast.LENGTH_SHORT).show()
                            mostrarManifestacoes()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_manifestacoes_arquivar_erro, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun confirmarExcluirManifestacao(item: Manifestacao) {
        val id = item.id ?: return
        ConfirmarSenhaMasterDialogUtil.mostrar(
            this,
            getString(R.string.admin_manifestacoes_excluir_titulo),
            getString(R.string.admin_manifestacoes_excluir_mensagem)
        ) { senhaMaster ->
            lifecycleScope.launch {
                adminRepository.excluirManifestacao(id, senhaMaster)
                    .onSuccess {
                        Toast.makeText(this@AdministracaoCaronasActivity, R.string.admin_manifestacoes_excluir_sucesso, Toast.LENGTH_SHORT).show()
                        if (mostrandoArquivadas) mostrarManifestacoesArquivadas() else mostrarManifestacoes()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_manifestacoes_excluir_erro, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun buscarMensagens() {
        val termo = etBuscaMensagens.text.toString().trim()
        if (termo.isEmpty()) {
            Toast.makeText(this, R.string.admin_mensagens_busca_hint, Toast.LENGTH_SHORT).show()
            return
        }
        prepararSecao("", "", mostrarBusca = true)
        lifecycleScope.launch {
            adminRepository.buscarUsuariosPorTexto(termo)
                .onSuccess { usuarios ->
                    when {
                        usuarios.isEmpty() -> {
                            progressBarSecao.visibility = View.GONE
                            tvVazioSecao.text = getString(R.string.admin_mensagens_usuario_nao_encontrado)
                            tvVazioSecao.visibility = View.VISIBLE
                        }
                        usuarios.size == 1 -> carregarMensagensDoUsuario(usuarios.first())
                        else -> {
                            progressBarSecao.visibility = View.GONE
                            tvTituloSecao.text = getString(R.string.admin_mensagens_selecionar_titulo)
                            tvTituloSecao.visibility = View.VISIBLE
                            rvSecao.visibility = View.VISIBLE
                            rvSecao.adapter = UsuarioAdminAdapter(usuarios) { usuario -> carregarMensagensDoUsuario(usuario) }
                        }
                    }
                }
                .onFailure { e ->
                    progressBarSecao.visibility = View.GONE
                    Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_mensagens_erro_buscar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun carregarMensagensDoUsuario(usuario: Usuario) {
        val usuarioId = usuario.id ?: return
        containerBuscaMensagens.visibility = View.VISIBLE
        tvTituloSecao.visibility = View.GONE
        tvVazioSecao.visibility = View.GONE
        rvSecao.visibility = View.GONE
        progressBarSecao.visibility = View.VISIBLE

        val nomeExibido = usuario.nomeCompleto?.ifBlank { null } ?: usuario.email ?: usuario.telefone ?: "?"
        lifecycleScope.launch {
            adminRepository.listarMensagensDoUsuario(usuarioId)
                .onSuccess { mensagens ->
                    progressBarSecao.visibility = View.GONE
                    tvTituloSecao.text = getString(R.string.admin_mensagens_titulo_formato, nomeExibido)
                    tvTituloSecao.visibility = View.VISIBLE
                    if (mensagens.isEmpty()) {
                        tvVazioSecao.text = getString(R.string.admin_mensagens_vazio)
                        tvVazioSecao.visibility = View.VISIBLE
                    } else {
                        rvSecao.visibility = View.VISIBLE
                        rvSecao.adapter = MensagemAdminAdapter(mensagens)
                    }
                }
                .onFailure { e ->
                    progressBarSecao.visibility = View.GONE
                    Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_mensagens_erro_buscar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun <T> exibirResultadoSecao(itens: List<T>, criarAdapter: (List<T>) -> RecyclerView.Adapter<*>) {
        progressBarSecao.visibility = View.GONE
        if (itens.isEmpty()) {
            tvVazioSecao.visibility = View.VISIBLE
        } else {
            rvSecao.visibility = View.VISIBLE
            rvSecao.adapter = criarAdapter(itens)
        }
    }

    private fun mostrarErroSecao(e: Throwable) {
        progressBarSecao.visibility = View.GONE
        Toast.makeText(this, getString(R.string.admin_erro_carregar, e.message), Toast.LENGTH_LONG).show()
    }
}

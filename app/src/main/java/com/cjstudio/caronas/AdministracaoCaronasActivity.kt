package com.cjstudio.caronas

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
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
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    @Inject
    lateinit var falhasRepository: IFalhasRepository

    private lateinit var ivFoto: ImageView
    private lateinit var tvNome: TextView
    private lateinit var tvTituloSecao: TextView
    private lateinit var progressBarSecao: ProgressBar
    private lateinit var tvVazioSecao: TextView
    private lateinit var rvSecao: RecyclerView
    private lateinit var containerBuscaMensagens: LinearLayout
    private lateinit var etBuscaMensagens: EditText
    private lateinit var btnArquivadosManifestacoes: Button
    // ===== Seção "Mensagens" — filtro de período + relatório (ver
    // carregarMensagensDoUsuario/aplicarFiltroMensagens) =====
    private lateinit var containerFiltroMensagens: LinearLayout
    private lateinit var spinnerPeriodoMensagens: Spinner
    private lateinit var btnFiltrarMensagens: Button
    private lateinit var layoutDataPersonalizadaMensagens: LinearLayout
    private lateinit var etDataInicialMensagens: EditText
    private lateinit var etDataFinalMensagens: EditText
    private lateinit var btnGerarRelatorioMensagens: Button
    private var mensagensUsuarioCache: List<MensagemAdminInfo> = emptyList()
    private var nomeUsuarioMensagensCache: String = ""
    private lateinit var badgeManifestacoes: TextView
    private lateinit var tvContadorSecao: TextView
    private var mostrandoArquivadas = false

    // ===== Seção "Financeiro" (ver mostrarFinanceiro) =====
    private lateinit var containerFiltroFinanceiro: LinearLayout
    private lateinit var spinnerPeriodoFinanceiro: Spinner
    private lateinit var btnFiltrarFinanceiro: Button
    private lateinit var layoutDataPersonalizadaFinanceiro: LinearLayout
    private lateinit var etDataInicialFinanceiro: EditText
    private lateinit var etDataFinalFinanceiro: EditText
    private lateinit var tvTotalPagamentosFinanceiro: TextView
    private lateinit var tvMotoristasUnicosFinanceiro: TextView
    private lateinit var tvTotalArrecadadoFinanceiro: TextView
    private lateinit var btnGerarRelatorioFinanceiro: Button
    private var pagamentosMotoristaCache: List<PagamentoMotorista> = emptyList()
    private val sdfFinanceiro = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val sdfFinanceiroCompleto = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

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
        escutarBadgeManifestacoes()
        containerFiltroMensagens = findViewById(R.id.containerFiltroMensagens)
        spinnerPeriodoMensagens = findViewById(R.id.spinnerPeriodoMensagens)
        btnFiltrarMensagens = findViewById(R.id.btnFiltrarMensagens)
        layoutDataPersonalizadaMensagens = findViewById(R.id.layoutDataPersonalizadaMensagens)
        etDataInicialMensagens = findViewById(R.id.etDataInicialMensagens)
        etDataFinalMensagens = findViewById(R.id.etDataFinalMensagens)
        btnGerarRelatorioMensagens = findViewById(R.id.btnGerarRelatorioMensagens)
        configurarFiltroMensagens()
        containerFiltroFinanceiro = findViewById(R.id.containerFiltroFinanceiro)
        spinnerPeriodoFinanceiro = findViewById(R.id.spinnerPeriodoFinanceiro)
        btnFiltrarFinanceiro = findViewById(R.id.btnFiltrarFinanceiro)
        layoutDataPersonalizadaFinanceiro = findViewById(R.id.layoutDataPersonalizadaFinanceiro)
        etDataInicialFinanceiro = findViewById(R.id.etDataInicialFinanceiro)
        etDataFinalFinanceiro = findViewById(R.id.etDataFinalFinanceiro)
        tvTotalPagamentosFinanceiro = findViewById(R.id.tvTotalPagamentosFinanceiro)
        tvMotoristasUnicosFinanceiro = findViewById(R.id.tvMotoristasUnicosFinanceiro)
        tvTotalArrecadadoFinanceiro = findViewById(R.id.tvTotalArrecadadoFinanceiro)
        btnGerarRelatorioFinanceiro = findViewById(R.id.btnGerarRelatorioFinanceiro)
        configurarFiltroFinanceiro()

        findViewById<TextView>(R.id.tvEditarPerfilAdmin).setOnClickListener {
            startActivity(Intent(this, BuscarAdminActivity::class.java))
        }
        findViewById<TextView>(R.id.btnMotoristas).setOnClickListener { mostrarMotoristas() }
        findViewById<TextView>(R.id.btnPassageiros).setOnClickListener { mostrarPassageiros() }
        findViewById<TextView>(R.id.btnViagens).setOnClickListener { abrirDialogEscolherViagens() }
        findViewById<TextView>(R.id.btnFinanceiro).setOnClickListener { mostrarFinanceiro() }
        findViewById<TextView>(R.id.tvConfiguracoesAdmin).setOnClickListener {
            startActivity(
                Intent(this, ConfiguracoesCaronasActivity::class.java)
                    .putExtra(ConfiguracoesCaronasActivity.EXTRA_MODO_ADMIN, true)
            )
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

        // Entrada vinda do card "Ver Mensagens" em Configurações (ver
        // ConfiguracoesCaronasActivity) — reaproveita a MESMA seção inline
        // de sempre, só abre ela automaticamente em vez do admin precisar
        // tocar em "Mensagens" de novo.
        if (intent.getStringExtra(EXTRA_ABRIR_SECAO) == SECAO_MENSAGENS) {
            abrirBuscaMensagens()
        }
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
    }

    private fun carregarAdminLogado(ivFoto: ImageView, tvNome: TextView) {
        val uid = adminRepository.uidLogado() ?: return
        notificacaoRepository.atualizarTokenAdmin(uid)
        falhasRepository.definirUsuario(uid)
        lifecycleScope.launch {
            adminRepository.buscarAdminLogado(uid).onSuccess { admin ->
                tvNome.text = admin.nome?.ifEmpty { null } ?: admin.email ?: ""
                findViewById<TextView>(R.id.tvAdminPapel).visibility =
                    if (admin.ehColaborador) View.VISIBLE else View.GONE
                if (!admin.fotoUrl.isNullOrEmpty()) {
                    ivFoto.load(admin.fotoUrl) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_person_default)
                        error(R.drawable.ic_person_default)
                    }
                }
                aplicarPermissoes(admin)
            }
        }
    }

    // Esconde os botões do painel cuja permissão o admin logado não tem
    // (admin "legado", sem permissoes definidas, continua vendo tudo — ver
    // Admin.temPermissao). Motoristas/Passageiros compartilham a permissão
    // "usuarios" (mesma coleção por baixo).
    private fun aplicarPermissoes(admin: Admin) {
        findViewById<View>(R.id.btnMotoristas).visibility = if (admin.temPermissao("usuarios")) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnPassageiros).visibility = if (admin.temPermissao("usuarios")) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnViagens).visibility = if (admin.temPermissao("viagens")) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnFinanceiro).visibility = if (admin.temPermissao("financeiro")) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnMensagens).visibility = if (admin.temPermissao("mensagens")) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnManifestacoes).visibility = if (admin.temPermissao("manifestacoes")) View.VISIBLE else View.GONE
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
                    // Viagens realizadas + total recebido de cada motorista —
                    // sempre recalculado nesta busca (não em tempo real), então
                    // atualiza a cada corrida nova toda vez que o admin abre
                    // essa lista (ver UsuarioAdminAdapter).
                    val solicitacoes = adminRepository.listarTodasSolicitacoes().getOrDefault(emptyList())
                    val (viagensPorMotorista, recebidoPorMotorista) = calcularEstatisticasMotorista(solicitacoes)
                    exibirResultadoSecao(motoristas) { lista ->
                        UsuarioAdminAdapter(
                            lista,
                            onClick = { usuario -> abrirDetalhesUsuario(usuario) },
                            onLongClick = { usuario -> confirmarRemoverUsuario(usuario) { mostrarMotoristas() } },
                            mostrarEstatisticas = true,
                            viagensPorUsuario = viagensPorMotorista,
                            valorPorUsuario = recebidoPorMotorista
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
                    // Todo cadastro é passageiro; quem também marcou "sou
                    // motorista" (veículo preenchido) continua aqui E entra na
                    // lista de Motoristas. Desmarcar apaga o veículo, então a
                    // pessoa sai de Motoristas na próxima vez que a lista abrir.
                    val passageiros = usuarios
                    exibirContadorSecao(passageiros.size)
                    // Viagens realizadas + total pago de cada passageiro (ver
                    // comentário equivalente em mostrarMotoristas acima).
                    val solicitacoes = adminRepository.listarTodasSolicitacoes().getOrDefault(emptyList())
                    val (viagensPorPassageiro, pagoPorPassageiro) = calcularEstatisticasPassageiro(solicitacoes)
                    exibirResultadoSecao(passageiros) { lista ->
                        UsuarioAdminAdapter(
                            lista,
                            onClick = { usuario -> abrirDetalhesUsuario(usuario) },
                            onLongClick = { usuario -> confirmarRemoverUsuario(usuario) { mostrarPassageiros() } },
                            mostrarVeiculo = false,
                            mostrarEstatisticas = true,
                            viagensPorUsuario = viagensPorPassageiro,
                            valorPorUsuario = pagoPorPassageiro
                        )
                    }
                }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    // "Recebido" só conta solicitações CONFIRMADAS (mesma regra do "total
    // recebido por oferta" em TelaCaronasActivity/MinhaOfertaAdapter — uma
    // solicitação ainda "solicitada" não é dinheiro que o motorista já tem
    // garantido). "Viagens" exige confirmada E já concluída (StatusViagemUtil,
    // mesma tolerância de 10min usada em todo o resto do app).
    private fun calcularEstatisticasMotorista(solicitacoes: List<Solicitacao>): Pair<Map<String, Int>, Map<String, Double>> {
        val viagens = HashMap<String, Int>()
        val recebido = HashMap<String, Double>()
        for (s in solicitacoes) {
            val motoristaId = s.motoristaId ?: continue
            if (s.status != "confirmada") continue
            recebido[motoristaId] = (recebido[motoristaId] ?: 0.0) + (s.valorPago ?: 0.0)
            if (StatusViagemUtil.jaConcluida(s.dataHoraPartida)) {
                viagens[motoristaId] = (viagens[motoristaId] ?: 0) + 1
            }
        }
        return viagens to recebido
    }

    // "Pago" conta tudo que não foi cancelado (mesma regra do resumo
    // "Minhas Viagens" em TelaCaronasActivity — inclui pedidos ainda
    // "solicitada", aguardando o motorista confirmar).
    private fun calcularEstatisticasPassageiro(solicitacoes: List<Solicitacao>): Pair<Map<String, Int>, Map<String, Double>> {
        val viagens = HashMap<String, Int>()
        val pago = HashMap<String, Double>()
        for (s in solicitacoes) {
            val passageiroId = s.passageiroId ?: continue
            if (s.status != "cancelada") {
                pago[passageiroId] = (pago[passageiroId] ?: 0.0) + (s.valorPago ?: 0.0)
            }
            if (s.status == "confirmada" && StatusViagemUtil.jaConcluida(s.dataHoraPartida)) {
                viagens[passageiroId] = (viagens[passageiroId] ?: 0) + 1
            }
        }
        return viagens to pago
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
            getString(R.string.admin_remover_usuario_mensagem, nome),
            hintSenha = R.string.admin_senha_autorizacao_usuario_hint
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

    // ===================== FINANCEIRO =====================
    // Histórico de cobranças dos R$15,99/30 dias do motorista (ver
    // PagamentoMotorista.kt, concederAcessoMotorista em functions/index.js)
    // — igual às outras seções, carregada na MESMA TELA, logo abaixo do box
    // azul (containerFiltroFinanceiro em activity_administracao_caronas.xml).
    // Adaptado do RelatoriosFinanceirosActivity do Match: mesmo filtro de
    // período + relatório mensal em PDF, sem os cards por "plano" (aqui é um
    // valor fixo só) e sem CPF no PDF (Caronas não coleta CPF de motorista/
    // passageiro comum).

    private fun configurarFiltroFinanceiro() {
        val spinnerAdapter = ArrayAdapter.createFromResource(
            this, R.array.periodos_array, android.R.layout.simple_spinner_item
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriodoFinanceiro.adapter = spinnerAdapter
        spinnerPeriodoFinanceiro.setSelection(PERIODO_FINANCEIRO_TODOS)

        spinnerPeriodoFinanceiro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutDataPersonalizadaFinanceiro.visibility = if (position == PERIODO_FINANCEIRO_PERSONALIZADO) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                layoutDataPersonalizadaFinanceiro.visibility = View.GONE
            }
        }

        aplicarMascaraDataFinanceiro(etDataInicialFinanceiro)
        aplicarMascaraDataFinanceiro(etDataFinalFinanceiro)

        btnFiltrarFinanceiro.setOnClickListener { aplicarFiltroFinanceiro() }
        btnGerarRelatorioFinanceiro.setOnClickListener { mostrarDialogMesAnoFinanceiro() }
    }

    private fun mostrarFinanceiro() {
        prepararSecao(
            getString(R.string.financeiro_titulo),
            getString(R.string.financeiro_sem_dados),
            mostrarFiltroFinanceiro = true
        )
        lifecycleScope.launch {
            adminRepository.listarPagamentosMotorista()
                .onSuccess { pagamentos ->
                    // Estornados não entram no financeiro (totais, lista e PDF).
                    pagamentosMotoristaCache = pagamentos.filter { !it.estornado }
                    aplicarFiltroFinanceiro()
                }
                .onFailure { mostrarErroSecao(it) }
        }
    }

    private fun aplicarFiltroFinanceiro() {
        val periodoSelecionado = spinnerPeriodoFinanceiro.selectedItemPosition
        val agora = System.currentTimeMillis()
        var dataInicialMillis = 0L
        var dataFinalMillis = Long.MAX_VALUE
        var dataLimite = 0L

        if (periodoSelecionado == PERIODO_FINANCEIRO_PERSONALIZADO) {
            val dataInicialStr = etDataInicialFinanceiro.text.toString().trim()
            val dataFinalStr = etDataFinalFinanceiro.text.toString().trim()

            if (TextUtils.isEmpty(dataInicialStr) || TextUtils.isEmpty(dataFinalStr)) {
                Toast.makeText(this, R.string.financeiro_preencha_datas, Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val dataInicial = sdfFinanceiro.parse(dataInicialStr)
                val dataFinal = sdfFinanceiro.parse(dataFinalStr)

                if (dataInicial == null || dataFinal == null) {
                    Toast.makeText(this, R.string.financeiro_erro_data_invalida, Toast.LENGTH_SHORT).show()
                    return
                }
                if (dataInicial.after(dataFinal)) {
                    Toast.makeText(this, R.string.financeiro_erro_data_maior, Toast.LENGTH_SHORT).show()
                    return
                }

                dataInicialMillis = dataInicial.time
                dataFinalMillis = dataFinal.time + 24 * 60 * 60 * 1000 - 1
            } catch (e: ParseException) {
                Toast.makeText(this, R.string.financeiro_erro_data_invalida, Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            dataLimite = when (periodoSelecionado) {
                PERIODO_FINANCEIRO_DIARIO -> agora - 1L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_SEMANAL -> agora - 7L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_MENSAL -> agora - 30L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_TRIMESTRAL -> agora - 90L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_SEMESTRAL -> agora - 180L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_ANUAL -> agora - 365L * 24 * 60 * 60 * 1000
                else -> 0L
            }
        }

        val filtrados = pagamentosMotoristaCache.filter { p ->
            val dataCompra = p.dataCompra ?: return@filter false
            if (periodoSelecionado == PERIODO_FINANCEIRO_PERSONALIZADO) {
                dataCompra in dataInicialMillis..dataFinalMillis
            } else {
                dataLimite == 0L || dataCompra >= dataLimite
            }
        }

        var totalArrecadado = 0.0
        val motoristasUnicos = HashSet<String>()
        filtrados.forEach { p ->
            totalArrecadado += p.valor
            p.usuarioId?.let { if (it.isNotEmpty()) motoristasUnicos.add(it) }
        }
        tvTotalPagamentosFinanceiro.text = filtrados.size.toString()
        tvMotoristasUnicosFinanceiro.text = motoristasUnicos.size.toString()
        tvTotalArrecadadoFinanceiro.text = getString(R.string.financeiro_valor_format, totalArrecadado)

        val ordenados = filtrados.sortedByDescending { it.dataCompra }
        progressBarSecao.visibility = View.GONE
        exibirResultadoSecao(ordenados) { lista -> PagamentoMotoristaAdminAdapter(lista) }
    }

    private fun aplicarMascaraDataFinanceiro(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (isUpdating) return

                var text = s.toString().replace(Regex("[^0-9]"), "")
                if (text.length > 8) text = text.substring(0, 8)

                val formatted = StringBuilder()
                for (i in text.indices) {
                    if (i == 2 || i == 4) formatted.append("/")
                    formatted.append(text[i])
                }

                isUpdating = true
                editText.setText(formatted.toString())
                editText.setSelection(formatted.length)
                isUpdating = false
            }
        })
    }

    // ----- Relatório mensal em PDF (ver PdfUtil.kt) -----

    private fun mostrarDialogMesAnoFinanceiro() {
        val meses = resources.getStringArray(R.array.meses_array)
        val calendarAgora = Calendar.getInstance()
        val anoAtual = calendarAgora.get(Calendar.YEAR)
        val anos = (anoAtual downTo anoAtual - 5).map { it.toString() }.toTypedArray()

        val spinnerMes = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdministracaoCaronasActivity, android.R.layout.simple_spinner_item, meses).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(calendarAgora.get(Calendar.MONTH))
        }
        val spinnerAno = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdministracaoCaronasActivity, android.R.layout.simple_spinner_item, anos).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(0)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(TextView(this@AdministracaoCaronasActivity).apply { text = getString(R.string.financeiro_mes_label) })
            addView(spinnerMes)
            addView(TextView(this@AdministracaoCaronasActivity).apply {
                text = getString(R.string.financeiro_ano_label)
                setPadding(0, 24, 0, 0)
            })
            addView(spinnerAno)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.financeiro_gerar_relatorio_opcao_mensal)
            .setView(container)
            .setPositiveButton(R.string.financeiro_gerar_relatorio_gerar) { _, _ ->
                val mes = spinnerMes.selectedItemPosition
                val ano = anos[spinnerAno.selectedItemPosition].toInt()
                gerarRelatorioMensalFinanceiro(mes, ano)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun gerarRelatorioMensalFinanceiro(mes: Int, ano: Int) {
        val progress = ProgressDialog(this)
        progress.setMessage(getString(R.string.financeiro_carregando_dados))
        progress.setCancelable(false)
        progress.show()

        val calInicio = Calendar.getInstance().apply { clear(); set(ano, mes, 1) }
        val inicioMillis = calInicio.timeInMillis
        val fimMillis = (calInicio.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis

        val linhas = pagamentosMotoristaCache.mapNotNull { p ->
            val dataCompra = p.dataCompra ?: return@mapNotNull null
            if (dataCompra < inicioMillis || dataCompra >= fimMillis) return@mapNotNull null
            if (p.valor <= 0.0) return@mapNotNull null
            LinhaRelatorioFinanceiro(
                nome = p.usuarioNome?.ifEmpty { null } ?: getString(R.string.financeiro_motorista_padrao),
                email = p.usuarioEmail ?: "-",
                valor = p.valor,
                dataCompra = Date(dataCompra),
                expiraEm = p.expiraEm?.let { Date(it) }
            )
        }.sortedBy { it.dataCompra }

        progress.dismiss()

        if (linhas.isEmpty()) {
            Toast.makeText(this, R.string.financeiro_sem_dados, Toast.LENGTH_SHORT).show()
            return
        }

        gerarPdfRelatorioMensalFinanceiro(linhas, mes, ano)
    }

    private fun gerarPdfRelatorioMensalFinanceiro(linhas: List<LinhaRelatorioFinanceiro>, mes: Int, ano: Int) {
        val nomesMeses = resources.getStringArray(R.array.meses_array)
        val periodoLabel = "${nomesMeses[mes]}/$ano"

        val pdf = PdfBuilder(this)
        pdf.titulo(getString(R.string.financeiro_pdf_titulo))
        pdf.subtitulo("${getString(R.string.financeiro_periodo_prefixo)}: $periodoLabel")

        pdf.secao(getString(R.string.financeiro_pdf_secao_detalhes))
        pdf.tabela(
            cabecalhos = listOf(
                getString(R.string.financeiro_pdf_coluna_nome),
                getString(R.string.financeiro_pdf_coluna_email),
                getString(R.string.financeiro_pdf_coluna_valor),
                getString(R.string.financeiro_pdf_coluna_data_compra),
                getString(R.string.financeiro_pdf_coluna_data_expiracao)
            ),
            linhas = linhas.map { l ->
                listOf(l.nome, l.email, formatarValorFinanceiro(l.valor), sdfFinanceiro.format(l.dataCompra), l.expiraEm?.let { sdfFinanceiro.format(it) } ?: "-")
            },
            pesos = listOf(0.28f, 0.27f, 0.15f, 0.15f, 0.15f)
        )

        // Agrupado pelo VALOR pago — mesmo raciocínio plano-agnóstico do
        // Match, mesmo que hoje só exista um valor fixo (R$15,99).
        pdf.secao(getString(R.string.financeiro_pdf_secao_resumo))
        val porValor = linhas.groupBy { it.valor }.toSortedMap()
        pdf.tabela(
            cabecalhos = listOf(
                getString(R.string.financeiro_pdf_coluna_valor),
                getString(R.string.financeiro_pdf_coluna_qtd),
                getString(R.string.financeiro_pdf_coluna_total)
            ),
            linhas = porValor.entries.map { (valor, itens) ->
                listOf(formatarValorFinanceiro(valor), itens.size.toString(), formatarValorFinanceiro(valor * itens.size))
            },
            pesos = listOf(0.34f, 0.33f, 0.33f)
        )
        val totalGeral = linhas.sumOf { it.valor }
        pdf.linhaDestaque(getString(R.string.financeiro_pdf_total_geral, formatarValorFinanceiro(totalGeral)))

        pdf.rodape(getString(R.string.financeiro_gerado_em, sdfFinanceiroCompleto.format(Date())))
        pdf.gerarEAbrir(getString(R.string.financeiro_pdf_nome_arquivo))
    }

    private fun formatarValorFinanceiro(v: Double): String = getString(R.string.financeiro_valor_format, v)

    private data class LinhaRelatorioFinanceiro(
        val nome: String,
        val email: String,
        val valor: Double,
        val dataCompra: Date,
        val expiraEm: Date?
    )

    // Só a seção "Mensagens" usa a caixa de busca — as outras (Motoristas/
    // Passageiros/Viagens/Sugestões) escondem ela de novo ao trocar de
    // seção. "mostrarArquivados" só liga pra seção "Sugestões" (ver
    // mostrarManifestacoes/mostrarManifestacoesArquivadas).
    private fun prepararSecao(
        titulo: String,
        textoVazio: String,
        mostrarBusca: Boolean = false,
        mostrarArquivados: Boolean = false,
        mostrarFiltroFinanceiro: Boolean = false
    ) {
        containerBuscaMensagens.visibility = if (mostrarBusca) View.VISIBLE else View.GONE
        containerFiltroMensagens.visibility = View.GONE
        btnArquivadosManifestacoes.visibility = if (mostrarArquivados) View.VISIBLE else View.GONE
        containerFiltroFinanceiro.visibility = if (mostrarFiltroFinanceiro) View.VISIBLE else View.GONE
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
        containerFiltroMensagens.visibility = View.GONE
        btnArquivadosManifestacoes.visibility = View.GONE
        containerFiltroFinanceiro.visibility = View.GONE
        tvTituloSecao.visibility = View.GONE
        tvVazioSecao.visibility = View.GONE
        rvSecao.visibility = View.GONE
        progressBarSecao.visibility = View.GONE
    }

    private fun configurarFiltroMensagens() {
        val spinnerAdapter = ArrayAdapter.createFromResource(
            this, R.array.periodos_array, android.R.layout.simple_spinner_item
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriodoMensagens.adapter = spinnerAdapter
        spinnerPeriodoMensagens.setSelection(PERIODO_FINANCEIRO_TODOS)

        spinnerPeriodoMensagens.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutDataPersonalizadaMensagens.visibility = if (position == PERIODO_FINANCEIRO_PERSONALIZADO) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                layoutDataPersonalizadaMensagens.visibility = View.GONE
            }
        }

        aplicarMascaraDataFinanceiro(etDataInicialMensagens)
        aplicarMascaraDataFinanceiro(etDataFinalMensagens)

        btnFiltrarMensagens.setOnClickListener { aplicarFiltroMensagens() }
        btnGerarRelatorioMensagens.setOnClickListener { gerarRelatorioMensagens() }
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
                            rvSecao.adapter = UsuarioAdminAdapter(usuarios, onClick = { usuario -> carregarMensagensDoUsuario(usuario) })
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
        nomeUsuarioMensagensCache = nomeExibido
        lifecycleScope.launch {
            adminRepository.listarMensagensDoUsuario(usuarioId)
                .onSuccess { mensagens ->
                    mensagensUsuarioCache = mensagens
                    tvTituloSecao.text = getString(R.string.admin_mensagens_titulo_formato, nomeExibido)
                    tvTituloSecao.visibility = View.VISIBLE
                    containerFiltroMensagens.visibility = View.VISIBLE
                    aplicarFiltroMensagens()
                }
                .onFailure { e ->
                    progressBarSecao.visibility = View.GONE
                    Toast.makeText(this@AdministracaoCaronasActivity, getString(R.string.admin_mensagens_erro_buscar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // Filtro de período (mesmo padrão de aplicarFiltroFinanceiro) aplicado
    // sobre mensagensUsuarioCache já carregada — nenhuma consulta nova ao
    // Firestore, só recorta o que já está em memória.
    private fun aplicarFiltroMensagens() {
        val periodoSelecionado = spinnerPeriodoMensagens.selectedItemPosition
        val agora = System.currentTimeMillis()
        var dataInicialMillis = 0L
        var dataFinalMillis = Long.MAX_VALUE
        var dataLimite = 0L

        if (periodoSelecionado == PERIODO_FINANCEIRO_PERSONALIZADO) {
            val dataInicialStr = etDataInicialMensagens.text.toString().trim()
            val dataFinalStr = etDataFinalMensagens.text.toString().trim()
            if (TextUtils.isEmpty(dataInicialStr) || TextUtils.isEmpty(dataFinalStr)) {
                Toast.makeText(this, R.string.financeiro_preencha_datas, Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val dataInicial = sdfFinanceiro.parse(dataInicialStr)
                val dataFinal = sdfFinanceiro.parse(dataFinalStr)
                if (dataInicial == null || dataFinal == null) {
                    Toast.makeText(this, R.string.financeiro_erro_data_invalida, Toast.LENGTH_SHORT).show()
                    return
                }
                if (dataInicial.after(dataFinal)) {
                    Toast.makeText(this, R.string.financeiro_erro_data_maior, Toast.LENGTH_SHORT).show()
                    return
                }
                dataInicialMillis = dataInicial.time
                dataFinalMillis = dataFinal.time + 24 * 60 * 60 * 1000 - 1
            } catch (e: ParseException) {
                Toast.makeText(this, R.string.financeiro_erro_data_invalida, Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            dataLimite = when (periodoSelecionado) {
                PERIODO_FINANCEIRO_DIARIO -> agora - 1L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_SEMANAL -> agora - 7L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_MENSAL -> agora - 30L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_TRIMESTRAL -> agora - 90L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_SEMESTRAL -> agora - 180L * 24 * 60 * 60 * 1000
                PERIODO_FINANCEIRO_ANUAL -> agora - 365L * 24 * 60 * 60 * 1000
                else -> 0L
            }
        }

        val filtradas = mensagensUsuarioCache.filter { info ->
            val millis = info.mensagem.timestamp?.time ?: return@filter false
            if (periodoSelecionado == PERIODO_FINANCEIRO_PERSONALIZADO) {
                millis in dataInicialMillis..dataFinalMillis
            } else {
                dataLimite == 0L || millis >= dataLimite
            }
        }

        progressBarSecao.visibility = View.GONE
        if (filtradas.isEmpty()) {
            tvVazioSecao.text = getString(R.string.admin_mensagens_vazio)
            tvVazioSecao.visibility = View.VISIBLE
            rvSecao.visibility = View.GONE
        } else {
            tvVazioSecao.visibility = View.GONE
            rvSecao.visibility = View.VISIBLE
            rvSecao.adapter = MensagemAdminAdapter(filtradas)
        }
    }

    // ----- Relatório de mensagens em PDF (mesmo PdfBuilder do Financeiro) -----
    private fun gerarRelatorioMensagens() {
        if (mensagensUsuarioCache.isEmpty()) {
            Toast.makeText(this, R.string.admin_mensagens_relatorio_sem_dados, Toast.LENGTH_SHORT).show()
            return
        }
        val periodoLabel = spinnerPeriodoMensagens.selectedItem?.toString() ?: ""

        val pdf = PdfBuilder(this)
        pdf.titulo(getString(R.string.admin_mensagens_pdf_titulo, nomeUsuarioMensagensCache))
        pdf.subtitulo("${getString(R.string.relatorios_periodo_prefixo)}: $periodoLabel")

        val linhas = mensagensUsuarioCache
            .sortedByDescending { it.mensagem.timestamp?.time ?: 0L }
            .map { info ->
                val rota = getString(
                    R.string.procurar_rota_formato,
                    info.conversa.cidadeOrigem ?: "", info.conversa.cidadeDestino ?: ""
                )
                val remetente = if (info.mensagem.remetenteId == info.conversa.motoristaId) info.conversa.motoristaNome else info.conversa.passageiroNome
                val destinatario = if (info.mensagem.destinatarioId == info.conversa.motoristaId) info.conversa.motoristaNome else info.conversa.passageiroNome
                listOf(
                    rota,
                    "${remetente ?: "?"} → ${destinatario ?: "?"}",
                    info.mensagem.conteudo.orEmpty(),
                    info.mensagem.timestamp?.let { sdfFinanceiroCompleto.format(it) } ?: "-"
                )
            }
        pdf.tabela(
            cabecalhos = listOf(
                getString(R.string.admin_mensagens_pdf_coluna_rota),
                getString(R.string.admin_mensagens_pdf_coluna_participantes),
                getString(R.string.admin_mensagens_pdf_coluna_conteudo),
                getString(R.string.admin_mensagens_pdf_coluna_data)
            ),
            linhas = linhas,
            pesos = listOf(0.2f, 0.25f, 0.4f, 0.15f)
        )

        pdf.rodape(getString(R.string.financeiro_gerado_em, sdfFinanceiroCompleto.format(Date())))
        pdf.gerarEAbrir(getString(R.string.admin_mensagens_pdf_nome_arquivo))
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

    companion object {
        const val EXTRA_ABRIR_SECAO = "abrirSecao"
        const val SECAO_MENSAGENS = "mensagens"

        // Mesmas posições do R.array.periodos_array — reaproveitadas pelos
        // dois filtros de período da tela (Financeiro e Mensagens), que
        // usam o mesmo spinner/array, só com nomes de variável próprios
        // pra deixar claro qual seção cada campo pertence.
        private const val PERIODO_FINANCEIRO_TODOS = 0
        private const val PERIODO_FINANCEIRO_DIARIO = 1
        private const val PERIODO_FINANCEIRO_SEMANAL = 2
        private const val PERIODO_FINANCEIRO_MENSAL = 3
        private const val PERIODO_FINANCEIRO_TRIMESTRAL = 4
        private const val PERIODO_FINANCEIRO_SEMESTRAL = 5
        private const val PERIODO_FINANCEIRO_ANUAL = 6
        private const val PERIODO_FINANCEIRO_PERSONALIZADO = 7
    }
}

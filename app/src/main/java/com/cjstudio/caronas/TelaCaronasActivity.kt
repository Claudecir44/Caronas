package com.cjstudio.caronas

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    lateinit var avaliacaoRepository: IAvaliacaoRepository

    @Inject
    lateinit var autocompleteRepository: IAutocompleteRepository

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    @Inject
    lateinit var bloqueioRepository: IBloqueioRepository

    @Inject
    lateinit var falhasRepository: IFalhasRepository

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var tvPapelUsuario: TextView
    private lateinit var tvNomeUsuario: TextView
    private lateinit var tvContadorViagensGratis: TextView
    private lateinit var tvContadorViagensRealizadas: TextView
    private lateinit var btnMinhasOfertasOuViagens: TextView
    private lateinit var badgeMinhasOfertasOuViagens: TextView
    private lateinit var btnProcurar: TextView
    private lateinit var btnOferecer: TextView
    private lateinit var btnPagamentos: TextView
    private lateinit var badgeChatNaoLidas: TextView
    private lateinit var btnSair: Button
    private lateinit var tvResumoBusca: TextView
    private lateinit var tvSemResultadosBusca: TextView
    private lateinit var rvResultadosBusca: RecyclerView
    private lateinit var progressBarBusca: ProgressBar
    private lateinit var layoutAbasOfertas: LinearLayout
    private lateinit var tvAbaOfertas: TextView
    private lateinit var tvAbaRecebidas: TextView
    private lateinit var containerSecaoDois: LinearLayout
    private lateinit var progressBarSecaoDois: ProgressBar
    private lateinit var tvSemResultadosSecaoDois: TextView
    private lateinit var rvSecaoDois: RecyclerView
    private lateinit var layoutResumoAvaliacoes: LinearLayout
    private lateinit var tvViagensRealizadasAvaliacoes: TextView
    private lateinit var tvNota5Avaliacoes: TextView
    private lateinit var tvNota4Avaliacoes: TextView
    private lateinit var tvNota3Avaliacoes: TextView
    private lateinit var tvNota2Avaliacoes: TextView
    private lateinit var tvNota1Avaliacoes: TextView
    private var usuarioAtual: Usuario? = null
    private var mostrandoMinhasViagens = false
    private var mostrandoMinhasOfertas = false
    // Aba aberta em "Minhas Ofertas": só uma lista aparece por vez.
    private var abaOfertaAtual = ABA_OFERTAS
    private var mostrandoAvaliacoes = false
    private var adapterResultadosBusca: CaronaResultadoAdapter? = null
    // Evita registrar um segundo listener de badge a cada carregarPerfil()
    // (chamado de novo em todo onResume) — só assina a contagem certa uma
    // vez, na primeira vez que o papel do usuário é conhecido.
    private var badgeMinhasOfertasIniciado = false

    private val formatoDataOferta = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val formatoHoraOferta = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    // Cidades da última busca feita — usadas em solicitarVaga pra saber
    // EXATAMENTE qual trecho da rota o passageiro quer (pode ser só um
    // pedaço, se a carona tem paradas intermediárias), sem precisar de um
    // diálogo extra de "escolher trecho": a busca já É a escolha do trecho.
    private var ultimaBuscaOrigem: String? = null
    private var ultimaBuscaDestino: String? = null

    private val formatoDataBusca = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    // Só precisa pedir em runtime a partir do Android 13 (API 33) — versões
    // anteriores concedem a permissão de notificação automaticamente no
    // manifest, sem diálogo. Resultado ignorado de propósito: se o usuário
    // negar, o app continua funcionando normalmente, só sem notificações.
    private val lancadorPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tela_caronas)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        tvPapelUsuario = findViewById(R.id.tvPapelUsuario)
        tvNomeUsuario = findViewById(R.id.tvNomeUsuario)
        tvContadorViagensGratis = findViewById(R.id.tvContadorViagensGratis)
        tvContadorViagensRealizadas = findViewById(R.id.tvContadorViagensRealizadas)
        btnMinhasOfertasOuViagens = findViewById(R.id.btnMinhasOfertasOuViagens)
        badgeMinhasOfertasOuViagens = findViewById(R.id.badgeMinhasOfertasOuViagens)
        btnSair = findViewById(R.id.btnSair)
        tvResumoBusca = findViewById(R.id.tvResumoBusca)
        tvSemResultadosBusca = findViewById(R.id.tvSemResultadosBusca)
        rvResultadosBusca = findViewById(R.id.rvResultadosBusca)
        progressBarBusca = findViewById(R.id.progressBarBusca)
        rvResultadosBusca.layoutManager = LinearLayoutManager(this)
        layoutAbasOfertas = findViewById(R.id.layoutAbasOfertas)
        tvAbaOfertas = findViewById(R.id.tvAbaViagensOfertadas)
        tvAbaRecebidas = findViewById(R.id.tvAbaSolicitacoesRecebidas)
        containerSecaoDois = findViewById(R.id.containerSecaoDois)
        tvAbaOfertas.setOnClickListener { selecionarAbaOfertas(ABA_OFERTAS) }
        tvAbaRecebidas.setOnClickListener { selecionarAbaOfertas(ABA_RECEBIDAS) }
        progressBarSecaoDois = findViewById(R.id.progressBarSecaoDois)
        tvSemResultadosSecaoDois = findViewById(R.id.tvSemResultadosSecaoDois)
        rvSecaoDois = findViewById(R.id.rvSecaoDois)
        rvSecaoDois.layoutManager = LinearLayoutManager(this)
        layoutResumoAvaliacoes = findViewById(R.id.layoutResumoAvaliacoes)
        tvViagensRealizadasAvaliacoes = findViewById(R.id.tvViagensRealizadasAvaliacoes)
        tvNota5Avaliacoes = findViewById(R.id.tvNota5Avaliacoes)
        tvNota4Avaliacoes = findViewById(R.id.tvNota4Avaliacoes)
        tvNota3Avaliacoes = findViewById(R.id.tvNota3Avaliacoes)
        tvNota2Avaliacoes = findViewById(R.id.tvNota2Avaliacoes)
        tvNota1Avaliacoes = findViewById(R.id.tvNota1Avaliacoes)

        val tvMeuPerfil = findViewById<TextView>(R.id.tvMeuPerfil)
        val tvConfiguracoes = findViewById<TextView>(R.id.tvConfiguracoes)
        tvConfiguracoes.setOnClickListener {
            startActivity(Intent(this, ConfiguracoesCaronasActivity::class.java))
        }
        btnProcurar = findViewById(R.id.btnProcurar)
        btnOferecer = findViewById(R.id.btnOferecer)
        btnPagamentos = findViewById(R.id.btnPagamentos)
        val btnChat = findViewById<TextView>(R.id.btnChat)
        badgeChatNaoLidas = findViewById(R.id.badgeChatNaoLidas)
        val btnAvaliacoes = findViewById<TextView>(R.id.btnAvaliacoes)

        solicitarPermissaoNotificacaoSeNecessario()
        carregarPerfil()
        escutarBadgeChat()

        // "Meu Perfil" já abre a tela de edição (que reúne ver + editar +
        // excluir cadastro) — não tem mais link separado de "Editar Perfil".
        tvMeuPerfil.setOnClickListener {
            startActivity(Intent(this, EditarCadastroCaronasActivity::class.java))
        }

        btnProcurar.setOnClickListener { abrirDialogBuscarCarona() }
        btnOferecer.setOnClickListener { abrirOferecerCarona() }
        // Conteúdo/configuração real ainda por vir (pedido explícito do
        // usuário: "a ser criado a sua configuração depois") — por ora
        // abre a mesma tela de acesso pago já existente, é o que mais se
        // aproxima de "Pagamentos" hoje (ver AssinaturaMotoristaActivity).
        btnPagamentos.setOnClickListener {
            startActivity(Intent(this, AssinaturaMotoristaActivity::class.java))
        }
        btnChat.setOnClickListener { startActivity(Intent(this, ConversasCaronaActivity::class.java)) }
        btnAvaliacoes.setOnClickListener { toggleAvaliacoes() }

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
        // Também cobre a volta do chat com um passageiro, sem precisar
        // fechar e reabrir a seção (mesmo comentário que existia em
        // MinhasOfertasActivity.onResume, agora embutida aqui).
        if (mostrandoMinhasOfertas && abaOfertaAtual == ABA_RECEBIDAS) carregarSolicitacoesRecebidas()
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

        AutocompleteEnderecoUtil.ligar(this, etOrigem, autocompleteRepository, TipoAutocomplete.CIDADE)
        AutocompleteEnderecoUtil.ligar(this, etDestino, autocompleteRepository, TipoAutocomplete.CIDADE)

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
        mostrandoAvaliacoes = false
        layoutResumoAvaliacoes.visibility = View.GONE
        esconderSecaoDois()
        ultimaBuscaOrigem = origem
        ultimaBuscaDestino = destino
        progressBarBusca.visibility = View.VISIBLE

        lifecycleScope.launch {
            caronaRepository.buscarCaronas(origem, destino, data)
                .onSuccess { todasCaronas ->
                    // Some quem tem bloqueio com o usuário (em qualquer
                    // sentido, ver Bloqueio.kt). Falha ao ler os bloqueios
                    // não impede a busca; a regra de solicitacoes barra a
                    // solicitação de qualquer jeito.
                    val bloqueados = bloqueioRepository.idsComBloqueio().getOrDefault(emptySet())
                    val caronas = todasCaronas.filter { it.motoristaId !in bloqueados }
                    // Resolve o TRECHO buscado (índices + preço só daquele
                    // pedaço) de cada resultado — ver resolverTrecho. Preço
                    // e rota mostrados ao passageiro nunca são os da viagem
                    // inteira do motorista, só os do trecho que ele
                    // procurou (ver ResultadoBuscaCarona).
                    val resultados = withContext(Dispatchers.IO) {
                        caronas.map { carona -> resolverTrecho(carona, origem, destino) }
                    }

                    progressBarBusca.visibility = View.GONE
                    tvResumoBusca.visibility = View.VISIBLE
                    tvResumoBusca.text = getString(
                        R.string.procurar_resumo_formato,
                        resultados.size,
                        origem,
                        destino,
                        formatoDataBusca.format(data.time)
                    )

                    if (resultados.isEmpty()) {
                        rvResultadosBusca.visibility = View.GONE
                        tvSemResultadosBusca.text = getString(R.string.procurar_sem_resultados)
                        tvSemResultadosBusca.visibility = View.VISIBLE
                    } else {
                        tvSemResultadosBusca.visibility = View.GONE
                        rvResultadosBusca.visibility = View.VISIBLE
                        adapterResultadosBusca = CaronaResultadoAdapter(
                            resultados,
                            onSolicitarClick = { resultado -> solicitarVaga(resultado) },
                            onPerfilClick = { usuarioId -> abrirPerfilPublico(usuarioId, comoMotorista = true) }
                        )
                        rvResultadosBusca.adapter = adapterResultadosBusca
                    }
                }
                .onFailure { e ->
                    progressBarBusca.visibility = View.GONE
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.procurar_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // Acha em que índices da rota da carona ficam a cidade de embarque e a
    // de desembarque buscadas, e calcula o preço só daquele trecho — cai de
    // volta pra rota inteira (0..último índice) se por algum motivo a
    // cidade buscada não bater com nenhuma parada (não deveria acontecer,
    // já que buscarCaronas só devolve caronas cujas paradas já batem com a
    // busca). Chamado de dentro de Dispatchers.IO (ver buscarCaronas) —
    // pode geocodificar.
    private suspend fun resolverTrecho(carona: Carona, origemBuscada: String, destinoBuscado: String): ResultadoBuscaCarona {
        val origemNormalizada = TextoUtil.normalizar(origemBuscada)
        val destinoNormalizado = TextoUtil.normalizar(destinoBuscado)
        val indiceOrigem = carona.paradas.indexOfFirst { it.cidadeBusca == origemNormalizada }.takeIf { it != -1 } ?: 0
        val indiceDestino = carona.paradas.indexOfLast { it.cidadeBusca == destinoNormalizado }.takeIf { it != -1 } ?: carona.paradas.lastIndex

        val cidadeEmbarque = carona.paradas.getOrNull(indiceOrigem)?.cidade ?: origemBuscada
        val cidadeDesembarque = carona.paradas.getOrNull(indiceDestino)?.cidade ?: destinoBuscado

        // Trecho = rota inteira: usa o valor que o motorista já publicou,
        // sem geocodificar de novo. Trecho parcial: em vez de recalcular um
        // preço do zero (o que IGNORARIA se o motorista alterou manualmente
        // o valor por vaga, mostrando ao passageiro um número diferente do
        // que o motorista realmente definiu), cobra a fração proporcional
        // do preço REAL da oferta — distância do trecho / distância total
        // da rota, aplicada sobre carona.valorPorVaga (nunca sobre um valor
        // sugerido recalculado). Só cai pro cálculo antigo (sugestão pura)
        // se a carona não tiver distanciaKm/valorPorVaga salvos (ofertas
        // publicadas antes desses campos existirem).
        val trechoInteiro = indiceOrigem == 0 && indiceDestino == carona.paradas.lastIndex
        val paradaOrigem = carona.paradas.getOrNull(indiceOrigem)
        val paradaDestino = carona.paradas.getOrNull(indiceDestino)
        // Distância do trecho buscado — base do tempo aproximado mostrado no
        // card (TempoViagemUtil). Trecho inteiro reaproveita a que a oferta
        // já tem gravada; trecho parcial vem da mesma geocodificação usada
        // pro preço logo abaixo.
        var distanciaTrechoKm: Double? = if (trechoInteiro) carona.distanciaKm else null

        val valorTrecho = if (trechoInteiro) {
            carona.valorPorVaga ?: 0.0
        } else {
            val calendario = Calendar.getInstance().apply { timeInMillis = carona.dataHoraPartida ?: System.currentTimeMillis() }
            val sugestao = if (paradaOrigem?.cidade != null && paradaDestino?.cidade != null) {
                runCatching {
                    DistanciaUtil.calcularSugestao(
                        this@TelaCaronasActivity,
                        DistanciaUtil.pontoParaGeocoding(paradaOrigem),
                        DistanciaUtil.pontoParaGeocoding(paradaDestino),
                        calendario
                    )
                }.getOrNull()
            } else null
            distanciaTrechoKm = sugestao?.distanciaKm

            val distanciaTotal = carona.distanciaKm
            val precoTotal = carona.valorPorVaga
            if (sugestao != null && distanciaTotal != null && distanciaTotal > 0 && precoTotal != null) {
                precoTotal * (sugestao.distanciaKm / distanciaTotal)
            } else {
                sugestao?.valorSugerido ?: (carona.valorPorVaga ?: 0.0)
            }
        }

        // Vagas livres só do trecho buscado (embarque->desembarque), não a
        // capacidade total do carro — mesmo raciocínio do preço acima: um
        // trecho pode já estar cheio mesmo com o carro tendo vaga livre em
        // outra perna da rota.
        val vagasDisponiveis = solicitacaoRepository.vagasDisponiveis(carona, indiceOrigem, indiceDestino).getOrDefault(carona.vagas)

        // Oferta antiga sem distanciaKm gravada: geocodifica o trecho inteiro
        // (só nesse caso — se o trecho parcial falhou acima, tentar de novo
        // falharia igual).
        if (distanciaTrechoKm == null && trechoInteiro && paradaOrigem != null && paradaDestino != null) {
            distanciaTrechoKm = distanciaCacheada(
                DistanciaUtil.pontoParaGeocoding(paradaOrigem),
                DistanciaUtil.pontoParaGeocoding(paradaDestino)
            )
        }

        return ResultadoBuscaCarona(carona, indiceOrigem, indiceDestino, cidadeEmbarque, cidadeDesembarque, valorTrecho, vagasDisponiveis, distanciaTrechoKm)
    }

    // Distâncias já geocodificadas nesta tela (origem, destino -> km) — o
    // mesmo trecho aparece em vários cards/listas e o Geocoder é lento e
    // limitado. Só guarda sucesso (falha tenta de novo na próxima vez).
    // ConcurrentHashMap porque "Minhas Ofertas" carrega ofertas e
    // solicitações recebidas ao mesmo tempo.
    private val distanciaPorTrechoCache = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Double>()

    private suspend fun distanciaCacheada(origem: String, destino: String): Double? = withContext(Dispatchers.IO) {
        val chave = origem to destino
        distanciaPorTrechoCache[chave] ?: runCatching {
            DistanciaUtil.distanciaAproximadaKm(this@TelaCaronasActivity, origem, destino)
        }.getOrNull()?.also { distanciaPorTrechoCache[chave] = it }
    }

    // Ofertas publicadas antes do tempo aproximado existir não têm
    // distanciaKm gravada: completa em memória (não regrava) DEPOIS que a
    // lista já está na tela, pra não atrasar a abertura por causa do
    // Geocoder, e avisa o adapter atual pra redesenhar os cards.
    private suspend fun completarDistanciaOfertas(ofertas: List<Carona>) {
        var alterou = false
        for (oferta in ofertas.filter { it.distanciaKm == null }) {
            val origem = oferta.paradas.firstOrNull()?.let { DistanciaUtil.pontoParaGeocoding(it) } ?: oferta.cidadeOrigem ?: continue
            val destino = oferta.paradas.lastOrNull()?.let { DistanciaUtil.pontoParaGeocoding(it) } ?: oferta.cidadeDestino ?: continue
            oferta.distanciaKm = distanciaCacheada(origem, destino)?.also { alterou = true }
        }
        if (alterou) rvResultadosBusca.adapter?.notifyDataSetChanged()
    }

    // Mesmo para pedidos de vaga antigos (sem distanciaKm) — usados tanto em
    // "Minhas Viagens" (rvResultadosBusca) quanto em "Solicitações
    // Recebidas" (rvSecaoDois).
    private suspend fun completarDistanciaSolicitacoes(solicitacoes: List<Solicitacao>, lista: RecyclerView) {
        var alterou = false
        for (solicitacao in solicitacoes.filter { it.distanciaKm == null }) {
            val origem = DistanciaUtil.pontoParaGeocoding(solicitacao.cidadeOrigem, solicitacao.enderecoOrigem)
            val destino = DistanciaUtil.pontoParaGeocoding(solicitacao.cidadeDestino, solicitacao.enderecoDestino)
            if (origem.isBlank() || destino.isBlank()) continue
            solicitacao.distanciaKm = distanciaCacheada(origem, destino)?.also { alterou = true }
        }
        if (alterou) lista.adapter?.notifyDataSetChanged()
    }

    private fun solicitarVaga(resultado: ResultadoBuscaCarona) {
        val carona = resultado.carona
        val caronaId = carona.id

        // Marca otimista (antes da resposta do servidor) pra desabilitar o
        // botão na hora — evita um segundo toque durante a espera da rede
        // virar uma segunda solicitação.
        caronaId?.let { adapterResultadosBusca?.marcarComoSolicitada(it) }

        lifecycleScope.launch {
            // Índices e preço já vêm resolvidos da busca (ver
            // resolverTrecho) — não recalcula aqui, garante que o valor
            // mostrado na lista é exatamente o valor enviado no pedido.
            solicitacaoRepository.solicitarVaga(carona, resultado.indiceOrigem, resultado.indiceDestino, resultado.valorTrecho, resultado.distanciaTrechoKm)
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
        esconderSecaoDois()
        carregarMinhasViagens()
    }

    private fun carregarMinhasViagens() {
        mostrandoMinhasViagens = true
        mostrandoAvaliacoes = false
        layoutResumoAvaliacoes.visibility = View.GONE
        progressBarBusca.visibility = View.VISIBLE
        tvResumoBusca.visibility = View.GONE
        tvSemResultadosBusca.visibility = View.GONE
        rvResultadosBusca.visibility = View.GONE

        lifecycleScope.launch {
            solicitacaoRepository.buscarMinhasSolicitacoes()
                .onSuccess { viagens ->
                    // 1 consulta só pra saber quais o passageiro já avaliou
                    // (ver comentário equivalente em MinhasOfertasActivity).
                    val avaliadas = avaliacaoRepository.buscarSolicitacoesJaAvaliadas().getOrDefault(emptySet())
                    // Marca as confirmações já vistas ao abrir a tela — zera
                    // o badge de "Minhas Viagens" (ver
                    // escutarBadgeMinhasOfertasOuViagens/Solicitacao.confirmacaoVista).
                    solicitacaoRepository.marcarConfirmacoesComoVistas()
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
                            avaliadas,
                            onCancelarClick = { confirmarCancelarViagem(it) },
                            onChatClick = { abrirChatViagem(it) },
                            onAvaliarClick = { abrirDialogAvaliarMotorista(it) },
                            onPerfilClick = { usuarioId -> abrirPerfilPublico(usuarioId, comoMotorista = true) },
                            onExcluirLongClick = { confirmarExcluirViagem(it) }
                        )
                        completarDistanciaSolicitacoes(viagens, rvResultadosBusca)
                    }
                }
                .onFailure { e ->
                    progressBarBusca.visibility = View.GONE
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_viagens_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // Esconde tudo que é só de "Minhas Ofertas": as abas e a lista de
    // Solicitações Recebidas. Chamado por todo outro modo da área de
    // resultados (busca, Minhas Viagens, Avaliações) e ao fechar Minhas Ofertas.
    private fun esconderSecaoDois() {
        layoutAbasOfertas.visibility = View.GONE
        containerSecaoDois.visibility = View.GONE
        progressBarSecaoDois.visibility = View.GONE
        tvSemResultadosSecaoDois.visibility = View.GONE
        rvSecaoDois.visibility = View.GONE
    }

    // Só uma aba de Minhas Ofertas aberta por vez: "Viagens Ofertadas" usa a
    // área compartilhada (rvResultadosBusca...), "Solicitações Recebidas" usa
    // o container próprio. Tocar numa aba recarrega SÓ aquela lista (dados
    // sempre frescos e o estado visual dela vem do próprio carregamento).
    private fun selecionarAbaOfertas(aba: Int) {
        abaOfertaAtual = aba
        aplicarVisibilidadeAbas()
        if (aba == ABA_OFERTAS) carregarOfertas() else carregarSolicitacoesRecebidas()
    }

    // Reforça qual lista pode aparecer — também chamado pelos carregamentos,
    // porque um deles pode terminar (ex.: recarga depois de confirmar/cancelar)
    // com a OUTRA aba aberta e não deve reaparecer por cima.
    private fun aplicarVisibilidadeAbas() {
        if (!mostrandoMinhasOfertas) return
        val naAbaOfertas = abaOfertaAtual == ABA_OFERTAS
        layoutAbasOfertas.visibility = View.VISIBLE
        containerSecaoDois.visibility = if (naAbaOfertas) View.GONE else View.VISIBLE
        if (!naAbaOfertas) {
            progressBarBusca.visibility = View.GONE
            tvResumoBusca.visibility = View.GONE
            tvSemResultadosBusca.visibility = View.GONE
            rvResultadosBusca.visibility = View.GONE
        }
        estilizarAba(tvAbaOfertas, naAbaOfertas)
        estilizarAba(tvAbaRecebidas, !naAbaOfertas)
    }

    private fun estilizarAba(aba: TextView, selecionada: Boolean) {
        aba.setBackgroundResource(if (selecionada) R.drawable.aba_ofertas_selecionada else R.drawable.aba_ofertas_normal)
        aba.setTextColor(if (selecionada) 0xFF1E90FF.toInt() else 0xFF666666.toInt())
    }

    // "Minhas Ofertas" (motorista) — mesmo espírito de "Minhas Viagens"
    // acima, só que com duas abas na mesma linha (Viagens Ofertadas e
    // Solicitações Recebidas, ver selecionarAbaOfertas) — reúne o que antes era a tela
    // separada MinhasOfertasActivity (removida), nesta mesma tela, abaixo
    // do quadro azul.
    private fun toggleMinhasOfertas() {
        if (mostrandoMinhasOfertas) {
            mostrandoMinhasOfertas = false
            progressBarBusca.visibility = View.GONE
            tvResumoBusca.visibility = View.GONE
            tvSemResultadosBusca.visibility = View.GONE
            rvResultadosBusca.visibility = View.GONE
            esconderSecaoDois()
            return
        }
        mostrandoMinhasViagens = false
        mostrandoMinhasOfertas = true
        // Abre em "Viagens Ofertadas". Só UMA lista é carregada por vez
        // (a da aba aberta) — o que também evita o problema antigo de rodar
        // as duas em paralelo, em que a que terminasse por último
        // sobrescrevia o adapter da outra e fechava um card recém-aberto.
        selecionarAbaOfertas(ABA_OFERTAS)
    }

    private fun carregarOfertas() {
        mostrandoAvaliacoes = false
        layoutResumoAvaliacoes.visibility = View.GONE
        progressBarBusca.visibility = View.VISIBLE
        tvResumoBusca.visibility = View.GONE
        tvSemResultadosBusca.visibility = View.GONE
        rvResultadosBusca.visibility = View.GONE
        aplicarVisibilidadeAbas()

        lifecycleScope.launch {
            caronaRepository.buscarMinhasOfertas()
                .onSuccess { ofertas ->
                    // Vagas ainda livres da rota INTEIRA de cada oferta
                    // (0..última parada) — descontando solicitações
                    // pendentes/confirmadas, nunca a capacidade total
                    // estática (ver ResultadoBuscaCarona pro mesmo ajuste do
                    // lado do passageiro).
                    val vagasPorOferta = ofertas.mapNotNull { oferta ->
                        val id = oferta.id ?: return@mapNotNull null
                        val ultimoIndice = oferta.paradas.lastIndex.coerceAtLeast(1)
                        val disponiveis = solicitacaoRepository.vagasDisponiveis(oferta, 0, ultimoIndice).getOrDefault(oferta.vagas)
                        id to disponiveis
                    }.toMap()

                    // Total recebido por oferta = soma de valorPago de todas
                    // as solicitações CONFIRMADAS daquela carona — uma
                    // query só (buscarSolicitacoesRecebidas já traz TODAS as
                    // solicitações recebidas pelo motorista, de qualquer
                    // oferta), agrupada aqui por caronaId em vez de 1
                    // consulta por oferta.
                    val totalPorOferta = solicitacaoRepository.buscarSolicitacoesRecebidas().getOrDefault(emptyList())
                        .filter { it.status == "confirmada" }
                        .groupBy { it.caronaId }
                        .mapNotNull { (caronaId, solicitacoes) ->
                            caronaId?.let { it to solicitacoes.sumOf { s -> s.valorPago ?: 0.0 } }
                        }
                        .toMap()

                    progressBarBusca.visibility = View.GONE
                    if (ofertas.isEmpty()) {
                        tvSemResultadosBusca.visibility = View.VISIBLE
                        tvSemResultadosBusca.text = getString(R.string.minhas_ofertas_vazio)
                        aplicarVisibilidadeAbas()
                    } else {
                        rvResultadosBusca.visibility = View.VISIBLE
                        rvResultadosBusca.adapter = MinhaOfertaAdapter(
                            ofertas,
                            vagasDisponiveisPorOferta = vagasPorOferta,
                            totalRecebidoPorOferta = totalPorOferta,
                            onEditarClick = { abrirDialogEditarOferta(it) },
                            onExcluirClick = { confirmarExcluirOferta(it) }
                        )
                        aplicarVisibilidadeAbas()
                        completarDistanciaOfertas(ofertas)
                    }
                }
                .onFailure { e ->
                    progressBarBusca.visibility = View.GONE
                    aplicarVisibilidadeAbas()
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_ofertas_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun carregarSolicitacoesRecebidas() {
        progressBarSecaoDois.visibility = View.VISIBLE
        tvSemResultadosSecaoDois.visibility = View.GONE
        rvSecaoDois.visibility = View.GONE

        lifecycleScope.launch {
            solicitacaoRepository.buscarSolicitacoesRecebidas()
                .onSuccess { solicitacoes ->
                    // Busca de uma vez só quais dessas o motorista já
                    // avaliou (1 consulta, não 1 por card).
                    val avaliadas = avaliacaoRepository.buscarSolicitacoesJaAvaliadas().getOrDefault(emptySet())
                    // Marca os cancelamentos (feitos pelo passageiro) já
                    // vistos ao abrir a tela — zera essa parte do badge de
                    // "Minhas Ofertas" (ver
                    // escutarBadgeMinhasOfertasOuViagens/Solicitacao.canceladoVisto).
                    solicitacaoRepository.marcarCancelamentosComoVistos()
                    progressBarSecaoDois.visibility = View.GONE
                    if (solicitacoes.isEmpty()) {
                        tvSemResultadosSecaoDois.visibility = View.VISIBLE
                    } else {
                        rvSecaoDois.visibility = View.VISIBLE
                        rvSecaoDois.adapter = SolicitacaoRecebidaAdapter(
                            solicitacoes,
                            avaliadas,
                            onConfirmarClick = { confirmarSolicitacao(it) },
                            onChatClick = { abrirChatViagem(it) },
                            onExcluirLongClick = { confirmarExcluirSolicitacao(it) },
                            onAvaliarClick = { abrirDialogAvaliarPassageiro(it) },
                            onPerfilClick = { usuarioId -> abrirPerfilPublico(usuarioId, comoMotorista = false) },
                            onCancelarClick = { confirmarCancelarComoMotorista(it) }
                        )
                        completarDistanciaSolicitacoes(solicitacoes, rvSecaoDois)
                    }
                }
                .onFailure { e ->
                    progressBarSecaoDois.visibility = View.GONE
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.solicitacoes_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // "Avaliações" (motorista ou passageiro, mesmo botão pros dois) — mesmo
    // espaço abaixo do quadro azul das outras seções, só que com um resumo
    // fixo (layoutResumoAvaliacoes) por cima da lista de comentários.
    private fun toggleAvaliacoes() {
        if (mostrandoAvaliacoes) {
            mostrandoAvaliacoes = false
            layoutResumoAvaliacoes.visibility = View.GONE
            progressBarBusca.visibility = View.GONE
            tvResumoBusca.visibility = View.GONE
            tvSemResultadosBusca.visibility = View.GONE
            rvResultadosBusca.visibility = View.GONE
            return
        }
        val usuario = usuarioAtual
        if (usuario == null) {
            Toast.makeText(this, R.string.carregando, Toast.LENGTH_SHORT).show()
            return
        }
        mostrandoMinhasViagens = false
        mostrandoMinhasOfertas = false
        esconderSecaoDois()
        mostrandoAvaliacoes = true
        carregarAvaliacoes(usuario)
    }

    private fun carregarAvaliacoes(usuario: Usuario) {
        val uid = usuario.id ?: return
        layoutResumoAvaliacoes.visibility = View.GONE
        progressBarBusca.visibility = View.VISIBLE
        tvResumoBusca.visibility = View.GONE
        tvSemResultadosBusca.visibility = View.GONE
        rvResultadosBusca.visibility = View.GONE

        lifecycleScope.launch {
            // "Viagens realizadas": como motorista, solicitações recebidas
            // já confirmadas E já ocorridas; como passageiro, as próprias
            // solicitações nas mesmas condições — mesmo critério de
            // "concluída" já usado em MinhaViagemAdapter/
            // SolicitacaoRecebidaAdapter (não existe um status "concluída"
            // próprio, ver Solicitacao.kt).
            val resultadoViagens = if (usuario.motorista) {
                solicitacaoRepository.buscarSolicitacoesRecebidas()
            } else {
                solicitacaoRepository.buscarMinhasSolicitacoes()
            }
            val resultadoAvaliacoes = avaliacaoRepository.buscarAvaliacoesRecebidas(uid)

            val erro = resultadoViagens.exceptionOrNull() ?: resultadoAvaliacoes.exceptionOrNull()
            if (erro != null) {
                progressBarBusca.visibility = View.GONE
                Toast.makeText(this@TelaCaronasActivity, getString(R.string.tela_avaliacoes_erro_carregar, erro.message), Toast.LENGTH_LONG).show()
                return@launch
            }

            val agora = System.currentTimeMillis()
            val viagensRealizadas = resultadoViagens.getOrDefault(emptyList())
                .count { it.status == "confirmada" && (it.dataHoraPartida ?: 0L) < agora }

            val avaliacoes = resultadoAvaliacoes.getOrDefault(emptyList())
            // Índices 1..5 — índice 0 não é usado (nota sempre entre 1 e 5,
            // ver AvaliacaoRepository.avaliar).
            val contagemPorNota = IntArray(6)
            avaliacoes.forEach { avaliacao -> if (avaliacao.nota in 1..5) contagemPorNota[avaliacao.nota]++ }

            progressBarBusca.visibility = View.GONE
            layoutResumoAvaliacoes.visibility = View.VISIBLE
            tvViagensRealizadasAvaliacoes.text = getString(R.string.tela_avaliacoes_viagens_formato, viagensRealizadas)
            tvNota5Avaliacoes.text = getString(R.string.tela_avaliacoes_estrela_contagem_formato, "⭐⭐⭐⭐⭐", contagemPorNota[5])
            tvNota4Avaliacoes.text = getString(R.string.tela_avaliacoes_estrela_contagem_formato, "⭐⭐⭐⭐", contagemPorNota[4])
            tvNota3Avaliacoes.text = getString(R.string.tela_avaliacoes_estrela_contagem_formato, "⭐⭐⭐", contagemPorNota[3])
            tvNota2Avaliacoes.text = getString(R.string.tela_avaliacoes_estrela_contagem_formato, "⭐⭐", contagemPorNota[2])
            tvNota1Avaliacoes.text = getString(R.string.tela_avaliacoes_estrela_contagem_formato, "⭐", contagemPorNota[1])

            tvResumoBusca.visibility = View.VISIBLE
            tvResumoBusca.text = getString(R.string.tela_avaliacoes_titulo)

            // Só os comentários de verdade preenchidos — sem nome, sem
            // data, sem nota por comentário (ver ComentarioAvaliacaoAdapter).
            val comentarios = avaliacoes.mapNotNull { it.comentario?.trim()?.takeIf { texto -> texto.isNotEmpty() } }
            if (comentarios.isEmpty()) {
                tvSemResultadosBusca.visibility = View.VISIBLE
                tvSemResultadosBusca.text = getString(R.string.tela_avaliacoes_sem_comentarios)
            } else {
                rvResultadosBusca.visibility = View.VISIBLE
                rvResultadosBusca.adapter = ComentarioAvaliacaoAdapter(comentarios)
            }
        }
    }

    private fun abrirDialogAvaliarPassageiro(solicitacao: Solicitacao) {
        val passageiroId = solicitacao.passageiroId ?: return
        val nome = solicitacao.passageiroNome?.trim()?.substringBefore(" ")
            ?: getString(R.string.tela_papel_passageiro)
        AvaliacaoDialogUtil.mostrar(this, nome) { nota, comentario ->
            lifecycleScope.launch {
                avaliacaoRepository.avaliar(solicitacao, passageiroId, nota, comentario)
                    .onSuccess {
                        Toast.makeText(this@TelaCaronasActivity, R.string.avaliar_sucesso, Toast.LENGTH_SHORT).show()
                        carregarSolicitacoesRecebidas()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@TelaCaronasActivity, getString(R.string.avaliar_erro_enviar, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun confirmarSolicitacao(solicitacao: Solicitacao) {
        val solicitacaoId = solicitacao.id ?: return
        lifecycleScope.launch {
            solicitacaoRepository.confirmarSolicitacao(solicitacaoId)
                .onSuccess {
                    Toast.makeText(this@TelaCaronasActivity, R.string.solicitacoes_confirmada_sucesso, Toast.LENGTH_SHORT).show()
                    carregarSolicitacoesRecebidas()
                    // Recarrega "Minhas Ofertas" também — a vaga que acabou
                    // de ser ocupada precisa refletir na hora no card da
                    // oferta, não só na lista de solicitações recebidas.
                    carregarOfertas()
                }
                .onFailure { e ->
                    Toast.makeText(this@TelaCaronasActivity, getString(R.string.solicitacoes_erro_confirmar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmarExcluirSolicitacao(solicitacao: Solicitacao) {
        val solicitacaoId = solicitacao.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.solicitacoes_excluir_titulo)
            .setMessage(R.string.solicitacoes_excluir_mensagem)
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    solicitacaoRepository.excluirSolicitacao(solicitacaoId)
                        .onSuccess {
                            Toast.makeText(this@TelaCaronasActivity, R.string.solicitacoes_excluida_sucesso, Toast.LENGTH_SHORT).show()
                            carregarSolicitacoesRecebidas()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@TelaCaronasActivity, getString(R.string.solicitacoes_erro_excluir, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // Motorista desiste de uma viagem que já tinha confirmado — diferente
    // de excluir (apaga o registro sem avisar ninguém), aqui o status vira
    // "cancelada" (mantém no histórico dos dois) e o passageiro recebe uma
    // notificação (ver functions/index.js: notificarViagemCancelada).
    private fun confirmarCancelarComoMotorista(solicitacao: Solicitacao) {
        val solicitacaoId = solicitacao.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.solicitacoes_cancelar_titulo)
            .setMessage(R.string.solicitacoes_cancelar_mensagem)
            .setPositiveButton(R.string.minhas_viagens_botao_cancelar) { _, _ ->
                lifecycleScope.launch {
                    solicitacaoRepository.cancelarComoMotorista(solicitacaoId)
                        .onSuccess {
                            Toast.makeText(this@TelaCaronasActivity, R.string.solicitacoes_cancelada_sucesso, Toast.LENGTH_SHORT).show()
                            carregarSolicitacoesRecebidas()
                            // A vaga liberada precisa refletir na hora no
                            // card da oferta também (mesmo motivo de
                            // confirmarSolicitacao acima).
                            carregarOfertas()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@TelaCaronasActivity, getString(R.string.solicitacoes_erro_cancelar, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // Formulário de edição completo — TODOS os campos que existiam na tela
    // de Oferecer Carona antes de publicar (origem+endereço, paradas
    // intermediárias, destino+endereço, data/hora, vagas, valor), já
    // preenchidos com o que a oferta tem hoje. Antes só dava pra editar
    // data/hora/vagas/valor; rota era fixa depois de publicada.
    private fun abrirDialogEditarOferta(oferta: Carona) {
        if (StatusViagemUtil.jaConcluida(oferta.dataHoraPartida)) {
            Toast.makeText(this, R.string.minhas_ofertas_erro_editar_concluida, Toast.LENGTH_LONG).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_editar_oferta, null)
        val etCidadeOrigem = view.findViewById<EditText>(R.id.etCidadeOrigemEditar)
        val etEnderecoOrigem = view.findViewById<EditText>(R.id.etEnderecoOrigemEditar)
        val containerParadas = view.findViewById<LinearLayout>(R.id.containerParadasEditar)
        val btnAdicionarParada = view.findViewById<TextView>(R.id.btnAdicionarParadaEditar)
        val etCidadeDestino = view.findViewById<EditText>(R.id.etCidadeDestinoEditar)
        val etEnderecoDestino = view.findViewById<EditText>(R.id.etEnderecoDestinoEditar)
        val etData = view.findViewById<EditText>(R.id.etDataEditar)
        val etHora = view.findViewById<EditText>(R.id.etHoraEditar)
        val etVagas = view.findViewById<EditText>(R.id.etVagasEditar)
        val etValor = view.findViewById<EditText>(R.id.etValorEditar)
        val btnSalvar = view.findViewById<Button>(R.id.btnSalvarOferta)

        AutocompleteEnderecoUtil.ligar(this, etCidadeOrigem, autocompleteRepository, TipoAutocomplete.CIDADE)
        AutocompleteEnderecoUtil.ligar(this, etEnderecoOrigem, autocompleteRepository, TipoAutocomplete.ENDERECO, etCidadeOrigem)
        AutocompleteEnderecoUtil.ligar(this, etCidadeDestino, autocompleteRepository, TipoAutocomplete.CIDADE)
        AutocompleteEnderecoUtil.ligar(this, etEnderecoDestino, autocompleteRepository, TipoAutocomplete.ENDERECO, etCidadeDestino)

        // Pré-preenche origem/destino a partir de paradas.first()/last()
        // (fonte da verdade, ver Carona.kt) e insere uma linha por parada
        // intermediária já existente (tudo entre a primeira e a última).
        val paradasAtuais = oferta.paradas
        etCidadeOrigem.setText(paradasAtuais.firstOrNull()?.cidade ?: oferta.cidadeOrigem)
        etEnderecoOrigem.setText(paradasAtuais.firstOrNull()?.endereco)
        etCidadeDestino.setText(paradasAtuais.lastOrNull()?.cidade ?: oferta.cidadeDestino)
        etEnderecoDestino.setText(paradasAtuais.lastOrNull()?.endereco)
        if (paradasAtuais.size > 2) {
            for (parada in paradasAtuais.subList(1, paradasAtuais.size - 1)) {
                adicionarLinhaParadaEditar(containerParadas, parada.cidade, parada.endereco)
            }
        }
        btnAdicionarParada.setOnClickListener { adicionarLinhaParadaEditar(containerParadas, null, null) }

        val calendario = Calendar.getInstance().apply {
            timeInMillis = oferta.dataHoraPartida ?: System.currentTimeMillis()
        }
        etData.setText(formatoDataOferta.format(calendario.time))
        etHora.setText(formatoHoraOferta.format(calendario.time))
        etVagas.setText(oferta.vagas.toString())
        etValor.setText(String.format(Locale("pt", "BR"), "%.2f", oferta.valorPorVaga ?: 0.0))

        etData.setOnClickListener {
            DatePickerDialog(this, { _, ano, mes, dia ->
                calendario.set(Calendar.YEAR, ano)
                calendario.set(Calendar.MONTH, mes)
                calendario.set(Calendar.DAY_OF_MONTH, dia)
                etData.setText(formatoDataOferta.format(calendario.time))
            }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }
        etHora.setOnClickListener {
            TimePickerDialog(this, { _, hora, minuto ->
                calendario.set(Calendar.HOUR_OF_DAY, hora)
                calendario.set(Calendar.MINUTE, minuto)
                etHora.setText(formatoHoraOferta.format(calendario.time))
            }, calendario.get(Calendar.HOUR_OF_DAY), calendario.get(Calendar.MINUTE), true).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        btnSalvar.setOnClickListener {
            val origem = etCidadeOrigem.text.toString().trim()
            val destino = etCidadeDestino.text.toString().trim()
            val vagas = etVagas.text.toString().trim().toIntOrNull()
            val valor = etValor.text.toString().trim().replace(",", ".").toDoubleOrNull()

            if (origem.isEmpty()) {
                etCidadeOrigem.error = getString(R.string.oferecer_erro_origem)
                return@setOnClickListener
            }
            if (destino.isEmpty()) {
                etCidadeDestino.error = getString(R.string.oferecer_erro_destino)
                return@setOnClickListener
            }
            val paradasIntermediarias = mutableListOf<ParadaRota>()
            for (i in 0 until containerParadas.childCount) {
                val linha = containerParadas.getChildAt(i)
                val cidadeParada = linha.findViewById<EditText>(R.id.etCidadeParada).text.toString().trim()
                val enderecoParada = linha.findViewById<EditText>(R.id.etEnderecoParada).text.toString().trim()
                if (cidadeParada.isEmpty()) {
                    Toast.makeText(this, R.string.oferecer_erro_parada_cidade, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                paradasIntermediarias.add(ParadaRota(cidade = cidadeParada, endereco = enderecoParada.ifEmpty { null }))
            }
            if (vagas == null || vagas <= 0) {
                etVagas.error = getString(R.string.oferecer_erro_vagas)
                return@setOnClickListener
            }
            if (valor == null || valor <= 0) {
                etValor.error = getString(R.string.oferecer_erro_valor)
                return@setOnClickListener
            }
            if (calendario.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(this, R.string.oferecer_erro_data_passado, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val caronaId = oferta.id
            if (caronaId == null) {
                dialog.dismiss()
                return@setOnClickListener
            }

            val rota = mutableListOf(ParadaRota(cidade = origem, endereco = etEnderecoOrigem.text.toString().trim().ifEmpty { null }))
            rota.addAll(paradasIntermediarias)
            rota.add(ParadaRota(cidade = destino, endereco = etEnderecoDestino.text.toString().trim().ifEmpty { null }))

            dialog.dismiss()
            lifecycleScope.launch {
                val distanciaKm = withContext(Dispatchers.IO) {
                    runCatching {
                        DistanciaUtil.distanciaAproximadaKm(
                            this@TelaCaronasActivity,
                            DistanciaUtil.pontoParaGeocoding(rota.first()),
                            DistanciaUtil.pontoParaGeocoding(rota.last())
                        )
                    }.getOrNull()
                }
                caronaRepository.atualizarOferta(caronaId, rota, calendario.timeInMillis, vagas, valor, distanciaKm)
                    .onSuccess {
                        Toast.makeText(this@TelaCaronasActivity, R.string.minhas_ofertas_salva_sucesso, Toast.LENGTH_SHORT).show()
                        carregarOfertas()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_ofertas_erro_salvar, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }

        dialog.show()
    }

    // Insere uma linha de parada intermediária no diálogo de edição, igual
    // ao container dinâmico de OferecerCaronaActivity — pré-preenchida
    // quando vem de uma parada já existente, vazia quando é "+ Adicionar
    // parada".
    private fun adicionarLinhaParadaEditar(container: LinearLayout, cidade: String?, endereco: String?) {
        val linha = layoutInflater.inflate(R.layout.item_parada_rota_input, container, false)
        linha.findViewById<EditText>(R.id.etCidadeParada).setText(cidade)
        linha.findViewById<EditText>(R.id.etEnderecoParada).setText(endereco)
        linha.findViewById<TextView>(R.id.btnRemoverParada).setOnClickListener {
            container.removeView(linha)
        }
        val etCidadeParada = linha.findViewById<EditText>(R.id.etCidadeParada)
        AutocompleteEnderecoUtil.ligar(this, etCidadeParada, autocompleteRepository, TipoAutocomplete.CIDADE)
        AutocompleteEnderecoUtil.ligar(this, linha.findViewById(R.id.etEnderecoParada), autocompleteRepository, TipoAutocomplete.ENDERECO, etCidadeParada)
        container.addView(linha)
    }

    private fun confirmarExcluirOferta(oferta: Carona) {
        val caronaId = oferta.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.minhas_ofertas_excluir_titulo)
            .setMessage(R.string.minhas_ofertas_excluir_mensagem)
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    caronaRepository.excluirOferta(caronaId)
                        .onSuccess {
                            Toast.makeText(this@TelaCaronasActivity, R.string.minhas_ofertas_excluida_sucesso, Toast.LENGTH_SHORT).show()
                            carregarOfertas()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_ofertas_erro_excluir, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // comoMotorista = true: passageiro abrindo o perfil de um motorista (busca,
    // Minhas Viagens) — mostra o carro. false: motorista abrindo o perfil de um
    // passageiro (Solicitações Recebidas) — nunca mostra carro, mesmo que essa
    // pessoa também seja motorista.
    private fun abrirPerfilPublico(usuarioId: String, comoMotorista: Boolean) {
        val intent = Intent(this, PerfilPublicoActivity::class.java)
        intent.putExtra(PerfilPublicoActivity.EXTRA_USUARIO_ID, usuarioId)
        intent.putExtra(PerfilPublicoActivity.EXTRA_EXIBIR_COMO_MOTORISTA, comoMotorista)
        startActivity(intent)
    }

    private fun abrirDialogAvaliarMotorista(viagem: Solicitacao) {
        val motoristaId = viagem.motoristaId ?: return
        val nome = viagem.motoristaNome?.trim()?.substringBefore(" ")
            ?: getString(R.string.procurar_motorista_desconhecido)
        AvaliacaoDialogUtil.mostrar(this, nome) { nota, comentario ->
            lifecycleScope.launch {
                avaliacaoRepository.avaliar(viagem, motoristaId, nota, comentario)
                    .onSuccess {
                        Toast.makeText(this@TelaCaronasActivity, R.string.avaliar_sucesso, Toast.LENGTH_SHORT).show()
                        carregarMinhasViagens()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@TelaCaronasActivity, getString(R.string.avaliar_erro_enviar, e.message), Toast.LENGTH_LONG).show()
                    }
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

    // Toque e segure num card de "Minhas Viagens" — apaga o registro de
    // vez (diferente de cancelar, que só muda o status e mantém no
    // histórico), mesmo padrão de confirmarExcluirSolicitacao do lado do
    // motorista. Reusa excluirSolicitacao (já existente), que também apaga
    // o registro de ocupação correspondente.
    private fun confirmarExcluirViagem(viagem: Solicitacao) {
        val solicitacaoId = viagem.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.minhas_viagens_excluir_titulo)
            .setMessage(R.string.minhas_viagens_excluir_mensagem)
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    solicitacaoRepository.excluirSolicitacao(solicitacaoId)
                        .onSuccess {
                            Toast.makeText(this@TelaCaronasActivity, R.string.minhas_viagens_excluida_sucesso, Toast.LENGTH_SHORT).show()
                            carregarMinhasViagens()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@TelaCaronasActivity, getString(R.string.minhas_viagens_erro_excluir, e.message), Toast.LENGTH_LONG).show()
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
        val meuId = usuarioRepository.uidLogado() ?: return
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
                    totalChatNaoLidas = total
                    atualizarBadgeIconeApp()
                }
            }
        }
    }

    // Soma dos dois badges já mostrados dentro do app (Chat + Minhas
    // Ofertas/Minhas Viagens) — é o que aparece em cima do ícone do
    // Caronas na tela inicial (ver AppIconBadgeUtil). Cada um dos dois
    // listeners (escutarBadgeChat/escutarBadgeMinhasOfertasOuViagens)
    // atualiza sua própria metade e chama isso de novo.
    private var totalChatNaoLidas = 0
    private var totalMinhasOfertasOuViagens = 0
    private fun atualizarBadgeIconeApp() {
        AppIconBadgeUtil.atualizar(this, totalChatNaoLidas + totalMinhasOfertasOuViagens)
    }

    private fun solicitarPermissaoNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val jaConcedida = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!jaConcedida) {
            lancadorPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Badge do botão "Minhas Ofertas"/"Minhas Viagens" — a origem da
    // contagem depende do papel: motorista vê solicitações pendentes
    // recebidas, passageiro vê confirmações que ainda não abriu (ver
    // ISolicitacaoRepository.escutarContagemPendentes/
    // escutarContagemConfirmacoesNaoVistas). Chamado de dentro de
    // carregarPerfil, depois que o papel já é conhecido.
    // Guardado por badgeMinhasOfertasIniciado (só roda uma vez, depois de o papel
    // ser conhecido em carregarPerfil) — o aviso RepeatOnLifecycleWrongUsage do
    // lint vem só de carregarPerfil ser chamado no onResume.
    @Suppress("RepeatOnLifecycleWrongUsage")
    private fun escutarBadgeMinhasOfertasOuViagens(motorista: Boolean) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val contagem = if (motorista) {
                    solicitacaoRepository.escutarContagemPendentes()
                } else {
                    solicitacaoRepository.escutarContagemConfirmacoesNaoVistas()
                }
                contagem.catch { }.collect { total ->
                    if (total > 0) {
                        badgeMinhasOfertasOuViagens.visibility = View.VISIBLE
                        badgeMinhasOfertasOuViagens.text = total.toString()
                    } else {
                        badgeMinhasOfertasOuViagens.visibility = View.GONE
                    }
                    totalMinhasOfertasOuViagens = total
                    atualizarBadgeIconeApp()
                }
            }
        }
    }

    // Dois contadorezinhos acima do "Sair", só pro motorista (ver
    // AcessoMotoristaUtil/Usuario.caronasOferecidas): quantas das 10
    // gratuitas já usou (ou, se já pagou, até quando vale o acesso) e
    // quantas caronas já de fato aconteceram (data já passada — reusa
    // buscarMinhasOfertas, que "Minhas Ofertas" já carrega de qualquer
    // forma, em vez de outra consulta nova só pra isso).
    private fun atualizarContadoresMotorista(usuario: Usuario) {
        if (usuario.motorista) {
            // "Grátis: X/10" (ou "Acesso pago até..." depois de pagar) só
            // existe pro motorista — é quem tem o limite de 10 caronas
            // gratuitas (ver AcessoMotoristaUtil).
            tvContadorViagensGratis.visibility = View.VISIBLE
            tvContadorViagensGratis.text = AcessoMotoristaUtil.textoStatus(this, usuario)

            lifecycleScope.launch {
                caronaRepository.buscarMinhasOfertas().onSuccess { ofertas ->
                    val agora = System.currentTimeMillis()
                    val realizadas = ofertas.count { (it.dataHoraPartida ?: Long.MAX_VALUE) < agora }
                    tvContadorViagensRealizadas.visibility = View.VISIBLE
                    tvContadorViagensRealizadas.text = getString(R.string.tela_contador_viagens_realizadas_formato, realizadas)
                }
            }
        } else {
            // Passageiro não tem limite/cobrança — só o contador de
            // "Realizadas", baseado nas solicitações CONFIRMADAS cuja data
            // já passou (mesma ideia de MinhaViagemAdapter pro "já ocorreu").
            tvContadorViagensGratis.visibility = View.GONE

            lifecycleScope.launch {
                solicitacaoRepository.buscarMinhasSolicitacoes().onSuccess { solicitacoes ->
                    val agora = System.currentTimeMillis()
                    val realizadas = solicitacoes.count {
                        it.status == "confirmada" && (it.dataHoraPartida ?: Long.MAX_VALUE) < agora
                    }
                    tvContadorViagensRealizadas.visibility = View.VISIBLE
                    tvContadorViagensRealizadas.text = getString(R.string.tela_contador_viagens_realizadas_formato, realizadas)
                }
            }
        }
    }

    private fun carregarPerfil() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                usuarioAtual = usuario
                falhasRepository.definirUsuario(usuario.id)
                notificacaoRepository.atualizarTokenUsuario(usuario.id)
                if (!badgeMinhasOfertasIniciado) {
                    badgeMinhasOfertasIniciado = true
                    escutarBadgeMinhasOfertasOuViagens(usuario.motorista)
                }
                tvPapelUsuario.text = getString(
                    if (usuario.motorista) R.string.tela_papel_motorista else R.string.tela_papel_passageiro
                )
                tvNomeUsuario.text = usuario.nomeCompleto?.trim()?.substringBefore(" ") ?: ""

                // Mesmo botão, texto e destino diferentes conforme o papel
                // atual — motorista vê as ofertas que publicou, passageiro
                // vê as viagens que solicitou.
                if (usuario.motorista) {
                    btnMinhasOfertasOuViagens.text = getString(R.string.tela_botao_minhas_ofertas)
                    btnMinhasOfertasOuViagens.setOnClickListener { toggleMinhasOfertas() }
                } else {
                    btnMinhasOfertasOuViagens.text = getString(R.string.tela_botao_minhas_viagens)
                    btnMinhasOfertasOuViagens.setOnClickListener { toggleMinhasViagens() }
                }

                // Motorista oferece, passageiro procura — não faz sentido
                // mostrar os dois pro mesmo papel.
                btnOferecer.visibility = if (usuario.motorista) View.VISIBLE else View.GONE
                btnProcurar.visibility = if (usuario.motorista) View.GONE else View.VISIBLE
                btnPagamentos.visibility = if (usuario.motorista) View.VISIBLE else View.GONE

                atualizarContadoresMotorista(usuario)
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

    private companion object {
        // Abas de "Minhas Ofertas" (ver selecionarAbaOfertas).
        const val ABA_OFERTAS = 0
        const val ABA_RECEBIDAS = 1
    }
}

package com.cjstudio.caronas

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Painel de relatórios administrativos do Caronas — reúne, numa tela só,
// métricas de usuários, viagens, financeiro, avaliações, manifestações
// (reclamações/sugestões/denúncias) e administração, com filtro de período
// e exportação em PDF. Adaptado do RelatoriosActivity do Match: mesma
// estrutura (spinner de tipo + spinner de período + cards), mas usando o
// PdfBuilder nativo do Caronas (ver PdfUtil.kt/gerarPdfRelatorioMensalFinanceiro
// em AdministracaoCaronasActivity) em vez do WebView+PrintManager do Match.
//
// Aberta a partir de Configurações (modo admin) — ver ConfiguracoesCaronasActivity
// — não da tela de Administração (essa continua com suas seções inline, ver
// feedback-caronas-admin-secao-inline); "Relatórios" é uma tela nova de
// verdade, mesmo padrão de navegação que Termos/Orientações já usam ali.
@AndroidEntryPoint
class RelatoriosCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    @Inject
    lateinit var avaliacaoRepository: IAvaliacaoRepository

    private lateinit var spinnerTipoRelatorio: Spinner
    private lateinit var spinnerPeriodo: Spinner
    private lateinit var btnFiltrar: Button
    private lateinit var btnGerarPdf: Button
    private lateinit var btnVoltar: Button
    private lateinit var etDataInicial: EditText
    private lateinit var etDataFinal: EditText
    private lateinit var layoutDataPersonalizada: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutRelatorios: LinearLayout

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val sdfCompleto = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    private var ultimoRelatorio: RelatorioDados? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relatorios_caronas)

        spinnerTipoRelatorio = findViewById(R.id.spinnerTipoRelatorio)
        spinnerPeriodo = findViewById(R.id.spinnerPeriodo)
        btnFiltrar = findViewById(R.id.btnFiltrar)
        btnGerarPdf = findViewById(R.id.btnGerarPdf)
        btnVoltar = findViewById(R.id.btnVoltar)
        etDataInicial = findViewById(R.id.etDataInicial)
        etDataFinal = findViewById(R.id.etDataFinal)
        layoutDataPersonalizada = findViewById(R.id.layoutDataPersonalizada)
        progressBar = findViewById(R.id.progressBar)
        layoutRelatorios = findViewById(R.id.layoutRelatorios)

        aplicarMascaraData(etDataInicial)
        aplicarMascaraData(etDataFinal)

        spinnerTipoRelatorio.setSelection(TIPO_COMPLETO)
        spinnerTipoRelatorio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                ultimoRelatorio?.let { exibirRelatorio(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerPeriodo.setSelection(PERIODO_TODOS)
        spinnerPeriodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutDataPersonalizada.visibility = if (position == PERIODO_PERSONALIZADO) View.VISIBLE else View.GONE
                if (position != PERIODO_PERSONALIZADO) {
                    carregarRelatorios()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                layoutDataPersonalizada.visibility = View.GONE
            }
        }

        btnFiltrar.setOnClickListener { carregarRelatorios() }
        btnGerarPdf.setOnClickListener { gerarPdf() }
        btnVoltar.setOnClickListener { finish() }

        carregarRelatorios()
    }

    private fun aplicarMascaraData(editText: EditText) {
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

    /** Retorna [inicioMillis, fimMillis] da janela de período selecionada, ou null se inválida (já avisou o usuário). */
    private fun calcularJanelaPeriodo(): LongArray? {
        val periodoSelecionado = spinnerPeriodo.selectedItemPosition
        val agora = System.currentTimeMillis()

        if (periodoSelecionado == PERIODO_TODOS) {
            return longArrayOf(0L, Long.MAX_VALUE)
        }

        if (periodoSelecionado == PERIODO_PERSONALIZADO) {
            val dataInicialStr = etDataInicial.text.toString().trim()
            val dataFinalStr = etDataFinal.text.toString().trim()
            if (TextUtils.isEmpty(dataInicialStr) || TextUtils.isEmpty(dataFinalStr)) {
                Toast.makeText(this, R.string.relatorios_preencha_datas, Toast.LENGTH_SHORT).show()
                return null
            }
            return try {
                val dataInicial = sdf.parse(dataInicialStr)
                val dataFinal = sdf.parse(dataFinalStr)
                if (dataInicial == null || dataFinal == null) {
                    Toast.makeText(this, R.string.relatorios_erro_data_invalida, Toast.LENGTH_SHORT).show()
                    return null
                }
                if (dataInicial.after(dataFinal)) {
                    Toast.makeText(this, R.string.relatorios_erro_data_maior, Toast.LENGTH_SHORT).show()
                    return null
                }
                longArrayOf(dataInicial.time, dataFinal.time + 24 * 60 * 60 * 1000 - 1)
            } catch (e: ParseException) {
                Toast.makeText(this, R.string.relatorios_erro_data_invalida, Toast.LENGTH_SHORT).show()
                null
            }
        }

        val inicioMillis = when (periodoSelecionado) {
            PERIODO_DIARIO -> agora - 1L * 24 * 60 * 60 * 1000
            PERIODO_SEMANAL -> agora - 7L * 24 * 60 * 60 * 1000
            PERIODO_MENSAL -> agora - 30L * 24 * 60 * 60 * 1000
            PERIODO_TRIMESTRAL -> agora - 90L * 24 * 60 * 60 * 1000
            PERIODO_SEMESTRAL -> agora - 180L * 24 * 60 * 60 * 1000
            PERIODO_ANUAL -> agora - 365L * 24 * 60 * 60 * 1000
            else -> 0L
        }
        return longArrayOf(inicioMillis, agora)
    }

    private fun carregarRelatorios() {
        val janela = calcularJanelaPeriodo() ?: return
        val inicio = janela[0]
        val fim = janela[1]
        val periodoDescricao = spinnerPeriodo.selectedItem?.toString() ?: ""

        progressBar.visibility = View.VISIBLE
        layoutRelatorios.removeAllViews()

        lifecycleScope.launch {
            try {
                val usuarios = adminRepository.listarTodosUsuarios().getOrThrow()
                val caronas = adminRepository.listarTodasCaronas().getOrThrow()
                val solicitacoes = adminRepository.listarTodasSolicitacoes().getOrThrow()
                val pagamentos = adminRepository.listarPagamentosMotorista().getOrThrow()
                val avaliacoes = avaliacaoRepository.listarTodasAvaliacoes().getOrThrow()
                val manifestacoes = adminRepository.listarManifestacoes().getOrThrow()
                val manifestacoesArquivadas = adminRepository.listarManifestacoesArquivadas().getOrThrow()
                val admins = adminRepository.listarTodosAdmins().getOrThrow()

                val dados = calcularRelatorio(
                    inicio, fim, periodoDescricao,
                    usuarios, caronas, solicitacoes, pagamentos, avaliacoes,
                    manifestacoes + manifestacoesArquivadas, admins
                )
                ultimoRelatorio = dados
                exibirRelatorio(dados)
            } catch (e: Exception) {
                Toast.makeText(this@RelatoriosCaronasActivity, getString(R.string.relatorios_erro_carregar, e.message), Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun calcularRelatorio(
        inicio: Long,
        fim: Long,
        periodoDescricao: String,
        usuarios: List<Usuario>,
        caronas: List<Carona>,
        solicitacoes: List<Solicitacao>,
        pagamentos: List<PagamentoMotorista>,
        avaliacoes: List<Avaliacao>,
        manifestacoes: List<Manifestacao>,
        admins: List<Admin>
    ): RelatorioDados {
        // ----- Usuários -----
        var novosUsuariosNoPeriodo = 0
        var totalMotoristas = 0
        for (u in usuarios) {
            if (u.veiculo?.estaPreenchido() == true) totalMotoristas++
            val criadoEmMillis = u.criadoEm?.time
            if (criadoEmMillis != null && criadoEmMillis in inicio..fim) novosUsuariosNoPeriodo++
        }

        // ----- Viagens (ofertas do motorista) -----
        var caronasNoPeriodo = 0
        var caronasAtivas = 0
        var caronasCanceladas = 0
        var caronasConcluidas = 0
        for (c in caronas) {
            val criadoEmMillis = c.criadoEm?.time
            if (criadoEmMillis != null && criadoEmMillis in inicio..fim) caronasNoPeriodo++
            when (c.status) {
                "cancelada" -> caronasCanceladas++
                else -> if (StatusViagemUtil.jaConcluida(c.dataHoraPartida)) caronasConcluidas++ else caronasAtivas++
            }
        }

        // ----- Solicitações (viagens do passageiro) -----
        var solicitacoesNoPeriodo = 0
        var solicitacoesConfirmadas = 0
        var solicitacoesCanceladas = 0
        for (s in solicitacoes) {
            val criadoEmMillis = s.criadoEm?.time
            if (criadoEmMillis != null && criadoEmMillis in inicio..fim) solicitacoesNoPeriodo++
            when (s.status) {
                "confirmada" -> solicitacoesConfirmadas++
                "cancelada" -> solicitacoesCanceladas++
            }
        }

        // ----- Financeiro (mesma regra de AdministracaoCaronasActivity.mostrarFinanceiro: estornados fora do total) -----
        val pagamentosValidos = pagamentos.filter { !it.estornado }
        val pagamentosEstornados = pagamentos.filter { it.estornado }
        var totalArrecadado = 0.0
        var pagamentosNoPeriodo = 0
        val motoristasPagantes = mutableSetOf<String>()
        for (p in pagamentosValidos) {
            totalArrecadado += p.valor
            p.usuarioId?.let { if (it.isNotEmpty()) motoristasPagantes.add(it) }
            val dataCompra = p.dataCompra
            if (dataCompra != null && dataCompra in inicio..fim) pagamentosNoPeriodo++
        }
        val totalEstornado = pagamentosEstornados.sumOf { it.valor }

        // ----- Avaliações -----
        var avaliacoesNoPeriodo = 0
        var somaNotas = 0
        for (a in avaliacoes) {
            somaNotas += a.nota
            val criadoEmMillis = a.criadoEm?.time
            if (criadoEmMillis != null && criadoEmMillis in inicio..fim) avaliacoesNoPeriodo++
        }
        val mediaGeral = if (avaliacoes.isNotEmpty()) somaNotas.toDouble() / avaliacoes.size else 0.0

        // ----- Manifestações (reclamações/sugestões/denúncias) -----
        var manifestacoesNoPeriodo = 0
        var totalReclamacoes = 0
        var totalSugestoes = 0
        var totalDenuncias = 0
        var manifestacoesRespondidas = 0
        var manifestacoesPendentes = 0
        var manifestacoesArquivadas = 0
        for (m in manifestacoes) {
            val criadoEmMillis = m.criadoEm?.time
            if (criadoEmMillis != null && criadoEmMillis in inicio..fim) manifestacoesNoPeriodo++
            when (m.tipo) {
                Manifestacao.TIPO_RECLAMACAO -> totalReclamacoes++
                Manifestacao.TIPO_SUGESTAO -> totalSugestoes++
                Manifestacao.TIPO_DENUNCIA -> totalDenuncias++
            }
            if (m.status == Manifestacao.STATUS_RESPONDIDO) manifestacoesRespondidas++ else manifestacoesPendentes++
            if (m.arquivado) manifestacoesArquivadas++
        }

        return RelatorioDados(
            geradoEm = Date(),
            periodoDescricao = periodoDescricao,
            totalUsuarios = usuarios.size,
            novosUsuariosNoPeriodo = novosUsuariosNoPeriodo,
            totalMotoristas = totalMotoristas,
            totalPassageiros = usuarios.size,
            totalCaronasOfertadas = caronas.size,
            caronasNoPeriodo = caronasNoPeriodo,
            caronasAtivas = caronasAtivas,
            caronasCanceladas = caronasCanceladas,
            caronasConcluidas = caronasConcluidas,
            totalSolicitacoes = solicitacoes.size,
            solicitacoesNoPeriodo = solicitacoesNoPeriodo,
            solicitacoesConfirmadas = solicitacoesConfirmadas,
            solicitacoesCanceladas = solicitacoesCanceladas,
            totalArrecadado = totalArrecadado,
            motoristasPagantes = motoristasPagantes.size,
            pagamentosNoPeriodo = pagamentosNoPeriodo,
            totalEstornos = pagamentosEstornados.size,
            valorEstornado = totalEstornado,
            totalAvaliacoes = avaliacoes.size,
            avaliacoesNoPeriodo = avaliacoesNoPeriodo,
            mediaGeralAvaliacoes = mediaGeral,
            totalManifestacoes = manifestacoes.size,
            manifestacoesNoPeriodo = manifestacoesNoPeriodo,
            totalReclamacoes = totalReclamacoes,
            totalSugestoes = totalSugestoes,
            totalDenuncias = totalDenuncias,
            manifestacoesRespondidas = manifestacoesRespondidas,
            manifestacoesPendentes = manifestacoesPendentes,
            manifestacoesArquivadas = manifestacoesArquivadas,
            totalAdmins = admins.size
        )
    }

    // ===================== EXIBIÇÃO NA TELA =====================

    private fun exibirRelatorio(d: RelatorioDados) {
        layoutRelatorios.removeAllViews()
        val tipo = spinnerTipoRelatorio.selectedItemPosition

        if (tipo == TIPO_COMPLETO || tipo == TIPO_USUARIOS) {
            adicionarSecao(getString(R.string.relatorios_secao_usuarios), listOf(
                getString(R.string.relatorios_total_usuarios) to d.totalUsuarios.toString(),
                getString(R.string.relatorios_novos_periodo) to d.novosUsuariosNoPeriodo.toString(),
                getString(R.string.relatorios_total_motoristas) to d.totalMotoristas.toString(),
                getString(R.string.relatorios_total_passageiros) to d.totalPassageiros.toString()
            ))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_VIAGENS) {
            adicionarSecao(getString(R.string.relatorios_secao_viagens), listOf(
                getString(R.string.relatorios_viagens_total_ofertadas) to d.totalCaronasOfertadas.toString(),
                getString(R.string.relatorios_viagens_ofertadas_periodo) to d.caronasNoPeriodo.toString(),
                getString(R.string.relatorios_viagens_ativas) to d.caronasAtivas.toString(),
                getString(R.string.relatorios_viagens_canceladas) to d.caronasCanceladas.toString(),
                getString(R.string.relatorios_viagens_concluidas) to d.caronasConcluidas.toString(),
                getString(R.string.relatorios_viagens_total_solicitacoes) to d.totalSolicitacoes.toString(),
                getString(R.string.relatorios_viagens_solicitacoes_periodo) to d.solicitacoesNoPeriodo.toString(),
                getString(R.string.relatorios_viagens_solicitacoes_confirmadas) to d.solicitacoesConfirmadas.toString(),
                getString(R.string.relatorios_viagens_solicitacoes_canceladas) to d.solicitacoesCanceladas.toString()
            ))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_FINANCEIRO) {
            adicionarSecao(getString(R.string.relatorios_secao_financeiro), listOf(
                getString(R.string.relatorios_financeiro_total_arrecadado) to formatarValor(d.totalArrecadado),
                getString(R.string.relatorios_financeiro_motoristas_pagantes) to d.motoristasPagantes.toString(),
                getString(R.string.relatorios_financeiro_pagamentos_periodo) to d.pagamentosNoPeriodo.toString(),
                getString(R.string.relatorios_financeiro_estornos_qtd) to d.totalEstornos.toString(),
                getString(R.string.relatorios_financeiro_estornos_valor) to formatarValor(d.valorEstornado)
            ))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_AVALIACOES) {
            adicionarSecao(getString(R.string.relatorios_secao_avaliacoes), listOf(
                getString(R.string.relatorios_avaliacoes_total) to d.totalAvaliacoes.toString(),
                getString(R.string.relatorios_avaliacoes_periodo) to d.avaliacoesNoPeriodo.toString(),
                getString(R.string.relatorios_avaliacoes_media) to String.format(Locale("pt", "BR"), "%.1f", d.mediaGeralAvaliacoes)
            ))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_MANIFESTACOES) {
            adicionarSecao(getString(R.string.relatorios_secao_manifestacoes), listOf(
                getString(R.string.relatorios_manifestacoes_total) to d.totalManifestacoes.toString(),
                getString(R.string.relatorios_manifestacoes_periodo) to d.manifestacoesNoPeriodo.toString(),
                getString(R.string.relatorios_manifestacoes_reclamacoes) to d.totalReclamacoes.toString(),
                getString(R.string.relatorios_manifestacoes_sugestoes) to d.totalSugestoes.toString(),
                getString(R.string.relatorios_manifestacoes_denuncias) to d.totalDenuncias.toString(),
                getString(R.string.relatorios_manifestacoes_respondidas) to d.manifestacoesRespondidas.toString(),
                getString(R.string.relatorios_manifestacoes_pendentes) to d.manifestacoesPendentes.toString(),
                getString(R.string.relatorios_manifestacoes_arquivadas) to d.manifestacoesArquivadas.toString()
            ))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_ADMINISTRACAO) {
            adicionarSecao(getString(R.string.relatorios_secao_administracao), listOf(
                getString(R.string.relatorios_total_admins) to d.totalAdmins.toString()
            ))
        }

        val tvGeradoEm = TextView(this)
        tvGeradoEm.text = getString(R.string.relatorios_gerado_em, sdfCompleto.format(d.geradoEm))
        tvGeradoEm.setTextColor(Color.parseColor("#999999"))
        tvGeradoEm.textSize = 12f
        tvGeradoEm.gravity = Gravity.END
        tvGeradoEm.setPadding(0, 8, 0, 0)
        layoutRelatorios.addView(tvGeradoEm)
    }

    private fun adicionarSecao(titulo: String, linhas: List<Pair<String, String>>) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val padding = (14 * resources.displayMetrics.density).toInt()
        card.setPadding(padding, padding, padding, padding)
        card.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 12 * resources.displayMetrics.density
        }
        val paramsCard = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        paramsCard.bottomMargin = (12 * resources.displayMetrics.density).toInt()
        card.layoutParams = paramsCard

        val tvTitulo = TextView(this)
        tvTitulo.text = titulo
        tvTitulo.setTextColor(Color.parseColor("#0D47A1"))
        tvTitulo.textSize = 16f
        tvTitulo.setTypeface(tvTitulo.typeface, Typeface.BOLD)
        card.addView(tvTitulo)

        val corpo = linhas.joinToString("\n") { "${it.first}: ${it.second}" }
        val tvCorpo = TextView(this)
        tvCorpo.text = corpo
        tvCorpo.setTextColor(Color.parseColor("#222222"))
        tvCorpo.textSize = 14f
        val paramsCorpo = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        paramsCorpo.topMargin = (6 * resources.displayMetrics.density).toInt()
        tvCorpo.layoutParams = paramsCorpo
        card.addView(tvCorpo)

        layoutRelatorios.addView(card)
    }

    private fun formatarValor(v: Double): String = getString(R.string.financeiro_valor_format, v)

    // ===================== EXPORTAÇÃO EM PDF =====================
    // PdfBuilder nativo (ver PdfUtil.kt) — mesmo utilitário já usado pelo
    // relatório mensal de Financeiro no dashboard admin, sem WebView nem
    // diálogo de impressão do sistema.

    private fun gerarPdf() {
        val d = ultimoRelatorio
        if (d == null) {
            Toast.makeText(this, R.string.relatorios_gerar_pdf_sem_dados, Toast.LENGTH_SHORT).show()
            return
        }

        val tipo = spinnerTipoRelatorio.selectedItemPosition
        val pdf = PdfBuilder(this)
        pdf.titulo(getString(R.string.relatorios_pdf_titulo))
        pdf.subtitulo(spinnerTipoRelatorio.selectedItem?.toString() ?: "")
        pdf.subtitulo("${getString(R.string.relatorios_periodo_prefixo)}: ${d.periodoDescricao}")

        if (tipo == TIPO_COMPLETO || tipo == TIPO_USUARIOS) {
            pdf.secao(getString(R.string.relatorios_secao_usuarios))
            pdf.chaveValor(getString(R.string.relatorios_total_usuarios), d.totalUsuarios.toString())
            pdf.chaveValor(getString(R.string.relatorios_novos_periodo), d.novosUsuariosNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_total_motoristas), d.totalMotoristas.toString())
            pdf.chaveValor(getString(R.string.relatorios_total_passageiros), d.totalPassageiros.toString())
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_VIAGENS) {
            pdf.secao(getString(R.string.relatorios_secao_viagens))
            pdf.chaveValor(getString(R.string.relatorios_viagens_total_ofertadas), d.totalCaronasOfertadas.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_ofertadas_periodo), d.caronasNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_ativas), d.caronasAtivas.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_canceladas), d.caronasCanceladas.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_concluidas), d.caronasConcluidas.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_total_solicitacoes), d.totalSolicitacoes.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_solicitacoes_periodo), d.solicitacoesNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_solicitacoes_confirmadas), d.solicitacoesConfirmadas.toString())
            pdf.chaveValor(getString(R.string.relatorios_viagens_solicitacoes_canceladas), d.solicitacoesCanceladas.toString())
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_FINANCEIRO) {
            pdf.secao(getString(R.string.relatorios_secao_financeiro))
            pdf.chaveValor(getString(R.string.relatorios_financeiro_total_arrecadado), formatarValor(d.totalArrecadado))
            pdf.chaveValor(getString(R.string.relatorios_financeiro_motoristas_pagantes), d.motoristasPagantes.toString())
            pdf.chaveValor(getString(R.string.relatorios_financeiro_pagamentos_periodo), d.pagamentosNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_financeiro_estornos_qtd), d.totalEstornos.toString())
            pdf.chaveValor(getString(R.string.relatorios_financeiro_estornos_valor), formatarValor(d.valorEstornado))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_AVALIACOES) {
            pdf.secao(getString(R.string.relatorios_secao_avaliacoes))
            pdf.chaveValor(getString(R.string.relatorios_avaliacoes_total), d.totalAvaliacoes.toString())
            pdf.chaveValor(getString(R.string.relatorios_avaliacoes_periodo), d.avaliacoesNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_avaliacoes_media), String.format(Locale("pt", "BR"), "%.1f", d.mediaGeralAvaliacoes))
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_MANIFESTACOES) {
            pdf.secao(getString(R.string.relatorios_secao_manifestacoes))
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_total), d.totalManifestacoes.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_periodo), d.manifestacoesNoPeriodo.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_reclamacoes), d.totalReclamacoes.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_sugestoes), d.totalSugestoes.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_denuncias), d.totalDenuncias.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_respondidas), d.manifestacoesRespondidas.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_pendentes), d.manifestacoesPendentes.toString())
            pdf.chaveValor(getString(R.string.relatorios_manifestacoes_arquivadas), d.manifestacoesArquivadas.toString())
        }

        if (tipo == TIPO_COMPLETO || tipo == TIPO_ADMINISTRACAO) {
            pdf.secao(getString(R.string.relatorios_secao_administracao))
            pdf.chaveValor(getString(R.string.relatorios_total_admins), d.totalAdmins.toString())
        }

        pdf.rodape(getString(R.string.relatorios_gerado_em, sdfCompleto.format(d.geradoEm)))
        pdf.gerarEAbrir(getString(R.string.relatorios_pdf_nome_arquivo))
    }

    private data class RelatorioDados(
        val geradoEm: Date,
        val periodoDescricao: String,
        val totalUsuarios: Int,
        val novosUsuariosNoPeriodo: Int,
        val totalMotoristas: Int,
        val totalPassageiros: Int,
        val totalCaronasOfertadas: Int,
        val caronasNoPeriodo: Int,
        val caronasAtivas: Int,
        val caronasCanceladas: Int,
        val caronasConcluidas: Int,
        val totalSolicitacoes: Int,
        val solicitacoesNoPeriodo: Int,
        val solicitacoesConfirmadas: Int,
        val solicitacoesCanceladas: Int,
        val totalArrecadado: Double,
        val motoristasPagantes: Int,
        val pagamentosNoPeriodo: Int,
        val totalEstornos: Int,
        val valorEstornado: Double,
        val totalAvaliacoes: Int,
        val avaliacoesNoPeriodo: Int,
        val mediaGeralAvaliacoes: Double,
        val totalManifestacoes: Int,
        val manifestacoesNoPeriodo: Int,
        val totalReclamacoes: Int,
        val totalSugestoes: Int,
        val totalDenuncias: Int,
        val manifestacoesRespondidas: Int,
        val manifestacoesPendentes: Int,
        val manifestacoesArquivadas: Int,
        val totalAdmins: Int
    )

    companion object {
        private const val PERIODO_TODOS = 0
        private const val PERIODO_DIARIO = 1
        private const val PERIODO_SEMANAL = 2
        private const val PERIODO_MENSAL = 3
        private const val PERIODO_TRIMESTRAL = 4
        private const val PERIODO_SEMESTRAL = 5
        private const val PERIODO_ANUAL = 6
        private const val PERIODO_PERSONALIZADO = 7

        // Posições devem bater exatamente com R.array.tipos_relatorio_caronas_array
        private const val TIPO_COMPLETO = 0
        private const val TIPO_USUARIOS = 1
        private const val TIPO_VIAGENS = 2
        private const val TIPO_FINANCEIRO = 3
        private const val TIPO_AVALIACOES = 4
        private const val TIPO_MANIFESTACOES = 5
        private const val TIPO_ADMINISTRACAO = 6
    }
}

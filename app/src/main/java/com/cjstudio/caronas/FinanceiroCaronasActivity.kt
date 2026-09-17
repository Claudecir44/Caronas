package com.cjstudio.caronas

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Tela "Financeiro" do painel admin — histórico de cobranças dos R$15,99/30
// dias do motorista (ver PagamentoMotorista.kt, concederAcessoMotorista em
// functions/index.js), adaptada do RelatoriosFinanceirosActivity do Match:
// mesmo filtro de período + relatório mensal em PDF, mas sem os cards por
// "plano" (Match tem Mensal/Trimestral; aqui é um valor fixo só, R$15,99) e
// sem CPF no PDF (Caronas não coleta CPF de motorista/passageiro comum, só
// de admin — ver Usuario.kt).
@AndroidEntryPoint
class FinanceiroCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var spinnerPeriodo: Spinner
    private lateinit var btnFiltrar: Button
    private lateinit var btnGerarRelatorio: Button
    private lateinit var etDataInicial: EditText
    private lateinit var etDataFinal: EditText
    private lateinit var layoutDataPersonalizada: LinearLayout
    private lateinit var tvTotalPagamentos: TextView
    private lateinit var tvMotoristasUnicos: TextView
    private lateinit var tvTotalArrecadado: TextView
    private lateinit var tvVazio: TextView
    private lateinit var rvPagamentos: RecyclerView
    private lateinit var adapter: PagamentoAdapter
    private lateinit var listaPagamentos: MutableList<ItemPagamento>
    private var todosPagamentos: List<PagamentoMotorista> = emptyList()

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val sdfCompleto = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_financeiro_caronas)

        spinnerPeriodo = findViewById(R.id.spinnerPeriodo)
        btnFiltrar = findViewById(R.id.btnFiltrar)
        btnGerarRelatorio = findViewById(R.id.btnGerarRelatorio)
        etDataInicial = findViewById(R.id.etDataInicial)
        etDataFinal = findViewById(R.id.etDataFinal)
        layoutDataPersonalizada = findViewById(R.id.layoutDataPersonalizada)
        tvTotalPagamentos = findViewById(R.id.tvTotalPagamentos)
        tvMotoristasUnicos = findViewById(R.id.tvMotoristasUnicos)
        tvTotalArrecadado = findViewById(R.id.tvTotalArrecadado)
        rvPagamentos = findViewById(R.id.rvPagamentos)
        tvVazio = findViewById(R.id.tvVazio)

        rvPagamentos.layoutManager = LinearLayoutManager(this)
        listaPagamentos = ArrayList()
        adapter = PagamentoAdapter(listaPagamentos)
        rvPagamentos.adapter = adapter

        aplicarMascaraData(etDataInicial)
        aplicarMascaraData(etDataFinal)

        val spinnerAdapter = ArrayAdapter.createFromResource(
            this, R.array.periodos_array, android.R.layout.simple_spinner_item
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriodo.adapter = spinnerAdapter
        spinnerPeriodo.setSelection(PERIODO_TODOS)

        spinnerPeriodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutDataPersonalizada.visibility = if (position == PERIODO_PERSONALIZADO) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                layoutDataPersonalizada.visibility = View.GONE
            }
        }

        btnFiltrar.setOnClickListener { aplicarFiltroEExibir() }
        btnGerarRelatorio.setOnClickListener { mostrarDialogMesAno() }

        carregarDados()
    }

    private fun carregarDados() {
        Toast.makeText(this, R.string.financeiro_carregando_dados, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            adminRepository.listarPagamentosMotorista()
                .onSuccess { pagamentos ->
                    todosPagamentos = pagamentos
                    aplicarFiltroEExibir()
                }
                .onFailure { e ->
                    Toast.makeText(this@FinanceiroCaronasActivity, getString(R.string.financeiro_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    // ===================== FILTRO DE PERÍODO =====================

    private fun aplicarFiltroEExibir() {
        val periodoSelecionado = spinnerPeriodo.selectedItemPosition
        val agora = System.currentTimeMillis()
        var dataInicialMillis = 0L
        var dataFinalMillis = Long.MAX_VALUE
        var dataLimite = 0L

        if (periodoSelecionado == PERIODO_PERSONALIZADO) {
            val dataInicialStr = etDataInicial.text.toString().trim()
            val dataFinalStr = etDataFinal.text.toString().trim()

            if (TextUtils.isEmpty(dataInicialStr) || TextUtils.isEmpty(dataFinalStr)) {
                Toast.makeText(this, R.string.financeiro_preencha_datas, Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val dataInicial = sdf.parse(dataInicialStr)
                val dataFinal = sdf.parse(dataFinalStr)

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
                PERIODO_DIARIO -> agora - 1L * 24 * 60 * 60 * 1000
                PERIODO_SEMANAL -> agora - 7L * 24 * 60 * 60 * 1000
                PERIODO_MENSAL -> agora - 30L * 24 * 60 * 60 * 1000
                PERIODO_TRIMESTRAL -> agora - 90L * 24 * 60 * 60 * 1000
                PERIODO_SEMESTRAL -> agora - 180L * 24 * 60 * 60 * 1000
                PERIODO_ANUAL -> agora - 365L * 24 * 60 * 60 * 1000
                else -> 0L
            }
        }

        listaPagamentos.clear()
        var totalPagamentos = 0
        var totalArrecadado = 0.0
        val motoristasUnicos = HashSet<String>()

        for (p in todosPagamentos) {
            val dataCompra = p.dataCompra ?: continue

            if (periodoSelecionado == PERIODO_PERSONALIZADO) {
                if (dataCompra < dataInicialMillis || dataCompra > dataFinalMillis) continue
            } else {
                if (dataLimite > 0 && dataCompra < dataLimite) continue
            }

            val ativo = p.expiraEm != null && p.expiraEm!! > agora
            totalPagamentos++
            totalArrecadado += p.valor
            p.usuarioId?.let { if (it.isNotEmpty()) motoristasUnicos.add(it) }

            listaPagamentos.add(
                ItemPagamento(
                    nome = p.usuarioNome?.ifEmpty { null } ?: getString(R.string.financeiro_motorista_padrao),
                    email = p.usuarioEmail ?: "",
                    valor = p.valor,
                    dataCompra = Date(dataCompra),
                    expiraEm = p.expiraEm?.let { Date(it) },
                    ativo = ativo
                )
            )
        }

        tvTotalPagamentos.text = totalPagamentos.toString()
        tvMotoristasUnicos.text = motoristasUnicos.size.toString()
        tvTotalArrecadado.text = getString(R.string.financeiro_valor_format, totalArrecadado)

        listaPagamentos.sortWith { a, b -> b.dataCompra.compareTo(a.dataCompra) }
        adapter.notifyDataSetChanged()

        if (listaPagamentos.isEmpty()) {
            tvVazio.visibility = View.VISIBLE
            rvPagamentos.visibility = View.GONE
        } else {
            tvVazio.visibility = View.GONE
            rvPagamentos.visibility = View.VISIBLE
        }
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

    // ===================== RELATÓRIO DE PAGAMENTOS (PDF) =====================

    private fun mostrarDialogMesAno() {
        val meses = resources.getStringArray(R.array.meses_array)
        val calendarAgora = Calendar.getInstance()
        val anoAtual = calendarAgora.get(Calendar.YEAR)
        val anos = (anoAtual downTo anoAtual - 5).map { it.toString() }.toTypedArray()

        val spinnerMes = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceiroCaronasActivity, android.R.layout.simple_spinner_item, meses).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(calendarAgora.get(Calendar.MONTH))
        }
        val spinnerAno = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceiroCaronasActivity, android.R.layout.simple_spinner_item, anos).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(0)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(TextView(this@FinanceiroCaronasActivity).apply { text = getString(R.string.financeiro_mes_label) })
            addView(spinnerMes)
            addView(TextView(this@FinanceiroCaronasActivity).apply {
                text = getString(R.string.financeiro_ano_label)
                setPadding(0, 24, 0, 0)
            })
            addView(spinnerAno)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.financeiro_gerar_relatorio_opcao_mensal)
            .setView(container)
            .setPositiveButton(R.string.financeiro_gerar_relatorio_gerar) { _, _ ->
                val mes = spinnerMes.selectedItemPosition
                val ano = anos[spinnerAno.selectedItemPosition].toInt()
                gerarRelatorioMensal(mes, ano)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun gerarRelatorioMensal(mes: Int, ano: Int) {
        val progress = ProgressDialog(this)
        progress.setMessage(getString(R.string.financeiro_carregando_dados))
        progress.setCancelable(false)
        progress.show()

        val calInicio = Calendar.getInstance().apply { clear(); set(ano, mes, 1) }
        val inicioMillis = calInicio.timeInMillis
        val fimMillis = (calInicio.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis

        val linhas = todosPagamentos.mapNotNull { p ->
            val dataCompra = p.dataCompra ?: return@mapNotNull null
            if (dataCompra < inicioMillis || dataCompra >= fimMillis) return@mapNotNull null
            if (p.valor <= 0.0) return@mapNotNull null
            LinhaRelatorio(
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

        gerarPdfRelatorioMensal(linhas, mes, ano)
    }

    private fun gerarPdfRelatorioMensal(linhas: List<LinhaRelatorio>, mes: Int, ano: Int) {
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
                listOf(l.nome, l.email, formatarValor(l.valor), sdf.format(l.dataCompra), l.expiraEm?.let { sdf.format(it) } ?: "-")
            },
            pesos = listOf(0.28f, 0.27f, 0.15f, 0.15f, 0.15f)
        )

        // Agrupado pelo VALOR pago — mesmo raciocínio plano-agnóstico do
        // Match, mesmo que hoje só exista um valor fixo (R$15,99): cobre
        // sozinho qualquer mudança de preço futura sem precisar mexer aqui.
        pdf.secao(getString(R.string.financeiro_pdf_secao_resumo))
        val porValor = linhas.groupBy { it.valor }.toSortedMap()
        pdf.tabela(
            cabecalhos = listOf(
                getString(R.string.financeiro_pdf_coluna_valor),
                getString(R.string.financeiro_pdf_coluna_qtd),
                getString(R.string.financeiro_pdf_coluna_total)
            ),
            linhas = porValor.entries.map { (valor, itens) ->
                listOf(formatarValor(valor), itens.size.toString(), formatarValor(valor * itens.size))
            },
            pesos = listOf(0.34f, 0.33f, 0.33f)
        )
        val totalGeral = linhas.sumOf { it.valor }
        pdf.linhaDestaque(getString(R.string.financeiro_pdf_total_geral, formatarValor(totalGeral)))

        pdf.rodape(getString(R.string.financeiro_gerado_em, sdfCompleto.format(Date())))
        pdf.gerarEAbrir(getString(R.string.financeiro_pdf_nome_arquivo))
    }

    private fun formatarValor(v: Double): String = getString(R.string.financeiro_valor_format, v)

    private data class LinhaRelatorio(
        val nome: String,
        val email: String,
        val valor: Double,
        val dataCompra: Date,
        val expiraEm: Date?
    )

    private data class ItemPagamento(
        val nome: String,
        val email: String,
        val valor: Double,
        val dataCompra: Date,
        val expiraEm: Date?,
        val ativo: Boolean
    )

    // ===== ADAPTER =====
    private inner class PagamentoAdapter(private val pagamentos: MutableList<ItemPagamento>) :
        RecyclerView.Adapter<PagamentoAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pagamento_motorista, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val p = pagamentos[position]
            holder.tvNome.text = p.nome
            holder.tvEmail.text = p.email
            holder.tvValor.text = getString(R.string.financeiro_valor_format, p.valor)
            holder.tvDataCompra.text = sdf.format(p.dataCompra)
            holder.tvExpiraEm.text = p.expiraEm?.let { sdf.format(it) } ?: "-"
            holder.tvStatus.setText(if (p.ativo) R.string.financeiro_status_ativo else R.string.financeiro_status_expirado)
            holder.tvStatus.setTextColor(if (p.ativo) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt())
        }

        override fun getItemCount(): Int = pagamentos.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNome: TextView = itemView.findViewById(R.id.tvNome)
            val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
            val tvValor: TextView = itemView.findViewById(R.id.tvValor)
            val tvDataCompra: TextView = itemView.findViewById(R.id.tvDataCompra)
            val tvExpiraEm: TextView = itemView.findViewById(R.id.tvExpiraEm)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        }
    }

    companion object {
        private const val PERIODO_TODOS = 0
        private const val PERIODO_DIARIO = 1
        private const val PERIODO_SEMANAL = 2
        private const val PERIODO_MENSAL = 3
        private const val PERIODO_TRIMESTRAL = 4
        private const val PERIODO_SEMESTRAL = 5
        private const val PERIODO_ANUAL = 6
        private const val PERIODO_PERSONALIZADO = 7
    }
}

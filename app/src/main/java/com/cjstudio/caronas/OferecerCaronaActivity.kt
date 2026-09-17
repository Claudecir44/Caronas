package com.cjstudio.caronas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class OferecerCaronaActivity : AppCompatActivity() {

    @Inject
    lateinit var caronaRepository: ICaronaRepository

    @Inject
    lateinit var autocompleteRepository: IAutocompleteRepository

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var etCidadeOrigem: EditText
    private lateinit var etEnderecoOrigem: EditText
    private lateinit var etCidadeDestino: EditText
    private lateinit var etEnderecoDestino: EditText
    private lateinit var containerParadas: LinearLayout
    private lateinit var btnAdicionarParada: TextView
    private lateinit var btnData: EditText
    private lateinit var btnHora: EditText
    private lateinit var etVagas: EditText
    private lateinit var btnCalcularSugestao: TextView
    private lateinit var layoutSugestao: LinearLayout
    private lateinit var etValorPorVaga: EditText
    private lateinit var btnPublicar: Button
    private lateinit var progressBar: ProgressBar

    // Guarda a data/hora escolhida acumulando os dois pickers no mesmo
    // Calendar — só é considerada válida depois que os dois já rodaram
    // (dataEscolhida/horaEscolhida), pra não publicar com a hora atual por
    // engano quando o motorista esquece de tocar em um dos dois botões.
    private val calendarioSelecionado = Calendar.getInstance()
    private var dataEscolhida = false
    private var horaEscolhida = false
    private var distanciaCalculadaKm: Double? = null
    private var valorSugeridoAtual: Double? = null

    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oferecer_carona)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        etCidadeOrigem = findViewById(R.id.etCidadeOrigem)
        etEnderecoOrigem = findViewById(R.id.etEnderecoOrigem)
        etCidadeDestino = findViewById(R.id.etCidadeDestino)
        etEnderecoDestino = findViewById(R.id.etEnderecoDestino)
        containerParadas = findViewById(R.id.containerParadas)
        btnAdicionarParada = findViewById(R.id.btnAdicionarParada)
        btnData = findViewById(R.id.tvData)
        btnHora = findViewById(R.id.tvHora)
        etVagas = findViewById(R.id.etVagas)
        btnCalcularSugestao = findViewById(R.id.btnCalcularSugestao)
        layoutSugestao = findViewById(R.id.layoutSugestao)
        etValorPorVaga = findViewById(R.id.etValorPorVaga)
        btnPublicar = findViewById(R.id.btnPublicar)
        progressBar = findViewById(R.id.progressBar)

        AutocompleteEnderecoUtil.ligar(this, etCidadeOrigem, autocompleteRepository)
        AutocompleteEnderecoUtil.ligar(this, etEnderecoOrigem, autocompleteRepository)
        AutocompleteEnderecoUtil.ligar(this, etCidadeDestino, autocompleteRepository)
        AutocompleteEnderecoUtil.ligar(this, etEnderecoDestino, autocompleteRepository)

        btnData.setOnClickListener { abrirSeletorData() }
        btnHora.setOnClickListener { abrirSeletorHora() }
        btnAdicionarParada.setOnClickListener { adicionarLinhaParada() }
        btnCalcularSugestao.setOnClickListener { calcularSugestao() }
        btnPublicar.setOnClickListener { validarEPublicar() }
    }

    // Insere uma linha de parada (cidade + endereço opcional + botão
    // remover) no container — sem RecyclerView, é uma lista curta editada
    // uma vez só (mesmo espírito dos outros formulários simples do app).
    private fun adicionarLinhaParada() {
        val linha = LayoutInflater.from(this).inflate(R.layout.item_parada_rota_input, containerParadas, false)
        linha.findViewById<TextView>(R.id.btnRemoverParada).setOnClickListener {
            containerParadas.removeView(linha)
        }
        AutocompleteEnderecoUtil.ligar(this, linha.findViewById(R.id.etCidadeParada), autocompleteRepository)
        AutocompleteEnderecoUtil.ligar(this, linha.findViewById(R.id.etEnderecoParada), autocompleteRepository)
        containerParadas.addView(linha)
    }

    // Lê as linhas de parada já adicionadas, em ordem — null se alguma
    // ficou com a cidade em branco (o motorista precisa preencher ou
    // remover, não dá pra silenciosamente ignorar uma parada incompleta).
    private fun coletarParadasIntermediarias(): List<ParadaRota>? {
        val paradas = mutableListOf<ParadaRota>()
        for (i in 0 until containerParadas.childCount) {
            val linha = containerParadas.getChildAt(i)
            val cidade = linha.findViewById<EditText>(R.id.etCidadeParada).text.toString().trim()
            val endereco = linha.findViewById<EditText>(R.id.etEnderecoParada).text.toString().trim()
            if (cidade.isEmpty()) return null
            paradas.add(ParadaRota(cidade = cidade, endereco = endereco.ifEmpty { null }))
        }
        return paradas
    }

    // Rota completa em ordem: origem (com endereço, se houver) + paradas
    // intermediárias + destino — usada tanto pra calcular a sugestão quanto
    // pra montar a Carona final em validarEPublicar.
    private fun montarRota(): List<ParadaRota>? {
        val origem = etCidadeOrigem.text.toString().trim()
        val destino = etCidadeDestino.text.toString().trim()
        if (origem.isEmpty() || destino.isEmpty()) return null
        val intermediarias = coletarParadasIntermediarias() ?: return null

        val rota = mutableListOf(ParadaRota(cidade = origem, endereco = etEnderecoOrigem.text.toString().trim().ifEmpty { null }))
        rota.addAll(intermediarias)
        rota.add(ParadaRota(cidade = destino, endereco = etEnderecoDestino.text.toString().trim().ifEmpty { null }))
        return rota.mapIndexed { indice, parada -> parada.copy(ordem = indice) }
    }

    // "endereço, cidade" quando há endereço (geocodifica mais preciso),
    // senão só a cidade — mesma string usada tanto pra pré-visualizar a
    // sugestão quanto pra gravar de fato (ver DistanciaUtil.geocodificar).
    private fun pontoParaGeocoding(parada: ParadaRota): String {
        val endereco = parada.endereco
        val cidade = parada.cidade ?: ""
        return if (!endereco.isNullOrBlank()) "$endereco, $cidade" else cidade
    }

    private fun abrirSeletorData() {
        val c = calendarioSelecionado
        DatePickerDialog(this, { _, ano, mes, dia ->
            c.set(Calendar.YEAR, ano)
            c.set(Calendar.MONTH, mes)
            c.set(Calendar.DAY_OF_MONTH, dia)
            dataEscolhida = true
            btnData.setText(formatoData.format(c.time))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun abrirSeletorHora() {
        val c = calendarioSelecionado
        TimePickerDialog(this, { _, hora, minuto ->
            c.set(Calendar.HOUR_OF_DAY, hora)
            c.set(Calendar.MINUTE, minuto)
            horaEscolhida = true
            btnHora.setText(formatoHora.format(c.time))
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    // Ver DistanciaUtil: distância aproximada (linha reta × fator de
    // estrada) via Geocoder, sem depender de API paga de rotas.
    //
    // Cada campo mostrado é SEMPRE a origem até AQUELE ponto (parada ou o
    // destino final) — nunca encadeado entre pontos intermediários
    // (origem->parada1, origem->parada2, ..., origem->destino — não
    // parada1->parada2). Uma parada é um desvio pra pegar OUTRO passageiro;
    // ela não pode inflar o preço de quem embarca na origem, porque a
    // distância que ele percorre não muda com paradas de terceiros. Quem
    // pede uma vaga embarcando/descendo NUM PONTO DO MEIO (não na origem)
    // vê o valor daquele trecho específico calculado na hora, direto entre
    // os dois pontos que ele escolheu — não aqui, ver
    // TelaCaronasActivity.solicitarVaga.
    private fun calcularSugestao() {
        val origem = etCidadeOrigem.text.toString().trim()
        val destino = etCidadeDestino.text.toString().trim()
        if (origem.isEmpty()) {
            etCidadeOrigem.error = getString(R.string.oferecer_erro_origem)
            return
        }
        if (destino.isEmpty()) {
            etCidadeDestino.error = getString(R.string.oferecer_erro_destino)
            return
        }
        val rota = montarRota()
        if (rota == null) {
            Toast.makeText(this, R.string.oferecer_erro_parada_cidade, Toast.LENGTH_SHORT).show()
            return
        }

        btnCalcularSugestao.isEnabled = false
        mostrarLinhaUnicaSugestao(getString(R.string.oferecer_calculando))

        lifecycleScope.launch {
            val sugestoes = withContext(Dispatchers.IO) {
                runCatching {
                    DistanciaUtil.calcularSugestoesAPartirDaOrigem(this@OferecerCaronaActivity, rota.map { pontoParaGeocoding(it) }, calendarioSelecionado)
                }.getOrNull()
            }
            btnCalcularSugestao.isEnabled = true

            if (sugestoes == null) {
                mostrarLinhaUnicaSugestao(getString(R.string.oferecer_erro_cidades_nao_encontradas))
                return@launch
            }

            // Última posição = origem->destino direto — é este valor,
            // nunca uma soma, que define o preço de quem faz a viagem
            // inteira (ver comentário da função).
            val sugestaoTotal = sugestoes.last()
            val tarifa = getString(
                if (sugestaoTotal.fimDeSemanaOuFeriado) R.string.oferecer_tarifa_fim_de_semana else R.string.oferecer_tarifa_normal
            )

            distanciaCalculadaKm = sugestaoTotal.distanciaKm
            valorSugeridoAtual = sugestaoTotal.valorSugerido

            // Com paradas (rota.size > 2), mostra o preço sugerido de
            // origem até CADA parada (uma por vez, sempre a partir da
            // origem) além do total (origem->destino) no final. Sem
            // paradas, mantém a linha única de sempre (não muda nada pro
            // caso mais comum).
            layoutSugestao.removeAllViews()
            if (rota.size > 2) {
                // sugestoes[i] corresponde a rota[i + 1] (rota.drop(1)) —
                // todas MENOS a última (o destino, que vira a linha em negrito).
                for (indice in 0 until sugestoes.size - 1) {
                    val sugestao = sugestoes[indice]
                    layoutSugestao.addView(criarLinhaSugestao(
                        getString(
                            R.string.oferecer_trecho_formato,
                            rota.first().cidade, rota[indice + 1].cidade,
                            formatarReais(sugestao.valorSugerido), sugestao.distanciaKm.roundToInt()
                        ),
                        negrito = false
                    ))
                }
                layoutSugestao.addView(criarLinhaSugestao(
                    getString(R.string.oferecer_sugestao_total_formato, formatarReais(sugestaoTotal.valorSugerido), sugestaoTotal.distanciaKm.roundToInt(), tarifa),
                    negrito = true
                ))
            } else {
                layoutSugestao.addView(criarLinhaSugestao(
                    getString(R.string.oferecer_sugestao_texto, formatarReais(sugestaoTotal.valorSugerido), sugestaoTotal.distanciaKm.roundToInt(), tarifa),
                    negrito = false
                ))
            }
            layoutSugestao.visibility = View.VISIBLE

            // Pré-preenche o campo de valor com a sugestão origem->destino
            // direto, mas continua editável — o motorista decide o valor
            // final.
            etValorPorVaga.setText(formatarValorEditavel(sugestaoTotal.valorSugerido))
        }
    }

    private fun mostrarLinhaUnicaSugestao(texto: String) {
        layoutSugestao.removeAllViews()
        layoutSugestao.addView(criarLinhaSugestao(texto, negrito = false))
        layoutSugestao.visibility = View.VISIBLE
    }

    private fun criarLinhaSugestao(texto: String, negrito: Boolean): TextView {
        return TextView(this).apply {
            text = texto
            setTextColor(resources.getColor(R.color.azul_neon_escuro, theme))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 4, 0, 4)
            if (negrito) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun validarEPublicar() {
        val origem = etCidadeOrigem.text.toString().trim()
        val destino = etCidadeDestino.text.toString().trim()
        val vagas = etVagas.text.toString().trim().toIntOrNull()
        val valor = etValorPorVaga.text.toString().trim().replace(",", ".").toDoubleOrNull()

        if (origem.isEmpty()) {
            etCidadeOrigem.error = getString(R.string.oferecer_erro_origem)
            return
        }
        if (destino.isEmpty()) {
            etCidadeDestino.error = getString(R.string.oferecer_erro_destino)
            return
        }
        if (!dataEscolhida || !horaEscolhida) {
            Toast.makeText(this, R.string.oferecer_erro_data_hora, Toast.LENGTH_SHORT).show()
            return
        }
        if (calendarioSelecionado.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.oferecer_erro_data_passado, Toast.LENGTH_SHORT).show()
            return
        }
        if (vagas == null || vagas <= 0) {
            etVagas.error = getString(R.string.oferecer_erro_vagas)
            return
        }
        if (valor == null || valor <= 0) {
            etValorPorVaga.error = getString(R.string.oferecer_erro_valor)
            return
        }
        val rota = montarRota()
        if (rota == null) {
            Toast.makeText(this, R.string.oferecer_erro_parada_cidade, Toast.LENGTH_SHORT).show()
            return
        }

        val carona = Carona(
            cidadeOrigem = origem,
            cidadeDestino = destino,
            paradas = rota,
            distanciaKm = distanciaCalculadaKm,
            dataHoraPartida = calendarioSelecionado.timeInMillis,
            vagas = vagas,
            valorPorVaga = valor,
            valorSugerido = valorSugeridoAtual
        )

        progressBar.visibility = View.VISIBLE
        btnPublicar.isEnabled = false

        lifecycleScope.launch {
            // Checagem amigável ANTES de tentar publicar — quem garante de
            // verdade é firestore.rules:permiteOferecerCarona (ver
            // AcessoMotoristaUtil), isso aqui só evita um erro genérico de
            // permissão negada quando dá pra avisar com clareza antes.
            val usuario = usuarioRepository.buscarUsuarioLogado().getOrNull()
            if (usuario != null && !AcessoMotoristaUtil.permiteOferecerCarona(usuario)) {
                progressBar.visibility = View.GONE
                btnPublicar.isEnabled = true
                mostrarBloqueioAcessoMotorista()
                return@launch
            }

            caronaRepository.publicarCarona(carona)
                .onSuccess {
                    Toast.makeText(this@OferecerCaronaActivity, R.string.oferecer_sucesso, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    btnPublicar.isEnabled = true
                    Toast.makeText(
                        this@OferecerCaronaActivity,
                        getString(R.string.oferecer_erro_generico, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun formatarReais(valor: Double) = "R$ " + formatarValorEditavel(valor)
    private fun formatarValorEditavel(valor: Double) = String.format(Locale("pt", "BR"), "%.2f", valor)

    private fun mostrarBloqueioAcessoMotorista() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.oferecer_bloqueado_titulo)
            .setMessage(R.string.oferecer_bloqueado_mensagem)
            .setPositiveButton(R.string.oferecer_bloqueado_botao_assinar) { _, _ ->
                startActivity(Intent(this, AssinaturaMotoristaActivity::class.java))
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}

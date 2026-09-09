package com.cjstudio.caronas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    private lateinit var etCidadeOrigem: EditText
    private lateinit var etCidadeDestino: EditText
    private lateinit var btnData: Button
    private lateinit var btnHora: Button
    private lateinit var etVagas: EditText
    private lateinit var btnCalcularSugestao: TextView
    private lateinit var tvSugestao: TextView
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
        etCidadeDestino = findViewById(R.id.etCidadeDestino)
        btnData = findViewById(R.id.tvData)
        btnHora = findViewById(R.id.tvHora)
        etVagas = findViewById(R.id.etVagas)
        btnCalcularSugestao = findViewById(R.id.btnCalcularSugestao)
        tvSugestao = findViewById(R.id.tvSugestao)
        etValorPorVaga = findViewById(R.id.etValorPorVaga)
        btnPublicar = findViewById(R.id.btnPublicar)
        progressBar = findViewById(R.id.progressBar)

        btnData.setOnClickListener { abrirSeletorData() }
        btnHora.setOnClickListener { abrirSeletorHora() }
        btnCalcularSugestao.setOnClickListener { calcularSugestao() }
        btnPublicar.setOnClickListener { validarEPublicar() }
    }

    private fun abrirSeletorData() {
        val c = calendarioSelecionado
        DatePickerDialog(this, { _, ano, mes, dia ->
            c.set(Calendar.YEAR, ano)
            c.set(Calendar.MONTH, mes)
            c.set(Calendar.DAY_OF_MONTH, dia)
            dataEscolhida = true
            btnData.text = formatoData.format(c.time)
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
            btnHora.text = formatoHora.format(c.time)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    // Ver DistanciaUtil: distância aproximada (linha reta × fator de
    // estrada) via Geocoder, sem depender de API paga de rotas.
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

        btnCalcularSugestao.isEnabled = false
        tvSugestao.visibility = View.VISIBLE
        tvSugestao.text = getString(R.string.oferecer_calculando)

        lifecycleScope.launch {
            val sugestao = withContext(Dispatchers.IO) {
                runCatching {
                    DistanciaUtil.calcularSugestao(this@OferecerCaronaActivity, origem, destino, calendarioSelecionado)
                }.getOrNull()
            }
            btnCalcularSugestao.isEnabled = true

            if (sugestao == null) {
                tvSugestao.text = getString(R.string.oferecer_erro_cidades_nao_encontradas)
                return@launch
            }

            distanciaCalculadaKm = sugestao.distanciaKm
            valorSugeridoAtual = sugestao.valorSugerido
            val tarifa = getString(
                if (sugestao.fimDeSemanaOuFeriado) R.string.oferecer_tarifa_fim_de_semana else R.string.oferecer_tarifa_normal
            )
            tvSugestao.text = getString(
                R.string.oferecer_sugestao_texto,
                formatarReais(sugestao.valorSugerido),
                sugestao.distanciaKm.roundToInt(),
                tarifa
            )
            // Pré-preenche o campo de valor com a sugestão, mas continua
            // editável — o motorista decide o valor final.
            etValorPorVaga.setText(formatarValorEditavel(sugestao.valorSugerido))
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

        val carona = Carona(
            cidadeOrigem = origem,
            cidadeDestino = destino,
            distanciaKm = distanciaCalculadaKm,
            dataHoraPartida = calendarioSelecionado.timeInMillis,
            vagas = vagas,
            valorPorVaga = valor,
            valorSugerido = valorSugeridoAtual
        )

        progressBar.visibility = View.VISIBLE
        btnPublicar.isEnabled = false

        lifecycleScope.launch {
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
}

package com.cjstudio.caronas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MinhasOfertasActivity : AppCompatActivity() {

    @Inject
    lateinit var caronaRepository: ICaronaRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var tvSemOfertas: TextView
    private lateinit var rvMinhasOfertas: RecyclerView

    private lateinit var progressBarSolicitacoes: ProgressBar
    private lateinit var tvTituloSolicitacoesRecebidas: TextView
    private lateinit var tvSemSolicitacoes: TextView
    private lateinit var rvSolicitacoesRecebidas: RecyclerView

    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minhas_ofertas)

        progressBar = findViewById(R.id.progressBarOfertas)
        tvSemOfertas = findViewById(R.id.tvSemOfertas)
        rvMinhasOfertas = findViewById(R.id.rvMinhasOfertas)
        rvMinhasOfertas.layoutManager = LinearLayoutManager(this)

        progressBarSolicitacoes = findViewById(R.id.progressBarSolicitacoes)
        tvTituloSolicitacoesRecebidas = findViewById(R.id.tvTituloSolicitacoesRecebidas)
        tvSemSolicitacoes = findViewById(R.id.tvSemSolicitacoes)
        rvSolicitacoesRecebidas = findViewById(R.id.rvSolicitacoesRecebidas)
        rvSolicitacoesRecebidas.layoutManager = LinearLayoutManager(this)

        carregarOfertas()
        // Não chama carregarSolicitacoesRecebidas() aqui também — onResume()
        // já dispara logo em seguida (sempre roda depois de onCreate) e as
        // duas buscas assíncronas corriam em paralelo; qual terminasse por
        // último sobrescrevia rvSolicitacoesRecebidas.adapter com uma
        // instância nova (posicaoAberta reiniciada), fechando de novo
        // qualquer card que o usuário tivesse acabado de abrir bem na
        // largada.
    }

    override fun onResume() {
        super.onResume()
        // Também cobre a volta do chat com um passageiro, sem precisar
        // fechar e reabrir a tela toda.
        carregarSolicitacoesRecebidas()
    }

    private fun carregarOfertas() {
        progressBar.visibility = View.VISIBLE
        tvSemOfertas.visibility = View.GONE
        rvMinhasOfertas.visibility = View.GONE

        lifecycleScope.launch {
            caronaRepository.buscarMinhasOfertas()
                .onSuccess { ofertas ->
                    progressBar.visibility = View.GONE
                    if (ofertas.isEmpty()) {
                        tvSemOfertas.visibility = View.VISIBLE
                    } else {
                        rvMinhasOfertas.visibility = View.VISIBLE
                        rvMinhasOfertas.adapter = MinhaOfertaAdapter(
                            ofertas,
                            onEditarClick = { abrirDialogEditarOferta(it) },
                            onExcluirClick = { confirmarExcluirOferta(it) }
                        )
                    }
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MinhasOfertasActivity, getString(R.string.minhas_ofertas_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun carregarSolicitacoesRecebidas() {
        progressBarSolicitacoes.visibility = View.VISIBLE
        tvTituloSolicitacoesRecebidas.visibility = View.VISIBLE
        tvSemSolicitacoes.visibility = View.GONE
        rvSolicitacoesRecebidas.visibility = View.GONE

        lifecycleScope.launch {
            solicitacaoRepository.buscarSolicitacoesRecebidas()
                .onSuccess { solicitacoes ->
                    progressBarSolicitacoes.visibility = View.GONE
                    if (solicitacoes.isEmpty()) {
                        tvSemSolicitacoes.visibility = View.VISIBLE
                    } else {
                        rvSolicitacoesRecebidas.visibility = View.VISIBLE
                        rvSolicitacoesRecebidas.adapter = SolicitacaoRecebidaAdapter(
                            solicitacoes,
                            onConfirmarClick = { confirmarSolicitacao(it) },
                            onChatClick = { abrirChat(it) },
                            onExcluirLongClick = { confirmarExcluirSolicitacao(it) }
                        )
                    }
                }
                .onFailure { e ->
                    progressBarSolicitacoes.visibility = View.GONE
                    Toast.makeText(this@MinhasOfertasActivity, getString(R.string.solicitacoes_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmarSolicitacao(solicitacao: Solicitacao) {
        val solicitacaoId = solicitacao.id ?: return
        lifecycleScope.launch {
            solicitacaoRepository.confirmarSolicitacao(solicitacaoId)
                .onSuccess {
                    Toast.makeText(this@MinhasOfertasActivity, R.string.solicitacoes_confirmada_sucesso, Toast.LENGTH_SHORT).show()
                    carregarSolicitacoesRecebidas()
                }
                .onFailure { e ->
                    Toast.makeText(this@MinhasOfertasActivity, getString(R.string.solicitacoes_erro_confirmar, e.message), Toast.LENGTH_LONG).show()
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
                            Toast.makeText(this@MinhasOfertasActivity, R.string.solicitacoes_excluida_sucesso, Toast.LENGTH_SHORT).show()
                            carregarSolicitacoesRecebidas()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@MinhasOfertasActivity, getString(R.string.solicitacoes_erro_excluir, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun abrirChat(solicitacao: Solicitacao) {
        val intent = Intent(this, ChatCaronaActivity::class.java)
        intent.putExtra(ChatCaronaActivity.EXTRA_SOLICITACAO, solicitacao)
        startActivity(intent)
    }

    private fun abrirDialogEditarOferta(oferta: Carona) {
        val view = layoutInflater.inflate(R.layout.dialog_editar_oferta, null)
        val etData = view.findViewById<EditText>(R.id.etDataEditar)
        val etHora = view.findViewById<EditText>(R.id.etHoraEditar)
        val etVagas = view.findViewById<EditText>(R.id.etVagasEditar)
        val etValor = view.findViewById<EditText>(R.id.etValorEditar)
        val btnSalvar = view.findViewById<Button>(R.id.btnSalvarOferta)

        val calendario = Calendar.getInstance().apply {
            timeInMillis = oferta.dataHoraPartida ?: System.currentTimeMillis()
        }
        etData.setText(formatoData.format(calendario.time))
        etHora.setText(formatoHora.format(calendario.time))
        etVagas.setText(oferta.vagas.toString())
        etValor.setText(String.format(Locale("pt", "BR"), "%.2f", oferta.valorPorVaga ?: 0.0))

        etData.setOnClickListener {
            DatePickerDialog(this, { _, ano, mes, dia ->
                calendario.set(Calendar.YEAR, ano)
                calendario.set(Calendar.MONTH, mes)
                calendario.set(Calendar.DAY_OF_MONTH, dia)
                etData.setText(formatoData.format(calendario.time))
            }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }
        etHora.setOnClickListener {
            TimePickerDialog(this, { _, hora, minuto ->
                calendario.set(Calendar.HOUR_OF_DAY, hora)
                calendario.set(Calendar.MINUTE, minuto)
                etHora.setText(formatoHora.format(calendario.time))
            }, calendario.get(Calendar.HOUR_OF_DAY), calendario.get(Calendar.MINUTE), true).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        btnSalvar.setOnClickListener {
            val vagas = etVagas.text.toString().trim().toIntOrNull()
            val valor = etValor.text.toString().trim().replace(",", ".").toDoubleOrNull()

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

            dialog.dismiss()
            lifecycleScope.launch {
                caronaRepository.atualizarOferta(caronaId, calendario.timeInMillis, vagas, valor)
                    .onSuccess {
                        Toast.makeText(this@MinhasOfertasActivity, R.string.minhas_ofertas_salva_sucesso, Toast.LENGTH_SHORT).show()
                        carregarOfertas()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@MinhasOfertasActivity, getString(R.string.minhas_ofertas_erro_salvar, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }

        dialog.show()
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
                            Toast.makeText(this@MinhasOfertasActivity, R.string.minhas_ofertas_excluida_sucesso, Toast.LENGTH_SHORT).show()
                            carregarOfertas()
                        }
                        .onFailure { e ->
                            Toast.makeText(this@MinhasOfertasActivity, getString(R.string.minhas_ofertas_erro_excluir, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}

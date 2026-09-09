package com.cjstudio.caronas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EditarCadastroCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var etNomeCompleto: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefone: EditText
    private lateinit var cbSouMotorista: CheckBox
    private lateinit var layoutVeiculo: LinearLayout
    private lateinit var etVeiculoModelo: EditText
    private lateinit var etVeiculoMarca: EditText
    private lateinit var etVeiculoCor: EditText
    private lateinit var etVeiculoPlaca: EditText
    private lateinit var btnSalvar: Button
    private lateinit var btnExcluirCadastro: Button
    private lateinit var progressBar: ProgressBar

    private var usuarioAtual: Usuario? = null
    private var fotoUriSelecionada: Uri? = null

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val uriLocal = FotoUtil.copiarUriParaArquivoTemporario(this, uri)
            fotoUriSelecionada = uriLocal
            ivFotoPerfil.load(uriLocal ?: uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restaurarRedimensionamentoComTeclado()
        setContentView(R.layout.activity_editar_cadastro_caronas)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        etNomeCompleto = findViewById(R.id.etNomeCompleto)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        cbSouMotorista = findViewById(R.id.cbSouMotorista)
        layoutVeiculo = findViewById(R.id.layoutVeiculo)
        etVeiculoModelo = findViewById(R.id.etVeiculoModelo)
        etVeiculoMarca = findViewById(R.id.etVeiculoMarca)
        etVeiculoCor = findViewById(R.id.etVeiculoCor)
        etVeiculoPlaca = findViewById(R.id.etVeiculoPlaca)
        btnSalvar = findViewById(R.id.btnSalvar)
        btnExcluirCadastro = findViewById(R.id.btnExcluirCadastro)
        progressBar = findViewById(R.id.progressBar)

        val tvSelecionarFoto = findViewById<TextView>(R.id.tvSelecionarFoto)
        val abrirSeletor = View.OnClickListener {
            seletorFoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        ivFotoPerfil.setOnClickListener(abrirSeletor)
        tvSelecionarFoto.setOnClickListener(abrirSeletor)

        cbSouMotorista.setOnCheckedChangeListener { _, marcado ->
            layoutVeiculo.visibility = if (marcado) View.VISIBLE else View.GONE
            if (!marcado) {
                etVeiculoModelo.text.clear()
                etVeiculoMarca.text.clear()
                etVeiculoCor.text.clear()
                etVeiculoPlaca.text.clear()
            }
        }

        btnSalvar.setOnClickListener { validarESalvar() }
        btnExcluirCadastro.setOnClickListener { confirmarExclusao() }

        carregarUsuarioLogado()
    }

    private fun carregarUsuarioLogado() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                progressBar.visibility = View.GONE
                preencherCampos(usuario)
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this@EditarCadastroCaronasActivity, getString(R.string.erro_generico, e.message), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun preencherCampos(usuario: Usuario) {
        usuarioAtual = usuario
        etNomeCompleto.setText(usuario.nomeCompleto)
        etEmail.setText(usuario.email)
        etTelefone.setText(usuario.telefone)
        cbSouMotorista.isChecked = usuario.motorista
        layoutVeiculo.visibility = if (usuario.motorista) View.VISIBLE else View.GONE
        usuario.veiculo?.let { veiculo ->
            etVeiculoModelo.setText(veiculo.modelo)
            etVeiculoMarca.setText(veiculo.marca)
            etVeiculoCor.setText(veiculo.cor)
            etVeiculoPlaca.setText(veiculo.placa)
        }
        if (!usuario.fotoUrl.isNullOrEmpty()) {
            ivFotoPerfil.load(usuario.fotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        }
    }

    private fun validarESalvar() {
        val base = usuarioAtual ?: return
        val nome = etNomeCompleto.text.toString().trim()
        val telefone = etTelefone.text.toString().trim()
        val souMotorista = cbSouMotorista.isChecked

        if (nome.isEmpty()) {
            Toast.makeText(this, R.string.cadastro_erro_nome_obrigatorio, Toast.LENGTH_SHORT).show()
            return
        }
        if (telefone.isEmpty()) {
            Toast.makeText(this, R.string.cadastro_erro_telefone_obrigatorio, Toast.LENGTH_SHORT).show()
            return
        }

        var veiculo: Veiculo? = null
        if (souMotorista) {
            val modelo = etVeiculoModelo.text.toString().trim()
            val marca = etVeiculoMarca.text.toString().trim()
            val cor = etVeiculoCor.text.toString().trim()
            val placa = etVeiculoPlaca.text.toString().trim()
            if (modelo.isEmpty() || marca.isEmpty() || cor.isEmpty() || placa.isEmpty()) {
                Toast.makeText(this, R.string.cadastro_erro_veiculo_obrigatorio, Toast.LENGTH_SHORT).show()
                return
            }
            veiculo = Veiculo(modelo = modelo, marca = marca, cor = cor, placa = placa)
        }

        val usuarioAtualizado = base.copy(
            nomeCompleto = nome,
            telefone = telefone,
            motorista = souMotorista,
            veiculo = veiculo
        )

        progressBar.visibility = View.VISIBLE
        btnSalvar.isEnabled = false

        lifecycleScope.launch {
            val uid = usuarioAtualizado.id!!
            val uriFoto = fotoUriSelecionada
            if (uriFoto != null) {
                usuarioRepository.uploadFotoPerfil(uid, uriFoto)
            }

            usuarioRepository.atualizarPerfil(usuarioAtualizado).onSuccess {
                progressBar.visibility = View.GONE
                btnSalvar.isEnabled = true
                Toast.makeText(this@EditarCadastroCaronasActivity, R.string.editar_sucesso, Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                btnSalvar.isEnabled = true
                Toast.makeText(this@EditarCadastroCaronasActivity, getString(R.string.editar_erro_generico, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmarExclusao() {
        val campoSenha = EditText(this).apply {
            hint = getString(R.string.editar_senha_atual)
            habilitarToggleSenha()
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            setPadding(padding, padding, padding, 0)
            addView(campoSenha)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.editar_confirmar_exclusao_titulo)
            .setMessage(R.string.editar_confirmar_exclusao_mensagem)
            .setView(container)
            .setPositiveButton(R.string.excluir) { _, _ ->
                executarExclusao(campoSenha.text.toString())
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun executarExclusao(senhaAtual: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            usuarioRepository.excluirContaPropria(senhaAtual).onSuccess {
                progressBar.visibility = View.GONE
                startActivity(Intent(this@EditarCadastroCaronasActivity, LoginCaronasActivity::class.java))
                finishAffinity()
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@EditarCadastroCaronasActivity,
                    getString(R.string.editar_excluir_erro_generico, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

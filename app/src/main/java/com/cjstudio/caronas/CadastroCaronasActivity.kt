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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil3.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CadastroCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var etNomeCompleto: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefone: EditText
    private lateinit var etSenha: EditText
    private lateinit var etConfirmarSenha: EditText
    private lateinit var cbSouMotorista: CheckBox
    private lateinit var layoutVeiculo: LinearLayout
    private lateinit var etVeiculoModelo: EditText
    private lateinit var etVeiculoMarca: EditText
    private lateinit var etVeiculoCor: EditText
    private lateinit var etVeiculoPlaca: EditText
    private lateinit var btnCadastrar: Button
    private lateinit var progressBar: ProgressBar

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
        setContentView(R.layout.activity_cadastro_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        etNomeCompleto = findViewById(R.id.etNomeCompleto)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        etSenha = findViewById(R.id.etSenha)
        etSenha.habilitarToggleSenha()
        etConfirmarSenha = findViewById(R.id.etConfirmarSenha)
        etConfirmarSenha.habilitarToggleSenha()
        cbSouMotorista = findViewById(R.id.cbSouMotorista)
        layoutVeiculo = findViewById(R.id.layoutVeiculo)
        etVeiculoModelo = findViewById(R.id.etVeiculoModelo)
        etVeiculoMarca = findViewById(R.id.etVeiculoMarca)
        etVeiculoCor = findViewById(R.id.etVeiculoCor)
        etVeiculoPlaca = findViewById(R.id.etVeiculoPlaca)
        btnCadastrar = findViewById(R.id.btnCadastrar)
        progressBar = findViewById(R.id.progressBar)

        val tvSelecionarFoto = findViewById<TextView>(R.id.tvSelecionarFoto)
        val abrirSeletor = View.OnClickListener {
            seletorFoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        ivFotoPerfil.setOnClickListener(abrirSeletor)
        tvSelecionarFoto.setOnClickListener(abrirSeletor)

        // Campos do veículo só aparecem (e só são obrigatórios) quando o
        // checkbox está marcado — desmarcar limpa o que já tinha sido
        // preenchido, pra não sobrar dado de veículo num passageiro.
        cbSouMotorista.setOnCheckedChangeListener { _, marcado ->
            layoutVeiculo.visibility = if (marcado) View.VISIBLE else View.GONE
            if (!marcado) {
                etVeiculoModelo.text.clear()
                etVeiculoMarca.text.clear()
                etVeiculoCor.text.clear()
                etVeiculoPlaca.text.clear()
            }
        }

        btnCadastrar.setOnClickListener { validarECadastrar() }
    }

    private fun validarECadastrar() {
        val nome = etNomeCompleto.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val telefone = etTelefone.text.toString().trim()
        val senha = etSenha.text.toString()
        val confirmarSenha = etConfirmarSenha.text.toString()
        val souMotorista = cbSouMotorista.isChecked

        if (nome.isEmpty()) {
            Toast.makeText(this, R.string.cadastro_erro_nome_obrigatorio, Toast.LENGTH_SHORT).show()
            return
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.cadastro_erro_email_obrigatorio, Toast.LENGTH_SHORT).show()
            return
        }
        if (telefone.isEmpty()) {
            Toast.makeText(this, R.string.cadastro_erro_telefone_obrigatorio, Toast.LENGTH_SHORT).show()
            return
        }
        if (senha.length < 6 || senha.length > 10) {
            Toast.makeText(this, R.string.cadastro_erro_senha_tamanho, Toast.LENGTH_SHORT).show()
            return
        }
        if (senha != confirmarSenha) {
            Toast.makeText(this, R.string.cadastro_erro_senhas_diferentes, Toast.LENGTH_SHORT).show()
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

        val usuario = Usuario(
            nomeCompleto = nome,
            email = email,
            telefone = telefone,
            motorista = souMotorista,
            veiculo = veiculo
        )

        progressBar.visibility = View.VISIBLE
        btnCadastrar.isEnabled = false

        lifecycleScope.launch {
            val resultadoCadastro = usuarioRepository.cadastrar(usuario, senha)
            resultadoCadastro.onSuccess { uid ->
                val uriFoto = fotoUriSelecionada
                if (uriFoto != null) {
                    val resultadoFoto = usuarioRepository.uploadFotoPerfil(uid, uriFoto)
                    if (resultadoFoto.isFailure) {
                        // Cadastro (Auth + doc) já existe — não desfaz por causa
                        // só da foto, ela pode ser adicionada depois em Editar
                        // Cadastro. Só avisa o usuário.
                        Toast.makeText(
                            this@CadastroCaronasActivity,
                            getString(R.string.cadastro_erro_generico, resultadoFoto.exceptionOrNull()?.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                progressBar.visibility = View.GONE
                Toast.makeText(this@CadastroCaronasActivity, R.string.cadastro_sucesso, Toast.LENGTH_LONG).show()
                startActivity(Intent(this@CadastroCaronasActivity, TelaCaronasActivity::class.java))
                finish()
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                btnCadastrar.isEnabled = true
                Toast.makeText(
                    this@CadastroCaronasActivity,
                    getString(R.string.cadastro_erro_generico, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

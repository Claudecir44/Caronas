package com.cjstudio.caronas

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

// "Ver cadastro completo" de um motorista ou passageiro, aberta por toque
// (não toque-e-segure, que continua sendo excluir — ver
// UsuarioAdminAdapter/AdministracaoCaronasActivity.mostrarMotoristas/
// mostrarPassageiros) num item das listas do dashboard. Campos começam
// travados (só visualização); "Editar" libera nome/telefone/veículo pra
// edição, "Salvar" pede a senha do administrador master (mesma trava de
// excluirAdmin/excluirUsuario/atualizarAdmin) e grava via Cloud Function
// admAtualizarUsuario (Admin SDK — cliente não tem permissão de escrever
// no cadastro de outra pessoa, ver firestore.rules). Email e foto ficam
// sempre só-leitura aqui (mudar e-mail afeta o login no Firebase Auth,
// mudar foto é um fluxo de upload à parte — fora do escopo deste ajuste).
@AndroidEntryPoint
class DetalhesUsuarioAdminActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var ivFoto: ImageView
    private lateinit var etNome: EditText
    private lateinit var tvEmail: TextView
    private lateinit var etTelefone: EditText
    private lateinit var tvCriadoEm: TextView
    private lateinit var tvCpfRotulo: TextView
    private lateinit var tvCpf: TextView
    private lateinit var containerVeiculo: View
    private lateinit var etVeiculoMarca: EditText
    private lateinit var etVeiculoModelo: EditText
    private lateinit var etVeiculoCor: EditText
    private lateinit var etVeiculoPlaca: EditText
    private lateinit var btnEditarSalvar: Button
    private lateinit var progressBar: ProgressBar

    private var usuarioAtual: Usuario? = null
    private var emEdicao = false
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_usuario_admin)

        ivFoto = findViewById(R.id.ivFotoDetalhesUsuario)
        etNome = findViewById(R.id.etDetalhesNome)
        tvEmail = findViewById(R.id.tvDetalhesEmail)
        etTelefone = findViewById(R.id.etDetalhesTelefone)
        tvCpfRotulo = findViewById(R.id.tvDetalhesCpfRotulo)
        tvCpf = findViewById(R.id.tvDetalhesCpf)
        tvCriadoEm = findViewById(R.id.tvDetalhesCriadoEm)
        containerVeiculo = findViewById(R.id.containerVeiculoDetalhes)
        etVeiculoMarca = findViewById(R.id.etDetalhesVeiculoMarca)
        etVeiculoModelo = findViewById(R.id.etDetalhesVeiculoModelo)
        etVeiculoCor = findViewById(R.id.etDetalhesVeiculoCor)
        etVeiculoPlaca = findViewById(R.id.etDetalhesVeiculoPlaca)
        btnEditarSalvar = findViewById(R.id.btnEditarSalvarDetalhes)
        progressBar = findViewById(R.id.progressBarDetalhesUsuario)

        btnEditarSalvar.setOnClickListener { if (emEdicao) confirmarSalvar() else entrarEmEdicao() }

        val uid = intent.getStringExtra(EXTRA_UID)
        if (uid.isNullOrBlank()) {
            finish()
            return
        }
        carregarUsuario(uid)
    }

    private fun carregarUsuario(uid: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioPorId(uid).onSuccess { usuario ->
                progressBar.visibility = View.GONE
                usuarioAtual = usuario
                preencherCampos(usuario)
                carregarCpf(uid, usuario)
            }.onFailure {
                progressBar.visibility = View.GONE
                Toast.makeText(this@DetalhesUsuarioAdminActivity, getString(R.string.admin_erro_carregar, it.message), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // CPF só existe pra motorista (vínculo de identidade). Motorista antigo
    // que ainda não informou aparece como "Não informado"; conta só de
    // passageiro não mostra o campo.
    private fun carregarCpf(uid: String, usuario: Usuario) {
        lifecycleScope.launch {
            val vinculo = adminRepository.buscarVinculoMotorista(uid).getOrNull()
            val ehMotorista = vinculo != null || usuario.veiculo?.estaPreenchido() == true
            tvCpfRotulo.visibility = if (ehMotorista) View.VISIBLE else View.GONE
            tvCpf.visibility = if (ehMotorista) View.VISIBLE else View.GONE
            tvCpf.text = vinculo?.cpf?.let { CpfUtil.formatar(it) } ?: getString(R.string.admin_detalhes_cpf_nao_informado)
        }
    }

    private fun preencherCampos(usuario: Usuario) {
        etNome.setText(usuario.nomeCompleto ?: "")
        tvEmail.text = usuario.email ?: ""
        etTelefone.setText(usuario.telefone ?: "")
        tvCriadoEm.text = usuario.criadoEm?.let { formatoData.format(it) } ?: ""

        if (!usuario.fotoUrl.isNullOrEmpty()) {
            ivFoto.load(usuario.fotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        }

        val veiculo = usuario.veiculo
        if (veiculo != null && veiculo.estaPreenchido()) {
            containerVeiculo.visibility = View.VISIBLE
            etVeiculoMarca.setText(veiculo.marca ?: "")
            etVeiculoModelo.setText(veiculo.modelo ?: "")
            etVeiculoCor.setText(veiculo.cor ?: "")
            etVeiculoPlaca.setText(veiculo.placa ?: "")
        } else {
            containerVeiculo.visibility = View.GONE
        }
    }

    private fun entrarEmEdicao() {
        emEdicao = true
        btnEditarSalvar.text = getString(R.string.salvar)
        listOf(etNome, etTelefone, etVeiculoMarca, etVeiculoModelo, etVeiculoCor, etVeiculoPlaca).forEach { it.isEnabled = true }
    }

    private fun sairDeEdicao() {
        emEdicao = false
        btnEditarSalvar.text = getString(R.string.editar)
        listOf(etNome, etTelefone, etVeiculoMarca, etVeiculoModelo, etVeiculoCor, etVeiculoPlaca).forEach { it.isEnabled = false }
    }

    private fun confirmarSalvar() {
        val usuario = usuarioAtual ?: return
        val uid = usuario.id ?: return
        val nome = etNome.text.toString().trim()
        val telefone = etTelefone.text.toString().trim()
        if (nome.isEmpty() || telefone.isEmpty()) {
            Toast.makeText(this, R.string.admin_detalhes_erro_campos, Toast.LENGTH_SHORT).show()
            return
        }
        val veiculo = if (containerVeiculo.visibility == View.VISIBLE) {
            Veiculo(
                marca = etVeiculoMarca.text.toString().trim(),
                modelo = etVeiculoModelo.text.toString().trim(),
                cor = etVeiculoCor.text.toString().trim(),
                placa = etVeiculoPlaca.text.toString().trim()
            )
        } else {
            null
        }

        val nomeExibido = usuario.nomeCompleto?.ifBlank { null } ?: usuario.email ?: "esse usuário"
        ConfirmarSenhaMasterDialogUtil.mostrar(
            this,
            getString(R.string.admin_detalhes_salvar_titulo),
            getString(R.string.admin_detalhes_salvar_mensagem, nomeExibido),
            textoBotaoConfirmar = R.string.salvar,
            hintSenha = R.string.admin_senha_autorizacao_usuario_hint
        ) { senhaMaster ->
            progressBar.visibility = View.VISIBLE
            btnEditarSalvar.isEnabled = false
            lifecycleScope.launch {
                adminRepository.atualizarUsuario(uid, nome, telefone, veiculo, senhaMaster)
                    .onSuccess {
                        progressBar.visibility = View.GONE
                        btnEditarSalvar.isEnabled = true
                        Toast.makeText(this@DetalhesUsuarioAdminActivity, R.string.admin_detalhes_salvar_sucesso, Toast.LENGTH_SHORT).show()
                        sairDeEdicao()
                        carregarUsuario(uid)
                    }
                    .onFailure { e ->
                        progressBar.visibility = View.GONE
                        btnEditarSalvar.isEnabled = true
                        Toast.makeText(this@DetalhesUsuarioAdminActivity, getString(R.string.admin_detalhes_salvar_erro, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    companion object {
        const val EXTRA_UID = "uid"
    }
}

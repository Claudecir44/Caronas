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
    // true quando a tela foi aberta pelo login (escolheu entrar como motorista mas
    // o cadastro não tinha veículo) — ver LoginCaronasActivity.
    private var vemDoLoginComoMotorista = false
    private lateinit var layoutVeiculo: LinearLayout
    private lateinit var etCpf: EditText
    private lateinit var etVeiculoModelo: EditText
    private lateinit var etVeiculoMarca: EditText
    private lateinit var etVeiculoCor: EditText
    private lateinit var etVeiculoPlaca: EditText
    private lateinit var btnSalvar: Button
    private lateinit var btnExcluirCadastro: Button
    private lateinit var progressBar: ProgressBar

    private var usuarioAtual: Usuario? = null
    // Vínculo de identidade do motorista (com o CPF) — null = conta sem CPF
    // vinculado ainda (passageiro, ou motorista antigo). Com vínculo, o CPF
    // aparece travado e nome/telefone passam pelo servidor ao salvar (ver
    // validarESalvar).
    private var vinculoAtual: VinculoMotorista? = null
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
        setContentView(R.layout.activity_editar_cadastro_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        etNomeCompleto = findViewById(R.id.etNomeCompleto)
        etEmail = findViewById(R.id.etEmail)
        etTelefone = findViewById(R.id.etTelefone)
        vemDoLoginComoMotorista = intent.getBooleanExtra(EXTRA_VEM_DO_LOGIN_COMO_MOTORISTA, false)
        cbSouMotorista = findViewById(R.id.cbSouMotorista)
        layoutVeiculo = findViewById(R.id.layoutVeiculo)
        etCpf = findViewById(R.id.etCpf)
        CpfUtil.aplicarMascara(etCpf)
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
                if (vinculoAtual == null) etCpf.text.clear()
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
                vinculoAtual = usuarioRepository.buscarMeuVinculoMotorista().getOrNull()
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

        // "usuario.motorista" é o papel do ÚLTIMO login (Motorista ou
        // Passageiro — ver LoginCaronasActivity.atualizarPapelMotorista),
        // não se o cadastro tem veículo. Usar esse campo aqui fazia o
        // checkbox vir desmarcado sempre que a pessoa tivesse logado como
        // passageiro por último — e ao salvar QUALQUER edição (nome,
        // telefone, foto) com o checkbox desmarcado, o veículo já salvo
        // era apagado (veiculo = null lá embaixo), mesmo sem a pessoa
        // mexer nisso de propósito. O que decide o checkbox aqui tem que
        // ser só "esse cadastro tem veículo?".
        val temVeiculoCadastrado = usuario.veiculo?.estaPreenchido() == true
        // Vindo do login como motorista, já abre marcado com os campos do veículo
        // à mostra — é exatamente pra isso que a pessoa caiu aqui.
        val marcarComoMotorista = temVeiculoCadastrado || vemDoLoginComoMotorista
        cbSouMotorista.isChecked = marcarComoMotorista
        vinculoAtual?.cpf?.let { cpf ->
            // Já vinculado: mostra e trava. O CPF nunca muda depois do
            // vínculo (o servidor também recusa) — mudar seria a forma de
            // "trocar de identidade" e recomeçar as caronas grátis.
            etCpf.setText(CpfUtil.formatar(cpf))
            etCpf.isEnabled = false
        }
        layoutVeiculo.visibility = if (marcarComoMotorista) View.VISIBLE else View.GONE
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

        // Motorista precisa de CPF: o do vínculo já existente (travado) ou
        // um válido digitado agora. A unicidade é checada no servidor,
        // logo antes de salvar.
        val cpfMotorista = vinculoAtual?.cpf ?: CpfUtil.somenteDigitos(etCpf.text.toString())
        if (souMotorista && !CpfUtil.valido(cpfMotorista)) {
            Toast.makeText(this, R.string.cadastro_erro_cpf_invalido, Toast.LENGTH_SHORT).show()
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

        // "motorista" é o papel da sessão atual (decidido no login, ver
        // LoginCaronasActivity) — editar o perfil nunca PASSA a pessoa pra motorista,
        // mas se ela desmarcou "sou motorista" (veículo apagado) enquanto estava
        // logada como motorista, o papel cai pra passageiro na hora: sem veículo
        // ela deixa de ser motorista, e a tela principal deixa de oferecer as
        // ações de motorista sem precisar sair e entrar de novo.
        val usuarioAtualizado = base.copy(
            nomeCompleto = nome,
            telefone = telefone,
            veiculo = veiculo,
            motorista = base.motorista && souMotorista
        )

        progressBar.visibility = View.VISIBLE
        btnSalvar.isEnabled = false

        lifecycleScope.launch {
            val uid = usuarioAtualizado.id!!

            // Vínculo do motorista (nome completo, CPF, e-mail e telefone
            // únicos entre motoristas): antes de qualquer gravação, e também
            // pra quem já tem vínculo e só mudou nome/telefone (o servidor
            // mantém o vínculo igual ao perfil — firestore.rules recusa o
            // contrário). Repetido em outro motorista = não salva nada.
            if (souMotorista || vinculoAtual != null) {
                val resultadoVinculo = usuarioRepository.registrarMotorista(nome, cpfMotorista, telefone)
                if (resultadoVinculo.isFailure) {
                    progressBar.visibility = View.GONE
                    btnSalvar.isEnabled = true
                    Toast.makeText(
                        this@EditarCadastroCaronasActivity,
                        getString(R.string.editar_erro_generico, resultadoVinculo.exceptionOrNull()?.message),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }

            // Perfil ANTES da foto: uploadFotoPerfil grava fotoUrl sozinho no
            // fim, então a foto nova nunca é desfeita por este merge (que
            // levaria o fotoUrl antigo) — e o perfil já bate com o vínculo
            // quando o upload faz o update dele (firestore.rules).
            val usuarioParaSalvar = usuarioAtualizado
            usuarioRepository.atualizarPerfil(usuarioParaSalvar).onSuccess {
                val uriFoto = fotoUriSelecionada
                if (uriFoto != null) {
                    usuarioRepository.uploadFotoPerfil(uid, uriFoto)
                        .onFailure { e ->
                            Toast.makeText(
                                this@EditarCadastroCaronasActivity,
                                getString(R.string.editar_erro_generico, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                progressBar.visibility = View.GONE
                btnSalvar.isEnabled = true
                Toast.makeText(this@EditarCadastroCaronasActivity, R.string.editar_sucesso, Toast.LENGTH_LONG).show()
                // Dois casos em que o papel da sessão MUDA e a tela principal precisa
                // ser refeita do zero (limpando a pilha), em vez de só voltar pra ela:
                //  - veio do login querendo entrar como motorista e salvou o veículo
                //    (a tela de login já foi encerrada — só finish() fechava o app);
                //  - estava logada como motorista e desmarcou "sou motorista": passa a
                //    ser passageira na hora. Refazer a tela evita ficar com botões,
                //    lista de ofertas e avisos de motorista de um papel que ela já não
                //    tem, e o app não fecha nem exige sair e entrar de novo.
                val virouPassageiro = base.motorista && !souMotorista
                if (vemDoLoginComoMotorista || virouPassageiro) {
                    // Papel motorista só vale se salvou com veículo (souMotorista).
                    usuarioRepository.atualizarPapelMotorista(uid, souMotorista)
                    startActivity(
                        Intent(this@EditarCadastroCaronasActivity, TelaCaronasActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
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

    companion object {
        const val EXTRA_VEM_DO_LOGIN_COMO_MOTORISTA = "vemDoLoginComoMotorista"
    }
}

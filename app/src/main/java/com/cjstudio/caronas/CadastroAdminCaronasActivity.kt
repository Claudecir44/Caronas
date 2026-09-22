package com.cjstudio.caronas

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
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

// Cria uma conta de admin nova (nome/sobrenome/email/telefone/cpf/foto,
// papel e permissões, todos obrigatórios) OU edita/exclui uma já existente
// (ver EXTRA_UID_EDITAR, aberto a partir de BuscarAdminActivity ou da nova
// GerenciarAdministradoresCaronasActivity) — protegidas pela SENHA do
// administrador master (sem CPF — a senha sozinha basta) e, quando já existe
// mais de um admin, também pela permissão "administradores" de quem está
// chamando — verificado só na Cloud Function
// (cadastrarAdmin/atualizarAdmin/atualizarPermissoesAdmin/excluirAdmin, ver
// AdminRepository), nunca client-side.
//
// No modo edição: e-mail não é editável aqui (mudar e-mail de uma conta
// Auth já existente tem mais implicações, fora do escopo pedido) e os
// campos de senha de CADASTRO somem (edição não mexe na senha de login).
// Foto também só é editável quando é o PRÓPRIO admin logado editando o
// próprio perfil — firestore.rules só libera update de fotoUrl pro dono do
// documento (ver AdminRepository.atualizarFotoAdmin), então trocar a foto
// de OUTRO admin exigiria uma Cloud Function à parte; não implementado
// ainda. "Excluir Cadastro de Admin" só remove admins/{uid} (revoga o
// acesso ao painel) — não apaga a conta Firebase Auth nem o cadastro de
// usuário comum dessa pessoa.
@AndroidEntryPoint
class CadastroAdminCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var ivFoto: ImageView
    private lateinit var tvSelecionarFoto: TextView
    private lateinit var etSenhaMaster: EditText
    private lateinit var radioGroupPapel: RadioGroup
    private lateinit var containerPermissoes: LinearLayout
    private val checkboxesPorChave = mutableMapOf<String, CheckBox>()
    private var fotoUriSelecionada: Uri? = null
    private var uidEditando: String? = null
    private var nomeAdminEditando: String = ""

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val uriLocal = FotoUtil.copiarUriParaArquivoTemporario(this, uri)
            fotoUriSelecionada = uriLocal
            ivFoto.load(uriLocal ?: uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_admin_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        uidEditando = intent.getStringExtra(EXTRA_UID_EDITAR)

        ivFoto = findViewById(R.id.ivFotoAdmin)
        tvSelecionarFoto = findViewById(R.id.tvSelecionarFotoAdmin)
        val tvTitulo = findViewById<TextView>(R.id.tvTituloCadastroAdmin)
        val etNome = findViewById<EditText>(R.id.etNome)
        val etSobrenome = findViewById<EditText>(R.id.etSobrenome)
        val etEmail = findViewById<EditText>(R.id.etEmailAdmin)
        val etTelefone = findViewById<EditText>(R.id.etTelefoneAdmin)
        val etCpf = findViewById<EditText>(R.id.etCpfAdmin)
        val layoutSenha = findViewById<LinearLayout>(R.id.layoutSenhaAdmin)
        val etSenha = findViewById<EditText>(R.id.etSenhaAdmin)
        etSenha.habilitarToggleSenha()
        val etConfirmarSenha = findViewById<EditText>(R.id.etConfirmarSenhaAdmin)
        etConfirmarSenha.habilitarToggleSenha()
        etSenhaMaster = findViewById(R.id.etSenhaMaster)
        etSenhaMaster.habilitarToggleSenha()
        radioGroupPapel = findViewById(R.id.radioGroupPapelAdmin)
        containerPermissoes = findViewById(R.id.containerPermissoesAdmin)
        val btnSalvar = findViewById<Button>(R.id.btnCadastrarAdmin)
        val btnExcluir = findViewById<Button>(R.id.btnExcluirAdmin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        criarCheckboxesPermissoes()

        val ehEdicao = uidEditando != null

        // Papel escolhido decide o default das permissões só no CADASTRO —
        // admin recém-criado começa com tudo marcado (acesso total, igual
        // um admin de hoje), colaborador começa com tudo desmarcado (o
        // criador escolhe o que liberar). Na edição, quem decide o estado
        // dos checkboxes é o admin já carregado (carregarAdminParaEditar).
        radioGroupPapel.setOnCheckedChangeListener { _, checkedId ->
            if (ehEdicao) return@setOnCheckedChangeListener
            val marcarTudo = checkedId == R.id.radioPapelAdmin
            checkboxesPorChave.values.forEach { it.isChecked = marcarTudo }
        }

        if (ehEdicao) {
            tvTitulo.setText(R.string.admin_editar_titulo)
            btnSalvar.setText(R.string.admin_editar_botao)
            layoutSenha.visibility = View.GONE
            etEmail.isEnabled = false
            btnExcluir.visibility = View.VISIBLE

            // Foto de outro admin (não o logado) não é editável aqui — ver
            // comentário da classe.
            val souEuMesmo = uidEditando == adminRepository.uidLogado()
            if (!souEuMesmo) {
                ivFoto.isClickable = false
                tvSelecionarFoto.visibility = View.GONE
            }

            carregarAdminParaEditar(uidEditando!!, etNome, etSobrenome, etEmail, etTelefone, etCpf)
        } else {
            checkboxesPorChave.values.forEach { it.isChecked = true } // default: admin (radio já marcado)
        }

        val abrirSeletor = View.OnClickListener {
            seletorFoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        ivFoto.setOnClickListener(abrirSeletor)
        tvSelecionarFoto.setOnClickListener(abrirSeletor)

        btnSalvar.setOnClickListener {
            val nome = etNome.text.toString().trim()
            val sobrenome = etSobrenome.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val telefone = etTelefone.text.toString().trim()
            val cpf = etCpf.text.toString().trim()
            val senha = etSenha.text.toString()
            val confirmarSenha = etConfirmarSenha.text.toString()
            val senhaMaster = etSenhaMaster.text.toString()

            if (nome.isEmpty()) {
                Toast.makeText(this, R.string.admin_cadastro_erro_nome_obrigatorio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (sobrenome.isEmpty()) {
                Toast.makeText(this, R.string.admin_cadastro_erro_sobrenome_obrigatorio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!ehEdicao && (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
                Toast.makeText(this, R.string.cadastro_erro_email_obrigatorio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (telefone.isEmpty()) {
                Toast.makeText(this, R.string.cadastro_erro_telefone_obrigatorio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cpf.isEmpty()) {
                Toast.makeText(this, R.string.admin_cadastro_erro_cpf, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!ehEdicao) {
                if (senha.length < 6 || senha.length > 10) {
                    Toast.makeText(this, R.string.cadastro_erro_senha_tamanho, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (senha != confirmarSenha) {
                    Toast.makeText(this, R.string.cadastro_erro_senhas_diferentes, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (fotoUriSelecionada == null) {
                    Toast.makeText(this, R.string.admin_cadastro_erro_foto_obrigatoria, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            if (senhaMaster.isEmpty()) {
                Toast.makeText(this, R.string.admin_cadastro_erro_senha_autorizacao, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnSalvar.isEnabled = false

            val role = if (radioGroupPapel.checkedRadioButtonId == R.id.radioPapelColaborador) "colaborador" else "admin"
            val permissoes = checkboxesPorChave.mapValues { it.value.isChecked }

            if (ehEdicao) {
                salvarEdicao(uidEditando!!, nome, sobrenome, telefone, cpf, role, permissoes, senhaMaster, progressBar, btnSalvar)
            } else {
                salvarCadastroNovo(nome, sobrenome, email, telefone, cpf, senha, role, permissoes, senhaMaster, progressBar, btnSalvar)
            }
        }

        btnExcluir.setOnClickListener { confirmarExclusao(uidEditando!!) }
    }

    private fun criarCheckboxesPermissoes() {
        containerPermissoes.removeAllViews()
        checkboxesPorChave.clear()
        for ((chave, rotulo) in Admin.CHAVES_PERMISSOES) {
            val checkBox = CheckBox(this).apply {
                text = rotulo
                setTextColor(android.graphics.Color.WHITE)
            }
            checkboxesPorChave[chave] = checkBox
            containerPermissoes.addView(checkBox)
        }
    }

    private fun carregarAdminParaEditar(
        uid: String,
        etNome: EditText,
        etSobrenome: EditText,
        etEmail: EditText,
        etTelefone: EditText,
        etCpf: EditText
    ) {
        lifecycleScope.launch {
            adminRepository.buscarAdminLogado(uid).onSuccess { admin ->
                nomeAdminEditando = admin.nomeCompleto.ifEmpty { admin.email ?: "" }
                etNome.setText(admin.nome)
                etSobrenome.setText(admin.sobrenome)
                etEmail.setText(admin.email)
                etTelefone.setText(admin.telefone)
                etCpf.setText(admin.cpf)
                radioGroupPapel.check(if (admin.ehColaborador) R.id.radioPapelColaborador else R.id.radioPapelAdmin)
                checkboxesPorChave.forEach { (chave, checkBox) -> checkBox.isChecked = admin.temPermissao(chave) }
                if (!admin.fotoUrl.isNullOrEmpty()) {
                    ivFoto.load(admin.fotoUrl) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_person_default)
                        error(R.drawable.ic_person_default)
                    }
                }
            }.onFailure { e ->
                Toast.makeText(this@CadastroAdminCaronasActivity, getString(R.string.admin_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun salvarCadastroNovo(
        nome: String, sobrenome: String, email: String, telefone: String, cpf: String, senha: String,
        role: String, permissoes: Map<String, Boolean>,
        senhaMaster: String, progressBar: ProgressBar, btnSalvar: Button
    ) {
        lifecycleScope.launch {
            adminRepository.cadastrarAdmin(nome, sobrenome, email, telefone, cpf, senha, role, permissoes, senhaMaster)
                .onSuccess { uid ->
                    // Foto + e-mail de verificação (precisa entrar como o novo admin, ver
                    // AdminRepository.finalizarCadastroAdmin). A conta e o Firestore já
                    // foram criados com sucesso — se isso falhar, não desfaz o cadastro;
                    // a foto pode ser adicionada depois.
                    adminRepository.finalizarCadastroAdmin(uid, email, senha, fotoUriSelecionada)

                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CadastroAdminCaronasActivity, R.string.admin_cadastro_sucesso, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    btnSalvar.isEnabled = true
                    Toast.makeText(this@CadastroAdminCaronasActivity, getString(R.string.admin_cadastro_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun salvarEdicao(
        uid: String, nome: String, sobrenome: String, telefone: String, cpf: String,
        role: String, permissoes: Map<String, Boolean>,
        senhaMaster: String, progressBar: ProgressBar, btnSalvar: Button
    ) {
        lifecycleScope.launch {
            adminRepository.atualizarAdmin(uid, nome, sobrenome, telefone, cpf, senhaMaster)
                .onSuccess {
                    // Dados básicos salvos — permissões são uma Cloud
                    // Function separada (mesmo padrão do Match); se ela
                    // falhar, os dados básicos já salvos não são desfeitos,
                    // só avisa o erro específico de permissões.
                    adminRepository.atualizarPermissoesAdmin(uid, role, permissoes, senhaMaster)
                        .onSuccess {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@CadastroAdminCaronasActivity, R.string.admin_editar_sucesso, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        .onFailure { e ->
                            progressBar.visibility = View.GONE
                            btnSalvar.isEnabled = true
                            Toast.makeText(this@CadastroAdminCaronasActivity, getString(R.string.admin_cadastro_erro_generico, e.message), Toast.LENGTH_LONG).show()
                        }
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    btnSalvar.isEnabled = true
                    Toast.makeText(this@CadastroAdminCaronasActivity, getString(R.string.admin_cadastro_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmarExclusao(uid: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_editar_excluir_confirmar_titulo)
            .setMessage(getString(R.string.admin_editar_excluir_confirmar_mensagem, nomeAdminEditando))
            .setPositiveButton(R.string.excluir) { _, _ ->
                val senhaMaster = etSenhaMaster.text.toString()
                if (senhaMaster.isEmpty()) {
                    Toast.makeText(this, R.string.admin_cadastro_erro_senha_autorizacao, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executarExclusao(uid, senhaMaster)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun executarExclusao(uid: String, senhaMaster: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            adminRepository.excluirAdmin(uid, senhaMaster)
                .onSuccess {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CadastroAdminCaronasActivity, R.string.admin_editar_excluir_sucesso, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CadastroAdminCaronasActivity, getString(R.string.admin_cadastro_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    companion object {
        const val EXTRA_UID_EDITAR = "uid_editar"
    }
}

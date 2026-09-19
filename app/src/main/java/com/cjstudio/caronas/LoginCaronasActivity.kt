package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var cbLoginMotorista: CheckBox
    private lateinit var cbLoginPassageiro: CheckBox
    private lateinit var btnEntrar: Button
    private lateinit var tvCriarConta: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_caronas)
        findViewById<View>(R.id.scrollRoot).ajustarPaddingParaTeclado()

        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        etSenha.habilitarToggleSenha()
        cbLoginMotorista = findViewById(R.id.cbLoginMotorista)
        cbLoginPassageiro = findViewById(R.id.cbLoginPassageiro)
        btnEntrar = findViewById(R.id.btnEntrar)
        tvCriarConta = findViewById(R.id.tvCriarConta)
        progressBar = findViewById(R.id.progressBar)
        val tvSuporte = findViewById<TextView>(R.id.tvSuporte)

        // Mutuamente exclusivas — marcar uma desmarca a outra (não é papel
        // de conta separado, é só a escolha de como entrar desta vez;
        // atualiza o campo "motorista" do usuário se ele marcar alguma).
        cbLoginMotorista.setOnCheckedChangeListener { _, marcado ->
            if (marcado) cbLoginPassageiro.isChecked = false
        }
        cbLoginPassageiro.setOnCheckedChangeListener { _, marcado ->
            if (marcado) cbLoginMotorista.isChecked = false
        }

        btnEntrar.setOnClickListener { fazerLogin() }
        tvCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroCaronasActivity::class.java))
        }
        tvSuporte.setOnClickListener { mostrarDialogSuporte() }
    }

    private fun fazerLogin() {
        val email = etEmail.text.toString().trim()
        val senha = etSenha.text.toString().trim()

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, R.string.login_erro_campos_obrigatorios, Toast.LENGTH_SHORT).show()
            return
        }

        // Precisa escolher um papel pra entrar — não loga direto como
        // motorista (ou o que já estava salvo) sem confirmar de propósito.
        if (!cbLoginMotorista.isChecked && !cbLoginPassageiro.isChecked) {
            Toast.makeText(this, R.string.login_erro_selecionar_papel, Toast.LENGTH_SHORT).show()
            return
        }

        val entrarComoMotorista = cbLoginMotorista.isChecked

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val resultado = usuarioRepository.login(email, senha)
            resultado.onSuccess { usuario ->
                progressBar.visibility = View.GONE

                // Escolheu entrar como motorista mas o cadastro não tem
                // veículo salvo (ou tem um vazio, sem modelo/marca/cor/
                // placa) — não deixa entrar assim, manda completar o
                // cadastro antes. Se já tem veículo de verdade, entra direto.
                if (entrarComoMotorista && usuario.veiculo?.estaPreenchido() != true) {
                    Toast.makeText(this@LoginCaronasActivity, R.string.login_erro_sem_veiculo, Toast.LENGTH_LONG).show()
                    // Avisa a edição que a pessoa veio do login querendo entrar como
                    // motorista: ao salvar o veículo ela segue direto pra tela
                    // principal (senão o app fecha, já que esta tela de login é
                    // encerrada logo abaixo e não sobra nada na pilha).
                    startActivity(
                        Intent(this@LoginCaronasActivity, EditarCadastroCaronasActivity::class.java)
                            .putExtra(EditarCadastroCaronasActivity.EXTRA_VEM_DO_LOGIN_COMO_MOTORISTA, true)
                    )
                    finish()
                    return@onSuccess
                }

                usuarioRepository.atualizarPapelMotorista(usuario.id!!, entrarComoMotorista)
                startActivity(Intent(this@LoginCaronasActivity, TelaCaronasActivity::class.java))
                finish()
            }.onFailure { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this@LoginCaronasActivity, getString(R.string.erro_generico, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    // "Suporte" (canto inferior esquerdo) — menu com as duas opções
    // pedidas, cada uma abrindo o mesmo formulário (nome completo, e-mail,
    // telefone), só muda o texto de explicação e a ação do envio.
    private fun mostrarDialogSuporte() {
        val opcoes = arrayOf(
            getString(R.string.login_suporte_opcao_esqueci_senha),
            getString(R.string.login_suporte_opcao_reenviar_verificacao)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.login_suporte)
            .setItems(opcoes) { _, which ->
                if (which == 0) abrirFormularioSuporte(ehRedefinicaoSenha = true)
                else abrirFormularioSuporte(ehRedefinicaoSenha = false)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun abrirFormularioSuporte(ehRedefinicaoSenha: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_suporte_caronas, null)
        val tvExplicacao = view.findViewById<TextView>(R.id.tvExplicacaoSuporte)
        val etNome = view.findViewById<EditText>(R.id.etNomeSuporte)
        val etEmailSuporte = view.findViewById<EditText>(R.id.etEmailSuporte)
        val etTelefoneSuporte = view.findViewById<EditText>(R.id.etTelefoneSuporte)

        tvExplicacao.setText(
            if (ehRedefinicaoSenha) R.string.login_suporte_esqueci_senha_explicacao
            else R.string.login_suporte_reenviar_verificacao_explicacao
        )
        // Já vem com o e-mail digitado no login, se houver — economiza
        // redigitar o mais comum de errar.
        etEmailSuporte.setText(etEmail.text.toString().trim())

        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (ehRedefinicaoSenha) R.string.login_suporte_opcao_esqueci_senha
                else R.string.login_suporte_opcao_reenviar_verificacao
            )
            .setView(view)
            .setPositiveButton(R.string.login_suporte_botao_enviar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nome = etNome.text.toString().trim()
                val emailDigitado = etEmailSuporte.text.toString().trim()
                val telefone = etTelefoneSuporte.text.toString().trim()

                if (nome.isEmpty() || emailDigitado.isEmpty() || telefone.isEmpty()) {
                    Toast.makeText(this, R.string.login_suporte_erro_campos_obrigatorios, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialog.dismiss()
                if (ehRedefinicaoSenha) enviarRedefinicaoSenha(emailDigitado) else enviarReenvioVerificacao(emailDigitado)
            }
        }
        dialog.show()
    }

    private fun enviarRedefinicaoSenha(email: String) {
        lifecycleScope.launch {
            usuarioRepository.enviarRedefinicaoSenha(email)
                .onSuccess {
                    Toast.makeText(this@LoginCaronasActivity, R.string.login_email_redefinicao_enviado, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(this@LoginCaronasActivity, getString(R.string.login_suporte_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun enviarReenvioVerificacao(email: String) {
        lifecycleScope.launch {
            usuarioRepository.reenviarEmailVerificacao(email)
                .onSuccess {
                    Toast.makeText(this@LoginCaronasActivity, R.string.login_suporte_reenvio_enviado, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(this@LoginCaronasActivity, getString(R.string.login_suporte_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }
}

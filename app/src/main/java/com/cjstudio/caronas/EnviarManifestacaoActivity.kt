package com.cjstudio.caronas

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// "Reclamações, Sugestões e Denúncias" — aberta por ConfiguracoesCaronasActivity.
// Nome/e-mail/telefone vêm pré-preenchidos do cadastro do usuário logado (mas
// editáveis, já que quem preenche pode não ser necessariamente quem está
// relatando algo sobre a própria conta). Grava direto no Firestore (ver
// IUsuarioRepository.enviarManifestacao) — sem Cloud Function, mesmo espírito
// de "enviar é livre, só responder/excluir no painel é que é sensível".
@AndroidEntryPoint
class EnviarManifestacaoActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var etNome: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefone: EditText
    private lateinit var rgTipo: RadioGroup
    private lateinit var etMensagem: EditText
    private lateinit var btnEnviar: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enviar_manifestacao)

        etNome = findViewById(R.id.etNomeManifestacao)
        etEmail = findViewById(R.id.etEmailManifestacao)
        etTelefone = findViewById(R.id.etTelefoneManifestacao)
        rgTipo = findViewById(R.id.rgTipoManifestacao)
        etMensagem = findViewById(R.id.etMensagemManifestacao)
        btnEnviar = findViewById(R.id.btnEnviarManifestacao)
        progressBar = findViewById(R.id.progressBarManifestacao)

        preencherDadosDoUsuario()
        btnEnviar.setOnClickListener { enviar() }
    }

    private fun preencherDadosDoUsuario() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                etNome.setText(usuario.nomeCompleto ?: "")
                etEmail.setText(usuario.email ?: "")
                etTelefone.setText(usuario.telefone ?: "")
            }
        }
    }

    private fun enviar() {
        val nome = etNome.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val telefone = etTelefone.text.toString().trim()
        val mensagem = etMensagem.text.toString().trim()

        if (nome.isEmpty() || email.isEmpty() || telefone.isEmpty() || mensagem.isEmpty()) {
            Toast.makeText(this, R.string.manifestacao_erro_campos, Toast.LENGTH_SHORT).show()
            return
        }
        val tipo = when (rgTipo.checkedRadioButtonId) {
            R.id.rbReclamacao -> Manifestacao.TIPO_RECLAMACAO
            R.id.rbSugestao -> Manifestacao.TIPO_SUGESTAO
            R.id.rbDenuncia -> Manifestacao.TIPO_DENUNCIA
            else -> null
        }
        if (tipo == null) {
            Toast.makeText(this, R.string.manifestacao_erro_tipo, Toast.LENGTH_SHORT).show()
            return
        }

        btnEnviar.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            usuarioRepository.enviarManifestacao(tipo, nome, email, telefone, mensagem)
                .onSuccess {
                    Toast.makeText(this@EnviarManifestacaoActivity, R.string.manifestacao_sucesso, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { e ->
                    btnEnviar.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@EnviarManifestacaoActivity, getString(R.string.manifestacao_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }
}

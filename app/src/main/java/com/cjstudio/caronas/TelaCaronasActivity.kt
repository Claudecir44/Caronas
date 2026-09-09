package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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
import javax.inject.Inject

@AndroidEntryPoint
class TelaCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var tvPapelUsuario: TextView
    private lateinit var tvNomeUsuario: TextView
    private lateinit var btnSair: Button
    private var usuarioAtual: Usuario? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tela_caronas)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        tvPapelUsuario = findViewById(R.id.tvPapelUsuario)
        tvNomeUsuario = findViewById(R.id.tvNomeUsuario)
        btnSair = findViewById(R.id.btnSair)

        val tvMeuPerfil = findViewById<TextView>(R.id.tvMeuPerfil)
        val btnProcurar = findViewById<TextView>(R.id.btnProcurar)
        val btnOferecer = findViewById<TextView>(R.id.btnOferecer)
        val btnSuasViagens = findViewById<TextView>(R.id.btnSuasViagens)
        val btnChat = findViewById<TextView>(R.id.btnChat)

        carregarPerfil()

        // "Meu Perfil" já abre a tela de edição (que reúne ver + editar +
        // excluir cadastro) — não tem mais link separado de "Editar Perfil".
        tvMeuPerfil.setOnClickListener {
            startActivity(Intent(this, EditarCadastroCaronasActivity::class.java))
        }

        // Busca de carona, "Suas Viagens" e Chat de verdade ainda não
        // existem — só placeholders por enquanto (ver plano da fase 1).
        val mostrarEmBreve = { android.view.View.OnClickListener { Toast.makeText(this, R.string.tela_em_breve, Toast.LENGTH_SHORT).show() } }
        btnProcurar.setOnClickListener(mostrarEmBreve())
        btnOferecer.setOnClickListener { abrirOferecerCarona() }
        btnSuasViagens.setOnClickListener(mostrarEmBreve())
        btnChat.setOnClickListener(mostrarEmBreve())

        btnSair.setOnClickListener {
            lifecycleScope.launch {
                usuarioRepository.logout()
                startActivity(Intent(this@TelaCaronasActivity, LoginCaronasActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar da tela de Editar Perfil, pra refletir mudanças
        // (nome, foto, papel motorista/passageiro) sem precisar reabrir o app.
        carregarPerfil()
    }

    // Só quem está cadastrado como motorista (com veículo) pode oferecer
    // carona — passageiro sem carro não tem o que oferecer. Ver
    // CadastroCaronasActivity/EditarCadastroCaronasActivity pra virar
    // motorista.
    private fun abrirOferecerCarona() {
        val usuario = usuarioAtual
        if (usuario == null) {
            Toast.makeText(this, R.string.carregando, Toast.LENGTH_SHORT).show()
            return
        }
        if (!usuario.motorista) {
            Toast.makeText(this, R.string.tela_oferecer_precisa_ser_motorista, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, OferecerCaronaActivity::class.java))
    }

    private fun carregarPerfil() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                usuarioAtual = usuario
                tvPapelUsuario.text = getString(
                    if (usuario.motorista) R.string.tela_papel_motorista else R.string.tela_papel_passageiro
                )
                tvNomeUsuario.text = usuario.nomeCompleto?.trim()?.substringBefore(" ") ?: ""
                if (!usuario.fotoUrl.isNullOrEmpty()) {
                    ivFotoPerfil.load(usuario.fotoUrl) {
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_person_default)
                        error(R.drawable.ic_person_default)
                    }
                }
            }.onFailure {
                // Sessão inválida/expirada — volta pro login.
                startActivity(Intent(this@TelaCaronasActivity, LoginCaronasActivity::class.java))
                finish()
            }
        }
    }
}

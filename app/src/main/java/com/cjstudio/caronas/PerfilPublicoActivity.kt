package com.cjstudio.caronas

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Perfil de OUTRO usuário (motorista ou passageiro) — aberto ao tocar no
// nome/foto de alguém na busca de caronas, em Minhas Viagens ou em
// Solicitações Recebidas. Mostra os dados públicos do cadastro + a nota
// média e a lista de avaliações recebidas (ver Avaliacao.kt) — é o "visível
// a todos os usuários" pedido: qualquer usuário autenticado pode abrir o
// perfil de qualquer outro (mesma regra de leitura já existente em
// firestore.rules pra "usuarios").
@AndroidEntryPoint
class PerfilPublicoActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var avaliacaoRepository: IAvaliacaoRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var scroll: NestedScrollView
    private lateinit var ivFoto: ImageView
    private lateinit var tvNome: TextView
    private lateinit var tvPapel: TextView
    private lateinit var tvCarro: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var tvMedia: TextView
    private lateinit var tvSemAvaliacoes: TextView
    private lateinit var rvAvaliacoes: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil_publico)

        progressBar = findViewById(R.id.progressBarPerfilPublico)
        scroll = findViewById(R.id.scrollPerfilPublico)
        ivFoto = findViewById(R.id.ivFotoPerfilPublico)
        tvNome = findViewById(R.id.tvNomePerfilPublico)
        tvPapel = findViewById(R.id.tvPapelPerfilPublico)
        tvCarro = findViewById(R.id.tvCarroPerfilPublico)
        ratingBar = findViewById(R.id.ratingBarPerfilPublico)
        tvMedia = findViewById(R.id.tvMediaPerfilPublico)
        tvSemAvaliacoes = findViewById(R.id.tvSemAvaliacoesPerfilPublico)
        rvAvaliacoes = findViewById(R.id.rvAvaliacoesPerfilPublico)
        rvAvaliacoes.layoutManager = LinearLayoutManager(this)

        if (intent.hasExtra(EXTRA_EXIBIR_COMO_MOTORISTA)) {
            exibirComoMotorista = intent.getBooleanExtra(EXTRA_EXIBIR_COMO_MOTORISTA, false)
        }
        val usuarioId = intent.getStringExtra(EXTRA_USUARIO_ID)
        if (usuarioId == null) {
            finish()
            return
        }
        carregarPerfil(usuarioId)
    }

    // Papel em que a pessoa está sendo vista — decidido por QUEM ABRIU o perfil, não
    // pelo cadastro: o mesmo usuário pode ser motorista e passageiro, e o motorista
    // que abre o perfil de um passageiro (Solicitações Recebidas) não deve ver o
    // carro dele, mesmo que essa pessoa também seja motorista cadastrada.
    // Sem o extra, cai no comportamento antigo (papel da última sessão).
    private var exibirComoMotorista: Boolean? = null

    private fun carregarPerfil(usuarioId: String) {
        lifecycleScope.launch {
            val resultadoUsuario = usuarioRepository.buscarUsuarioPorId(usuarioId)
            val usuario = resultadoUsuario.getOrNull()
            if (usuario == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@PerfilPublicoActivity,
                    getString(R.string.perfil_publico_erro_carregar, resultadoUsuario.exceptionOrNull()?.message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }

            // Falha ao buscar avaliações não deveria impedir o resto do
            // perfil de aparecer — trata como "sem avaliações ainda".
            val avaliacoes = avaliacaoRepository.buscarAvaliacoesRecebidas(usuarioId).getOrDefault(emptyList())

            progressBar.visibility = View.GONE
            scroll.visibility = View.VISIBLE
            preencherPerfil(usuario)
            preencherAvaliacoes(avaliacoes)
        }
    }

    private fun preencherPerfil(usuario: Usuario) {
        tvNome.text = usuario.nomeCompleto ?: ""
        val comoMotorista = exibirComoMotorista ?: usuario.motorista
        tvPapel.text = getString(if (comoMotorista) R.string.perfil_publico_motorista else R.string.perfil_publico_passageiro)

        val veiculo = usuario.veiculo
        if (comoMotorista && veiculo != null && veiculo.estaPreenchido()) {
            tvCarro.visibility = View.VISIBLE
            tvCarro.text = getString(
                R.string.minhas_viagens_carro_formato,
                veiculo.modelo ?: "",
                veiculo.marca ?: "",
                veiculo.cor ?: "",
                veiculo.placa ?: ""
            )
        } else {
            tvCarro.visibility = View.GONE
        }

        if (!usuario.fotoUrl.isNullOrEmpty()) {
            ivFoto.load(usuario.fotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            ivFoto.setImageResource(R.drawable.ic_person_default)
        }
    }

    private fun preencherAvaliacoes(avaliacoes: List<Avaliacao>) {
        if (avaliacoes.isEmpty()) {
            ratingBar.rating = 0f
            tvMedia.text = getString(R.string.perfil_publico_sem_nota)
            tvSemAvaliacoes.visibility = View.VISIBLE
            rvAvaliacoes.visibility = View.GONE
            return
        }

        val media = avaliacoes.map { it.nota }.average()
        ratingBar.rating = media.toFloat()
        tvMedia.text = getString(R.string.perfil_publico_media_formato, media, avaliacoes.size)

        tvSemAvaliacoes.visibility = View.GONE
        rvAvaliacoes.visibility = View.VISIBLE
        rvAvaliacoes.adapter = AvaliacaoAdapter(avaliacoes)
    }

    companion object {
        const val EXTRA_USUARIO_ID = "usuarioId"
        const val EXTRA_EXIBIR_COMO_MOTORISTA = "exibirComoMotorista"
    }
}

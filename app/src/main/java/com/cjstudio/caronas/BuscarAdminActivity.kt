package com.cjstudio.caronas

import android.content.Intent
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
import javax.inject.Inject

// "Editar Perfil" do admin — não edita direto, primeiro busca por CPF (o
// admin master pode editar QUALQUER admin, não só o próprio perfil) e só
// depois de encontrar abre o cadastro pré-preenchido pra editar/salvar
// (ver CadastroAdminCaronasActivity, modo edição).
@AndroidEntryPoint
class BuscarAdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private var adminEncontrado: Admin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buscar_admin)

        val etCpf = findViewById<EditText>(R.id.etCpfBusca)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarBuscaAdmin)
        val tvVazio = findViewById<TextView>(R.id.tvBuscaAdminVazio)
        val cardResultado = findViewById<androidx.cardview.widget.CardView>(R.id.cardResultadoBuscaAdmin)
        val ivFoto = findViewById<ImageView>(R.id.ivFotoResultadoBuscaAdmin)
        val tvNome = findViewById<TextView>(R.id.tvNomeResultadoBuscaAdmin)
        val tvEmail = findViewById<TextView>(R.id.tvEmailResultadoBuscaAdmin)

        findViewById<Button>(R.id.btnBuscarAdmin).setOnClickListener {
            val cpf = etCpf.text.toString().trim()
            if (cpf.isEmpty()) {
                Toast.makeText(this, R.string.admin_buscar_erro_cpf_obrigatorio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            adminEncontrado = null
            cardResultado.visibility = View.GONE
            tvVazio.visibility = View.GONE
            progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                adminRepository.buscarAdminPorCpf(cpf)
                    .onSuccess { admin ->
                        progressBar.visibility = View.GONE
                        if (admin == null) {
                            tvVazio.visibility = View.VISIBLE
                        } else {
                            adminEncontrado = admin
                            cardResultado.visibility = View.VISIBLE
                            tvNome.text = admin.nomeCompleto.ifEmpty { admin.email ?: "" }
                            tvEmail.text = admin.email ?: ""
                            if (!admin.fotoUrl.isNullOrEmpty()) {
                                ivFoto.load(admin.fotoUrl) {
                                    transformations(CircleCropTransformation())
                                    placeholder(R.drawable.ic_person_default)
                                    error(R.drawable.ic_person_default)
                                }
                            } else {
                                ivFoto.setImageResource(R.drawable.ic_person_default)
                            }
                        }
                    }
                    .onFailure { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@BuscarAdminActivity, getString(R.string.admin_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }

        cardResultado.setOnClickListener {
            val uid = adminEncontrado?.id ?: return@setOnClickListener
            val intent = Intent(this, CadastroAdminCaronasActivity::class.java)
            intent.putExtra(CadastroAdminCaronasActivity.EXTRA_UID_EDITAR, uid)
            startActivity(intent)
        }
    }
}

package com.cjstudio.caronas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela de Configurações — aberta pelo ícone ⚙️ no canto superior esquerdo
// da tela principal (mesmo lugar/ícone do Match). Bem mais enxuta que a
// ConfiguracoesActivity do Match (que tem ~9 opções: idioma, pagamentos,
// remoção de conta com CPF, etc.) — Caronas só precisa das opções pedidas.
// Cada opção é um CardView com ícone+texto+seta (não mais um Button
// simples) — visual de lista de configurações, mais próximo do que apps
// nativos costumam usar.
//
// "Editar Perfil" NÃO fica mais aqui: já existe o "Meu Perfil" na tela
// principal (TelaCaronasActivity.tvMeuPerfil), que abre a mesma tela de
// edição — ter os dois era duplicado.
//
// "Pagamentos" só aparece pro motorista (passageiro não paga nada): abre o
// checkout do acesso pago (AssinaturaMotoristaActivity) e mostra logo abaixo
// o status atual ("Grátis: 09/10" ou "Acesso pago até DD/MM/AAAA"), o mesmo
// texto do contador da tela principal (AcessoMotoristaUtil.textoStatus).
// O status é relido no onResume pra refletir o pagamento assim que o
// motorista volta do checkout.
// Aberta também pelo ⚙️ do painel Administração (EXTRA_MODO_ADMIN): aí só
// mostra o que serve a um administrador — Termos de Uso e Privacidade. Sem
// "Pagamentos" (acesso pago é do motorista) e sem "Reclamações, Sugestões e
// Denúncias" (o admin recebe essas mensagens, não as envia) — e ganha
// "Orientações" (guia de como monitorar e ajustar o app), abaixo dos Termos.
@AndroidEntryPoint
class ConfiguracoesCaronasActivity : AppCompatActivity() {

    @Inject
    lateinit var usuarioRepository: IUsuarioRepository

    @Inject
    lateinit var adminRepository: IAdminRepository

    @Inject
    lateinit var bloqueioRepository: IBloqueioRepository

    private lateinit var cardPagamentos: View
    private lateinit var tvPagamentosStatus: TextView
    private var modoAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes_caronas)

        cardPagamentos = findViewById(R.id.cardConfigPagamentos)
        tvPagamentosStatus = findViewById(R.id.tvConfigPagamentosStatus)
        modoAdmin = intent.getBooleanExtra(EXTRA_MODO_ADMIN, false)
        if (modoAdmin) {
            cardPagamentos.visibility = View.GONE
            findViewById<View>(R.id.cardConfigManifestacoes).visibility = View.GONE
            findViewById<View>(R.id.cardConfigBloqueados).visibility = View.GONE
            findViewById<View>(R.id.cardConfigOrientacoes).visibility = View.VISIBLE
            aplicarPermissoesAdmin()
        }
        findViewById<View>(R.id.btnConfigOrientacoes).setOnClickListener {
            startActivity(Intent(this, OrientacoesAdminCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigRelatorios).setOnClickListener {
            startActivity(Intent(this, RelatoriosCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigAdministracao).setOnClickListener {
            startActivity(Intent(this, GerenciarAdministradoresCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigMensagens).setOnClickListener {
            startActivity(
                Intent(this, AdministracaoCaronasActivity::class.java)
                    .putExtra(AdministracaoCaronasActivity.EXTRA_ABRIR_SECAO, AdministracaoCaronasActivity.SECAO_MENSAGENS)
            )
        }
        findViewById<View>(R.id.btnConfigFinanceiro).setOnClickListener {
            startActivity(
                Intent(this, AdministracaoCaronasActivity::class.java)
                    .putExtra(AdministracaoCaronasActivity.EXTRA_ABRIR_SECAO, AdministracaoCaronasActivity.SECAO_FINANCEIRO)
            )
        }

        findViewById<View>(R.id.btnConfigPagamentos).setOnClickListener {
            startActivity(Intent(this, AssinaturaMotoristaActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigTermos).setOnClickListener {
            startActivity(Intent(this, TermosPrivacidadeCaronasActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigManifestacoes).setOnClickListener {
            startActivity(Intent(this, EnviarManifestacaoActivity::class.java))
        }
        findViewById<View>(R.id.btnConfigBloqueados).setOnClickListener { mostrarBloqueados() }
        findViewById<View>(R.id.btnVoltarConfig).setOnClickListener {
            finish()
        }
    }

    // Lista de quem o usuário bloqueou (ver SegurancaUsuarioDialogUtil) —
    // tocar num nome pergunta se quer desbloquear.
    private fun mostrarBloqueados() {
        lifecycleScope.launch {
            bloqueioRepository.listarMeusBloqueios()
                .onSuccess { bloqueios ->
                    if (bloqueios.isEmpty()) {
                        Toast.makeText(this@ConfiguracoesCaronasActivity, R.string.bloqueados_vazio, Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val nomes = bloqueios.map { it.bloqueadoNome ?: getString(R.string.procurar_motorista_desconhecido) }
                    MaterialAlertDialogBuilder(this@ConfiguracoesCaronasActivity)
                        .setTitle(R.string.config_botao_bloqueados)
                        .setItems(nomes.toTypedArray()) { _, indice ->
                            val outroId = bloqueios[indice].bloqueadoId ?: return@setItems
                            val nome = nomes[indice]
                            MaterialAlertDialogBuilder(this@ConfiguracoesCaronasActivity)
                                .setTitle(getString(R.string.bloqueados_desbloquear_formato, nome))
                                .setPositiveButton(R.string.seguranca_opcao_desbloquear) { _, _ ->
                                    SegurancaUsuarioDialogUtil.desbloquear(this@ConfiguracoesCaronasActivity, outroId, nome, bloqueioRepository) {}
                                }
                                .setNegativeButton(R.string.cancelar, null)
                                .show()
                        }
                        .setNegativeButton(R.string.voltar, null)
                        .show()
                }
                .onFailure { e ->
                    Toast.makeText(this@ConfiguracoesCaronasActivity, getString(R.string.bloqueados_erro_carregar, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!modoAdmin) atualizarPagamentos()
    }

    // Cards "Relatórios" e "Administração" só aparecem pra quem tem a
    // permissão correspondente (admin "legado", sem permissoes definidas,
    // continua vendo tudo — ver Admin.temPermissao).
    private fun aplicarPermissoesAdmin() {
        val uid = adminRepository.uidLogado() ?: return
        lifecycleScope.launch {
            adminRepository.buscarAdminLogado(uid).onSuccess { admin ->
                findViewById<View>(R.id.cardConfigRelatorios).visibility =
                    if (admin.temPermissao("relatorios")) View.VISIBLE else View.GONE
                findViewById<View>(R.id.cardConfigAdministracao).visibility =
                    if (admin.temPermissao("administradores")) View.VISIBLE else View.GONE
                findViewById<View>(R.id.cardConfigMensagens).visibility =
                    if (admin.temPermissao("mensagens")) View.VISIBLE else View.GONE
                findViewById<View>(R.id.cardConfigFinanceiro).visibility =
                    if (admin.temPermissao("financeiro")) View.VISIBLE else View.GONE
            }
        }
    }

    private fun atualizarPagamentos() {
        lifecycleScope.launch {
            usuarioRepository.buscarUsuarioLogado().onSuccess { usuario ->
                if (usuario.motorista) {
                    cardPagamentos.visibility = View.VISIBLE
                    tvPagamentosStatus.visibility = View.VISIBLE
                    tvPagamentosStatus.text = AcessoMotoristaUtil.textoStatus(this@ConfiguracoesCaronasActivity, usuario)
                } else {
                    cardPagamentos.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODO_ADMIN = "modoAdmin"
    }
}

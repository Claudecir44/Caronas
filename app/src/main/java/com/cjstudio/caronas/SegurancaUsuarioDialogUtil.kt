package com.cjstudio.caronas

import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

// "Denunciar ou bloquear" — o mesmo menu aberto do chat de uma viagem
// (ChatCaronaActivity) e do perfil público de alguém (PerfilPublicoActivity).
// Exigência da política de conteúdo gerado por usuários do Google Play:
// denunciar a partir do próprio conteúdo/usuário e poder bloquear quem
// incomoda. A denúncia cai em "manifestacoes" (painel admin); o bloqueio
// vale nos dois sentidos (ver Bloqueio.kt e firestore.rules).
object SegurancaUsuarioDialogUtil {

    const val ORIGEM_CHAT = "chat"
    const val ORIGEM_PERFIL = "perfil"

    fun mostrarOpcoes(
        activity: AppCompatActivity,
        outroId: String,
        outroNome: String?,
        origem: String,
        usuarioRepository: IUsuarioRepository,
        bloqueioRepository: IBloqueioRepository,
        onBloqueioAlterado: (bloqueado: Boolean) -> Unit = {}
    ) {
        val nome = outroNome?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.procurar_motorista_desconhecido)
        activity.lifecycleScope.launch {
            val jaBloqueei = bloqueioRepository.bloqueei(outroId).getOrDefault(false)
            val opcoes = arrayOf(
                activity.getString(R.string.seguranca_opcao_denunciar),
                activity.getString(if (jaBloqueei) R.string.seguranca_opcao_desbloquear else R.string.seguranca_opcao_bloquear)
            )
            MaterialAlertDialogBuilder(activity)
                .setTitle(nome)
                .setItems(opcoes) { _, indice ->
                    when (indice) {
                        0 -> mostrarDenuncia(activity, outroId, outroNome, nome, origem, jaBloqueei, usuarioRepository, bloqueioRepository, onBloqueioAlterado)
                        1 -> if (jaBloqueei) {
                            desbloquear(activity, outroId, nome, bloqueioRepository, onBloqueioAlterado)
                        } else {
                            confirmarBloqueio(activity, outroId, outroNome, nome, bloqueioRepository, onBloqueioAlterado)
                        }
                    }
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
    }

    private fun mostrarDenuncia(
        activity: AppCompatActivity,
        outroId: String,
        outroNome: String?,
        nome: String,
        origem: String,
        jaBloqueei: Boolean,
        usuarioRepository: IUsuarioRepository,
        bloqueioRepository: IBloqueioRepository,
        onBloqueioAlterado: (Boolean) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_denunciar, null)
        view.findViewById<TextView>(R.id.tvSubtituloDenunciar).text = activity.getString(R.string.denunciar_subtitulo_formato, nome)
        val rgMotivo = view.findViewById<RadioGroup>(R.id.rgMotivoDenunciar)
        val etDescricao = view.findViewById<TextInputEditText>(R.id.etDescricaoDenunciar)
        val motivos = activity.resources.getStringArray(R.array.denunciar_motivos)
        motivos.forEachIndexed { indice, motivo ->
            rgMotivo.addView(RadioButton(activity).apply {
                id = indice + 1
                text = motivo
            })
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.denunciar_titulo)
            .setView(view)
            .setPositiveButton(R.string.denunciar_botao_enviar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialog.setOnShowListener {
            // Listener próprio (em vez do padrão do setPositiveButton) pra não
            // fechar o diálogo quando falta escolher o motivo.
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val motivo = motivos.getOrNull(rgMotivo.checkedRadioButtonId - 1)
                if (motivo == null) {
                    Toast.makeText(activity, R.string.denunciar_erro_motivo, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val descricao = etDescricao.text?.toString()?.trim() ?: ""
                dialog.dismiss()
                activity.lifecycleScope.launch {
                    usuarioRepository.enviarDenuncia(outroId, outroNome, motivo, descricao, origem)
                        .onSuccess {
                            Toast.makeText(activity, R.string.denunciar_sucesso, Toast.LENGTH_LONG).show()
                            if (!jaBloqueei) confirmarBloqueio(activity, outroId, outroNome, nome, bloqueioRepository, onBloqueioAlterado)
                        }
                        .onFailure { e ->
                            Toast.makeText(activity, activity.getString(R.string.denunciar_erro_generico, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
        dialog.show()
    }

    private fun confirmarBloqueio(
        activity: AppCompatActivity,
        outroId: String,
        outroNome: String?,
        nome: String,
        bloqueioRepository: IBloqueioRepository,
        onBloqueioAlterado: (Boolean) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.bloquear_titulo_formato, nome))
            .setMessage(R.string.bloquear_explicacao)
            .setPositiveButton(R.string.seguranca_opcao_bloquear) { _, _ ->
                activity.lifecycleScope.launch {
                    bloqueioRepository.bloquear(outroId, outroNome)
                        .onSuccess {
                            Toast.makeText(activity, activity.getString(R.string.bloquear_sucesso_formato, nome), Toast.LENGTH_SHORT).show()
                            onBloqueioAlterado(true)
                        }
                        .onFailure { e ->
                            Toast.makeText(activity, activity.getString(R.string.bloquear_erro_generico, e.message), Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    fun desbloquear(
        activity: AppCompatActivity,
        outroId: String,
        nome: String,
        bloqueioRepository: IBloqueioRepository,
        onBloqueioAlterado: (Boolean) -> Unit
    ) {
        activity.lifecycleScope.launch {
            bloqueioRepository.desbloquear(outroId)
                .onSuccess {
                    Toast.makeText(activity, activity.getString(R.string.desbloquear_sucesso_formato, nome), Toast.LENGTH_SHORT).show()
                    onBloqueioAlterado(false)
                }
                .onFailure { e ->
                    Toast.makeText(activity, activity.getString(R.string.bloquear_erro_generico, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }
}
